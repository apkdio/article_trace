package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.Utils.TextExtractor;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.PageBean;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    @Qualifier("stringRedisTemplateArticle")
    private final StringRedisTemplate stringRedisTemplateArticle;
    @Value("${spring.data.redis.viewKey}")
    private String ARTICLE_PENDING_VIEWS_KEY;
    @Value("${rpc.agent.enabled:true}")
    private boolean agentEnabled;
    @Value("${rpc.agent.sync.ingestKey:agent:ingest:pending}")
    private String AGENT_INGEST_KEY;
    @Value("${rpc.agent.sync.deleteKey:agent:delete:pending}")
    private String AGENT_DELETE_KEY;

    private static final long HOT_ARTICLES_TTL_MINUTES = 5;


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
        boolean saved = articleMapper.insertOrUpdate(article);
        // 保存成功后标记待同步：已发布 → 待入库，非发布 → 待删除（避免草稿被检索）
        if (saved) {
            if (article.getState() != null && article.getState() == 1) {
                markIngestPending(article.getId());
            } else {
                markDeletePending(article.getId());
            }
        }
        return saved;
    }

    @Override
    public Article findArticleByUserAndTitle(int userId, String articleTitle) {
        QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
        // 标题在「同一作者」内唯一（uk_user_title），不同作者可以同名
        queryWrapper.eq("create_user", userId).eq("title", articleTitle).last("limit 1");
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
                article.setCoverThumbSrc(rustFsUtil.getThumbUrl(article.getCoverImg()));
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
            article.setCoverThumbSrc(rustFsUtil.getThumbUrl(article.getCoverImg()));
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
            // 标记待删除：由定时任务统一从 agent 移除索引
            if (deleted) {
                markDeletePending(id);
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
    public boolean removeCover(String key, int userId) {
        if (key == null || key.isBlank()) {
            return false;
        }
        // 客户端传来的 key 不可信：必须确认它正是该用户自己某篇文章的封面，才能删对象
        QueryWrapper<Article> wrapper = new QueryWrapper<>();
        wrapper.eq("cover_img", key).eq("create_user", userId).last("limit 1");
        if (articleMapper.selectCount(wrapper) == 0) {
            log.warn("refuse to remove cover not owned by user: key={}, userId={}", key, userId);
            return false;
        }
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
                article.setCoverThumbSrc(rustFsUtil.getThumbUrl(article.getCoverImg()));
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
                article.setCoverThumbSrc(rustFsUtil.getThumbUrl(article.getCoverImg()));
            } else {
                article.setCoverImg("");
            }
        }
        pageBean.setItems(allArticles);
        return pageBean;
    }

    @Override
    public boolean canTransfer(int from, int to, int roleType) {
        boolean master = (roleType == ROLE_MASTER);
        switch (to) {
            case STATE_DRAFT:
                // 任何已存在的状态都可以回到草稿（下架 / 撤回 / 放弃）
                return from == STATE_DRAFT || from == STATE_PUBLISHED
                        || from == STATE_PENDING || from == STATE_REJECTED;
            case STATE_PENDING:
                // 送审：草稿首投、已发布改后重送、已驳回改后重投
                return from == STATE_DRAFT || from == STATE_PUBLISHED
                        || from == STATE_REJECTED || from == STATE_PENDING;
            case STATE_PUBLISHED:
                // 仅站长：审核通过（2→1），或直接发布自己的草稿（0→1）
                return master && (from == STATE_PENDING || from == STATE_DRAFT);
            case STATE_REJECTED:
                // 仅站长：驳回待审（2→3），或追回已误审发布的文章（1→3）
                return master && (from == STATE_PENDING || from == STATE_PUBLISHED);
            default:
                return false;
        }
    }

    @Override
    public boolean updateState(int id, int state) {
        Article raw = articleMapper.selectById(id);
        if (raw == null || raw.getState() == null) {
            return false;
        }
        // 审核入口只做「发布 / 驳回」两类动作；「撤回为草稿」属于作者侧操作，不走这里
        if (state != STATE_PUBLISHED && state != STATE_REJECTED) {
            log.warn("article assess rejected non-assess target: id={}, target={}", id, state);
            return false;
        }
        // 审核入口只服务站长，且只允许 2→1 / 2→3 / 1→3
        if (!canTransfer(raw.getState(), state, ROLE_MASTER)) {
            log.warn("illegal article state transfer rejected: id={}, {} -> {}", id, raw.getState(), state);
            return false;
        }
        UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .eq("state", raw.getState())   // 条件更新：状态被并发改动过就失败
                .set("state", state);
        boolean updated = articleMapper.update(null, updateWrapper) == 1;
        if (updated) {
            // 审核通过 → 待入库；下架/驳回/转草稿 → 待删除
            if (state == 1) {
                markIngestPending(id);
            } else {
                markDeletePending(id);
            }
        }
        return updated;
    }

    /**
     * 标记文章待入库/更新到 agent 知识库（幂等，写入 Redis Set，由定时任务批量处理）。
     */
    private void markIngestPending(Integer articleId) {
        if (!agentEnabled || articleId == null) {
            return;
        }
        String id = String.valueOf(articleId);
        try {
            stringRedisTemplateArticle.opsForSet().add(AGENT_INGEST_KEY, id);
            // 后到操作覆盖先到操作：移除该 id 的待删除标记
            stringRedisTemplateArticle.opsForSet().remove(AGENT_DELETE_KEY, id);
        } catch (Exception e) {
            log.warn("mark ingest pending failed: articleId={}", articleId, e);
        }
    }

    /**
     * 标记文章待从 agent 知识库删除（幂等，写入 Redis Set，由定时任务批量处理）。
     */
    private void markDeletePending(Integer articleId) {
        if (!agentEnabled || articleId == null) {
            return;
        }
        String id = String.valueOf(articleId);
        try {
            stringRedisTemplateArticle.opsForSet().add(AGENT_DELETE_KEY, id);
            // 后到操作覆盖先到操作：移除该 id 的待入库标记
            stringRedisTemplateArticle.opsForSet().remove(AGENT_INGEST_KEY, id);
        } catch (Exception e) {
            log.warn("mark delete pending failed: articleId={}", articleId, e);
        }
    }

    @Override
    public void addViews(String username, int articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || article.getState() == null || article.getState() != 1) {
            return;
        }
        String dedupKey = ARTICLE_PENDING_VIEWS_KEY + ":" + username + ":" + articleId;
        // 原子去重：抢占成功才算一次新浏览，避免并发下同一人把计数刷高
        Boolean firstView = stringRedisTemplateArticle.opsForValue()
                .setIfAbsent(dedupKey, "", 1, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(firstView)) {
            return;
        }

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
