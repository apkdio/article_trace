package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.Utils.TextExtractor;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.PageBean;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ArticleServiceImpl implements ArticleService {
    private final ArticleMapper articleMapper;
    private final RustFsUtil rustFsUtil;
    private final AhoCorasickUtil ahoCorasickUtil;
    @Qualifier("stringRedisTemplateArticle")
    private final StringRedisTemplate stringRedisTemplateArticle;
    @Value("${spring.data.redis.viewKey}")
    private String ARTICLE_VIEW_KEY;
    @Value("${spring.data.redis.infoKey}")
    private String ARTICLE_INFO_KEY;


    public ArticleServiceImpl(ArticleMapper articleMapper, RustFsUtil rustFsUtil,
                              @Qualifier("stringRedisTemplateArticle") StringRedisTemplate stringRedisTemplateArticle,
                              AhoCorasickUtil ahoCorasickUtil) {
        this.ahoCorasickUtil = ahoCorasickUtil;
        this.articleMapper = articleMapper;
        this.rustFsUtil = rustFsUtil;
        this.stringRedisTemplateArticle = stringRedisTemplateArticle;
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
        String hashKey = ARTICLE_INFO_KEY + ":" + article.getId();
        stringRedisTemplateArticle.opsForHash().put(hashKey, "title", article.getTitle());
        stringRedisTemplateArticle.opsForHash().put(hashKey, "state", Integer.toString(article.getState()));
        return articleMapper.insertOrUpdate(article);
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
            stringRedisTemplateArticle.delete(ARTICLE_INFO_KEY + id);
            stringRedisTemplateArticle.opsForZSet().remove(ARTICLE_VIEW_KEY, String.valueOf(id));
            return articleMapper.deleteById(id) == 1;
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
            article.setViews(getViews(article.getId()) != null ? getViews(article.getId()) : 0);
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
        String hashKey = ARTICLE_INFO_KEY + ":" + id;
        if (stringRedisTemplateArticle.hasKey(hashKey)) {
            stringRedisTemplateArticle.opsForHash().put(hashKey, "state", Integer.toString(state));
        } else {
            stringRedisTemplateArticle.opsForHash().put(hashKey, "title", articleMapper.selectById(id).getTitle());
            stringRedisTemplateArticle.opsForHash().put(hashKey, "state", Integer.toString(state));
        }
        return articleMapper.update(updateWrapper) == 1;
    }

    @Override
    public void addViews(String username, int articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article.getState() != 1) {
            return;
        }
        String key = ARTICLE_VIEW_KEY + ":" + username + ":" + articleId;
        if (stringRedisTemplateArticle.hasKey(key)) return;

        String hashKey = ARTICLE_INFO_KEY + ":" + articleId;
        if (!stringRedisTemplateArticle.hasKey(hashKey)) {
            stringRedisTemplateArticle.opsForHash().put(hashKey, "title", article.getTitle());
            stringRedisTemplateArticle.opsForHash().put(hashKey, "state", article.getState().toString());
        }

        stringRedisTemplateArticle.opsForValue().set(key, "", 1, TimeUnit.DAYS);
        stringRedisTemplateArticle.opsForZSet().incrementScore(ARTICLE_VIEW_KEY,
                String.valueOf(articleId), 1);
    }

    @Override
    public Long getViews(int id) {
        try {
            return Objects.requireNonNull(stringRedisTemplateArticle.opsForZSet().score(ARTICLE_VIEW_KEY, String.valueOf(id))).longValue();
        } catch (NullPointerException e) {
            Long view = articleMapper.findArticleById(id).getViews();
            stringRedisTemplateArticle.opsForZSet().add(ARTICLE_VIEW_KEY, String.valueOf(id), view);
            return view;
        }
    }

    @Override
    public List<Article> getViewsTop10() {
        // 提取25个数据
        Set<ZSetOperations.TypedTuple<String>> views =
                stringRedisTemplateArticle.opsForZSet().reverseRangeWithScores(ARTICLE_VIEW_KEY, 0, 24);
        List<Article> articles = new ArrayList<>();
        if (views != null && !views.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> tuple : views) {
                if (articles.size() == 10) {
                    break;
                }
                if (Objects.requireNonNull(tuple.getScore()).longValue() == 0) {
                    break;
                }
                Article art = new Article();
                String hashKey = ARTICLE_INFO_KEY + ":" + tuple.getValue();
                if (stringRedisTemplateArticle.hasKey(hashKey)) {
                    String state = (Objects.requireNonNull(stringRedisTemplateArticle.opsForHash().
                            get(hashKey, "state"))).toString();
                    if (!Objects.equals(state, "1")) {
                        continue;
                    }
                    String articleName = Objects.requireNonNull(stringRedisTemplateArticle.opsForHash().
                            get(hashKey, "title")).toString();
                    art.setTitle(articleName);
                } else {
                    Article thisArticle = articleMapper.findArticleById(Integer.parseInt(Objects.requireNonNull(tuple.getValue())));
                    if (thisArticle.getState() != 1) {
                        continue;
                    }
                    art.setTitle(thisArticle.getTitle());
                    stringRedisTemplateArticle.opsForHash().put(hashKey, "title", thisArticle.getTitle());
                    stringRedisTemplateArticle.opsForHash().put(hashKey, "state", thisArticle.getState().toString());
                }
                art.setViews(Objects.requireNonNull(tuple.getScore()).longValue());
                art.setId(Integer.parseInt(Objects.requireNonNull(tuple.getValue())));
                articles.add(art);
            }
        }

        return articles;
    }
}
