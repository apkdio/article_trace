package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.Utils.TextExtractor;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.Category;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.User;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.ArticleProtoMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {
    private final ArticleMapper articleMapper;
    private final RustFsUtil rustFsUtil;
    private final AhoCorasickUtil ahoCorasickUtil;
    private final ArticleAgentClient agentClient;
    private final UserService userService;
    private final CategoryService categoryService;
    @Qualifier("stringRedisTemplateArticle")
    private final StringRedisTemplate stringRedisTemplateArticle;
    @Value("${spring.data.redis.viewKey}")
    private String ARTICLE_PENDING_VIEWS_KEY;

    private static final long HOT_ARTICLES_TTL_MINUTES = 5;


    public ArticleServiceImpl(ArticleMapper articleMapper, RustFsUtil rustFsUtil,
                              @Qualifier("stringRedisTemplateArticle") StringRedisTemplate stringRedisTemplateArticle,
                              AhoCorasickUtil ahoCorasickUtil,
                              ArticleAgentClient agentClient,
                              @Lazy UserService userService,
                              CategoryService categoryService) {
        this.ahoCorasickUtil = ahoCorasickUtil;
        this.articleMapper = articleMapper;
        this.rustFsUtil = rustFsUtil;
        this.stringRedisTemplateArticle = stringRedisTemplateArticle;
        this.agentClient = agentClient;
        this.userService = userService;
        this.categoryService = categoryService;
    }
    @Override
    public List<AhoCorasickUtil.Match> containsSensitive(String content) {
        return ahoCorasickUtil.search(content);
    }
    @Override
    public boolean articleAddOrUpdate(Article article, int type) {
        // 0为新增，1为更新
        Map<String, Object> content = new HashMap<>();
        content.put("content", article.getContent());
        long timeStamp = System.currentTimeMillis();
        String fileName = timeStamp + "-" + article.getCreateUser() + ".json";
        if (type == 0) {
            if (rustFsUtil.upload(content, "json", fileName)) {
                article.setContent(fileName);
                LocalDateTime now = LocalDateTime.now();
                article.setCreateTime(now);
            } else {
                return false;
            }
        } else {
            Article rawArticle = articleMapper.selectById(article.getId());
            String contentName = rawArticle.getContent();
            String imgName = rawArticle.getCoverImg();
            // 先增后删
            if (rustFsUtil.upload(content, "json", fileName)) {
                if (rustFsUtil.delete(contentName, "json")) {
                    if (!Objects.equals(imgName, "") && !imgName.equals(article.getCoverImg())) {
                        if (!rustFsUtil.delete(imgName, "image")) return false;
                    }
                    article.setUpdateTime(LocalDateTime.now());
                    article.setContent(fileName);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        boolean saved = articleMapper.insertOrUpdate(article);
        // 保存成功后，将已发布文章推送到 agent（旁路容错，失败不影响主流程）
        if (saved) {
            pushToAgent(article, (String) content.get("content"));
        }
        return saved;
    }

    @Override
    public Article findArticleByName(String articleTitle) {
        QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("title", articleTitle);
        return articleMapper.selectOne(queryWrapper);
    }

    @Override
    public PageBean<Article> findAllArticlesWithPage(int uid, int pageNum, int pageSize,
                                                     Integer categoryId, Integer state, String search) {
        PageBean<Article> pageBean = new PageBean<>();
        int offset = (pageNum - 1) * pageSize;
        List<Article> allArticles = articleMapper.allArticles(uid, pageNum, pageSize, categoryId, state, offset, search);
        for (Article article : allArticles) {
            article.setContent(rustFsUtil.getContent(article.getContent()));
            if (!Objects.equals(article.getCoverImg(), "")) {
                article.setCoverImgSrc(rustFsUtil.getPciUrl(article.getCoverImg()));
            } else {
                article.setCoverImg("");
            }
        }
        pageBean.setItems(allArticles);
        return pageBean;
    }

    @Override
    public boolean findArticleById(int id) {
        return articleMapper.selectById(id) != null;
    }

    @Override
    public int findAllArticlesWithConditions(int uid, Integer categoryId, Integer state, String search) {
        return articleMapper.findAllArticlesWithConditions(uid, categoryId, state, search);
    }

    @Override
    public Article findArticleByIdWithEntity(int id) {
        Article article = articleMapper.findArticleById(id);
        if (article == null) {
            return null;
        }
        article.setContent(rustFsUtil.getContent(article.getContent()));
        if (!Objects.equals(article.getCoverImg(), "")) {
            article.setCoverImgSrc(rustFsUtil.getPciUrl(article.getCoverImg()));
        } else {
            article.setCoverImg("");
        }
        return article;
    }

    @Override
    public boolean deleteById(int id) {
        Article article = articleMapper.selectById(id);
        String imgName = article.getCoverImg();
        String contentName = article.getContent();
        if (rustFsUtil.delete(imgName, "image")
                && rustFsUtil.delete(contentName, "json")) {
            stringRedisTemplateArticle.opsForHash().delete(ARTICLE_PENDING_VIEWS_KEY, String.valueOf(id));
            boolean deleted = articleMapper.deleteById(id) == 1;
            // 同步从 agent 删除索引（旁路容错）
            if (deleted) {
                agentClient.deleteArticles(Collections.singletonList((long) id));
            }
            return deleted;
        }
        return false;
    }

    @Override
    public Map<String, String> upload(MultipartFile cover, String username) {
        Map<String, String> info = new HashMap<>();
        String extension = Objects.requireNonNull(cover.getOriginalFilename()).substring(cover.getOriginalFilename().lastIndexOf("."));
        String fileName = System.currentTimeMillis() + username + extension;
        if (rustFsUtil.upload(cover, "image", fileName)) {
            info.put("key", fileName);
            String src = rustFsUtil.getPciUrl(fileName);
            if (src != null) {
                info.put("src", src);
            }
            return info;
        }
        return info;
    }


    @Override
    public boolean removeCover(String key) {
        stringRedisTemplateArticle.delete(key);
        return rustFsUtil.delete(key, "image");
    }

    @Override
    public int findAllArticlesCountInMaster(Integer categoryId, Integer state, Integer userId, String search, Integer searchType, String nickName) {
        return articleMapper.findAllArticlesCountInMaster(categoryId, state, userId, search, searchType, nickName);
    }

    @Override
    public int findAllArticlesCountInPublic(Integer categoryId, Integer state, String search, Integer searchType, String nickName) {
        return articleMapper.findAllArticlesCountInPublic(categoryId, state, search, searchType, nickName);
    }

    @Override
    public PageBean<Article> findAllArticlesInPublic(int pageNum, int pageSize, Integer categoryId,
                                                     Integer state, String search, Integer searchType, String nickName) {
        PageBean<Article> pageBean = new PageBean<>();
        int offset = (pageNum - 1) * pageSize;
        List<Article> allArticles = articleMapper.findAllArticlesInPublic
                (pageNum, pageSize, categoryId, state, offset, search, searchType, nickName);
        for (Article article : allArticles) {
            String extractorContent = TextExtractor.extractor
                    (rustFsUtil.getContent(article.getContent()), 100);
            if (extractorContent.length() == 100) extractorContent += "...";
            article.setContent(extractorContent);
            if (!Objects.equals(article.getCoverImg(), "")) {
                article.setCoverImgSrc(rustFsUtil.getPciUrl(article.getCoverImg()));
            } else {
                article.setCoverImg("");
            }
            Long v = article.getViews();
            article.setViews(v != null ? v : 0L);
        }
        pageBean.setItems(allArticles);
        return pageBean;
    }

    public PageBean<Article> findAllArticlesInMaster(int pageNum, int pageSize, Integer categoryId,
                                                     Integer state, Integer userId, String search, Integer searchType, String nickName) {
        PageBean<Article> pageBean = new PageBean<>();
        int offset = (pageNum - 1) * pageSize;
        List<Article> allArticles = articleMapper.findAllArticlesInMaster
                (pageNum, pageSize, categoryId, state, userId, offset, search, searchType, nickName);
        for (Article article : allArticles) {
            article.setContent(rustFsUtil.getContent(article.getContent()));
            if (!Objects.equals(article.getCoverImg(), "")) {
                article.setCoverImgSrc(rustFsUtil.getPciUrl(article.getCoverImg()));
            } else {
                article.setCoverImg("");
            }
        }
        pageBean.setItems(allArticles);
        return pageBean;
    }

    @Override
    public boolean updateState(int id, int state) {
        UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .set("state", state);
        boolean updated = articleMapper.update(updateWrapper) == 1;
        if (updated) {
            if (state == 1) {
                // 审核通过（发布）：推送完整文章到 agent
                Article article = articleMapper.selectById(id);
                if (article != null) {
                    pushToAgent(article, rustFsUtil.getContent(article.getContent()));
                }
            } else {
                // 下架 / 驳回 / 转草稿：从 agent 移除索引
                agentClient.deleteArticles(Collections.singletonList((long) id));
            }
        }
        return updated;
    }

    /**
     * 将已发布文章推送到 agent（旁路容错：任何异常只记录日志，不向上抛）。
     */
    private void pushToAgent(Article article, String rawContent) {
        if (article == null || article.getState() == null || article.getState() != 1) {
            return;
        }
        try {
            String authorName = null;
            User author = userService.findUserById(article.getCreateUser());
            if (author != null) {
                authorName = author.getNickname();
            }
            String categoryName = null;
            if (article.getCategoryId() != null) {
                Category category = categoryService.findById(article.getCategoryId());
                if (category != null) {
                    categoryName = category.getCategoryName();
                }
            }
            String coverUrl = null;
            if (article.getCoverImg() != null && !article.getCoverImg().isEmpty()) {
                coverUrl = rustFsUtil.getPciUrl(article.getCoverImg());
            }
            com.articleTraceBack.rpc.gen.Article proto = ArticleProtoMapper.toProto(
                    article, rawContent, authorName, categoryName, coverUrl);
            agentClient.ingestArticle(proto);
        } catch (Exception e) {
            log.warn("push article to agent failed: articleId={}", article.getId(), e);
        }
    }

    @Override
    public void addViews(String username, int articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article.getState() != 1) {
            return;
        }
        String dedupKey = ARTICLE_PENDING_VIEWS_KEY + ":" + username + ":" + articleId;
        if (stringRedisTemplateArticle.hasKey(dedupKey)) return;

        stringRedisTemplateArticle.opsForValue().set(dedupKey, "", 1, TimeUnit.DAYS);
        stringRedisTemplateArticle.opsForHash().increment(ARTICLE_PENDING_VIEWS_KEY,
                String.valueOf(articleId), 1);
    }

    @Override
    public Long getViews(int id) {
        return articleMapper.getArticleViews(id);
    }

    @Override
    public List<Article> getViewsTop10() {
        String cacheKey = "article:hot:top10";
        String cached = stringRedisTemplateArticle.opsForValue().get(cacheKey);
        if (cached != null) {
            String[] parts = cached.split(",");
            List<Article> articles = new ArrayList<>();
            for (int i = 0; i < parts.length; i += 3) {
                Article art = new Article();
                art.setId(Integer.parseInt(parts[i]));
                art.setTitle(parts[i + 1]);
                art.setViews(Long.parseLong(parts[i + 2]));
                articles.add(art);
            }
            return articles;
        }
        List<Article> articles = articleMapper.getHotArticlesTop10();
        StringBuilder sb = new StringBuilder();
        for (Article a : articles) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(a.getId()).append(",").append(a.getTitle()).append(",").append(a.getViews());
        }
        stringRedisTemplateArticle.opsForValue().set(cacheKey, sb.toString(),
                HOT_ARTICLES_TTL_MINUTES, TimeUnit.MINUTES);
        return articles;
    }
}
