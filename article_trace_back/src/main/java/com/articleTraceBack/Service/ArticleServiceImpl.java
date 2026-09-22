package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.Utils.FileCheckUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.config.SensitiveWordHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.articleTraceBack.constant.RedisKeys;
import com.articleTraceBack.Utils.RichTextCleaner;
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

    /** 热门文章缓存的 JSON 编解码器（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ArticleMapper articleMapper;
    private final RustFsUtil rustFsUtil;
    private final SensitiveWordHolder sensitiveWordHolder;
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
                              SensitiveWordHolder sensitiveWordHolder) {
        this.sensitiveWordHolder = sensitiveWordHolder;
        this.articleMapper = articleMapper;
        this.rustFsUtil = rustFsUtil;
        this.stringRedisTemplateArticle = stringRedisTemplateArticle;
    }
    @Override
    public List<AhoCorasickUtil.Match> containsSensitive(String content) {
        // 每次现取：词表热更新后替换的是 holder 里的引用，业务必须拿当前实例
        return sensitiveWordHolder.get().search(content);
    }
    @Override
    public boolean articleAddOrUpdate(Article article, int type, MultipartFile cover, String username) {
        // 富文本落库前按白名单清洗：正文会被前端 v-html 直接渲染，脏 HTML 就是存储型 XSS。
        // 放在这里而不是 Controller，是因为这是唯一的写库点，谁调用都绕不过去。
        article.setContent(RichTextCleaner.cleanToSafeHtml(article.getContent()));

        // 0为新增，1为更新
        Map<String, Object> content = new HashMap<>();
        content.put("content", article.getContent());
        long timeStamp = System.currentTimeMillis();
        String fileName = timeStamp + "-" + article.getCreateUser() + ".json";

        Article rawArticle = (type == 0) ? null : articleMapper.selectById(article.getId());
        // 旧对象名：要等写库成功后才删它们
        String oldContentName = rawArticle == null ? null : rawArticle.getContent();
        String oldImgName = rawArticle == null ? null : rawArticle.getCoverImg();

        // ---- 封面：有文件就上传新图，没有文件则只接受「清空」或「保持原值」----
        String newCoverKey = null;
        if (cover != null && !cover.isEmpty()) {
            newCoverKey = uploadCoverObject(cover, username);
            if (newCoverKey == null) {
                return false;
            }
            article.setCoverImg(newCoverKey);
        } else {
            // 客户端传来的 coverImg 不可信：只有明确传空串才表示「删除封面」，
            // null 或任何其它取值一律回退为库中原值，避免文章引用了别人的对象。
            String requested = article.getCoverImg();
            String current = oldImgName == null ? "" : oldImgName;
            article.setCoverImg(requested != null && requested.isBlank() ? "" : current);
        }

        if (type == 0) {
            if (!rustFsUtil.upload(content, "json", fileName)) {
                // 正文没传上去，回收刚上传的封面
                if (newCoverKey != null) {
                    rustFsUtil.delete(newCoverKey, "image");
                }
                return false;
            }
            article.setContent(fileName);
            article.setCreateTime(LocalDateTime.now());
        } else {
            if (!rustFsUtil.upload(content, "json", fileName)) {
                if (newCoverKey != null) {
                    rustFsUtil.delete(newCoverKey, "image");
                }
                return false;
            }
            article.setUpdateTime(LocalDateTime.now());
            article.setContent(fileName);
        }

        // 写库必须在「删旧文件」之前：写库可能因标题唯一索引冲突等原因失败，
        // 若先删了旧文件，DB 会指向一个已不存在的对象，正文永久变空且不可逆。
        boolean saved;
        try {
            saved = articleMapper.insertOrUpdate(article);
        } catch (Exception e) {
            // 写库失败（如 DuplicateKeyException）：回收本次上传的新文件，旧文件始终没动过，
            // 原样抛出交给上层转成友好提示。
            rustFsUtil.delete(fileName, "json");
            if (newCoverKey != null) {
                rustFsUtil.delete(newCoverKey, "image");
            }
            throw e;
        }
        if (!saved) {
            // 没抛异常但也没写成功：同样回收新文件，DB 保持指向旧文件
            rustFsUtil.delete(fileName, "json");
            if (newCoverKey != null) {
                rustFsUtil.delete(newCoverKey, "image");
            }
            return false;
        }

        // 走到这里说明 DB 已指向新对象；旧对象删除失败只会留下孤儿，不影响可用性。
        // 只在「更新」分支才需要清理旧对象——新增时本来就没有旧对象。
        if (type != 0) {
            if (!Objects.equals(oldContentName, fileName)
                    && !rustFsUtil.delete(oldContentName, "json")) {
                log.warn("delete old content failed: articleId={}, key={}", article.getId(), oldContentName);
            }
            // 换图或删图（新旧不一致）时，旧封面已无人引用
            if (!Objects.equals(oldImgName, "") && !oldImgName.equals(article.getCoverImg())
                    && !rustFsUtil.delete(oldImgName, "image")) {
                log.warn("delete old cover failed: articleId={}, key={}", article.getId(), oldImgName);
            }
        }

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
            article.setContent(readCleanContent(article.getContent()));
            if (!Objects.equals(article.getCoverImg(), "")) {
                article.setCoverImgSrc(rustFsUtil.getPciUrl(article.getCoverImg()));
                article.setCoverThumbSrc(rustFsUtil.getThumbUrl(article.getCoverImg()));
            } else {
                article.setCoverImg("");
            }
        }
        // 这是作者看自己的列表，命中词不能给
        Article.hideSensitiveDetail(allArticles);
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
        // 该实体既服务详情页也服务内部校验，但调用方都不需要「命中违禁词」这两个字段，
        // 一律清掉——详情接口对所有人开放，不能从这里反推词库
        Article.hideSensitiveDetail(List.of(article));
        article.setContent(readCleanContent(article.getContent()));
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

    /** 上传封面对象并返回对象名，校验不通过或上传失败返回 null；对象名由服务端生成，客户端文件名只用于取扩展名。 */
    private String uploadCoverObject(MultipartFile cover, String username) {
        if (!FileCheckUtil.isAcceptableImage(cover)) {
            log.warn("reject cover upload: unacceptable image, user={}, name={}, contentType={}",
                    username, cover.getOriginalFilename(), cover.getContentType());
            return null;
        }
        String fileName = System.currentTimeMillis() + username + FileCheckUtil.extensionOf(cover);
        return rustFsUtil.upload(cover, "image", fileName) ? fileName : null;
    }

    /** 从对象存储取正文并做白名单清洗，用于覆盖修复前遗留的历史数据（正文会经 {@code v-html} 直接渲染）。 */
    private String readCleanContent(String fileKey) {
        return RichTextCleaner.cleanToSafeHtml(rustFsUtil.getContent(fileKey));
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
        // 公开列表同理：命中词只给审核方
        Article.hideSensitiveDetail(allArticles);
        pageBean.setItems(allArticles);
        return pageBean;
    }

    /** 站长后台：全站文章分页（联查分类与作者） */
    public PageBean<Article> findAllArticlesInMaster(int pageNum, int pageSize, Integer categoryId,
                                                     Integer state, Integer userId, String search, Integer searchType, String nickName) {
        PageBean<Article> pageBean = new PageBean<>();
        int offset = (pageNum - 1) * pageSize;
        List<Article> allArticles = articleMapper.findAllArticlesInMaster
                (pageNum, pageSize, categoryId, state, userId, offset, search, searchType, nickName);
        for (Article article : allArticles) {
            article.setContent(readCleanContent(article.getContent()));
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
                // 仅站长：审核通过（2→1）、直接发布自己的草稿（0→1），
                // 以及「已发布文章改完仍保持已发布」（1→1）。没有最后这条，站长想改个错字
                // 就只能先下架再发布，中间文章是离线的。
                // 注意：文章命中违禁词时，目标状态在 Controller 里已被改成待审，根本走不到这一支。
                return master && (from == STATE_PENDING || from == STATE_DRAFT
                        || from == STATE_PUBLISHED);
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
        String cacheKey = RedisKeys.ARTICLE_HOT_TOP10;
        List<Article> cached = readHotArticlesCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<Article> articles = articleMapper.getHotArticlesTop10();
        writeHotArticlesCache(cacheKey, articles);
        return articles;
    }

    /** 读热门文章缓存：用 JSON 而非逗号拼接（标题可含逗号）；缓存损坏时返回 {@code null} 并清键，由调用方回源。 */
    private List<Article> readHotArticlesCache(String cacheKey) {
        String cached = stringRedisTemplateArticle.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(cached, new TypeReference<List<Article>>() {
            });
        } catch (Exception e) {
            log.warn("hot articles cache unreadable, fallback to db: key={}", cacheKey, e);
            stringRedisTemplateArticle.delete(cacheKey);
            return null;
        }
    }

    /** 写热门文章缓存。写失败只记日志——缓存是加速手段，不该影响主流程。 */
    private void writeHotArticlesCache(String cacheKey, List<Article> articles) {
        try {
            stringRedisTemplateArticle.opsForValue().set(cacheKey,
                    OBJECT_MAPPER.writeValueAsString(articles),
                    HOT_ARTICLES_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("write hot articles cache failed: key={}", cacheKey, e);
        }
    }
}
