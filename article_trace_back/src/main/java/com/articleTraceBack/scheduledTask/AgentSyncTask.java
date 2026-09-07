package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Service.CategoryService;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.Category;
import com.articleTraceBack.pojo.User;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.ArticleProtoMapper;
import com.articleTraceBack.rpc.gen.BatchIngestReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 知识库定时批量同步任务。
 *
 * <p>业务侧（ArticleServiceImpl）在文章增删改、审核时只往 Redis 写入待处理的
 * 文章 id，本任务每 5 分钟批量读取并统一推送到 article_trace_agent，成功后清空
 * Redis，失败则保留下次重试。目的是把「每篇一次」的推送合并为「每批一次」，
 * 避免频繁的 BM25 索引重建与知识库 IO。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class AgentSyncTask {

    private final ArticleAgentClient agentClient;
    private final ArticleMapper articleMapper;
    private final UserService userService;
    private final CategoryService categoryService;
    private final RustFsUtil rustFsUtil;
    @Qualifier("stringRedisTemplateArticle")
    private final StringRedisTemplate stringRedisTemplateArticle;

    @Value("${rpc.agent.sync.enabled:true}")
    private boolean enabled;
    @Value("${rpc.agent.sync.ingestKey:agent:ingest:pending}")
    private String ingestKey;
    @Value("${rpc.agent.sync.deleteKey:agent:delete:pending}")
    private String deleteKey;

    public AgentSyncTask(ArticleAgentClient agentClient, ArticleMapper articleMapper,
                         @Lazy UserService userService, CategoryService categoryService,
                         RustFsUtil rustFsUtil,
                         @Qualifier("stringRedisTemplateArticle") StringRedisTemplate stringRedisTemplateArticle) {
        this.agentClient = agentClient;
        this.articleMapper = articleMapper;
        this.userService = userService;
        this.categoryService = categoryService;
        this.rustFsUtil = rustFsUtil;
        this.stringRedisTemplateArticle = stringRedisTemplateArticle;
    }

    @Scheduled(cron = "${rpc.agent.sync.cron:0 */5 * * * ?}")
    public void syncPendingUpdates() {
        if (!enabled) {
            return;
        }
        try {
            Set<String> ingestIds = stringRedisTemplateArticle.opsForSet().members(ingestKey);
            Set<String> deleteIds = stringRedisTemplateArticle.opsForSet().members(deleteKey);
            if (ingestIds.isEmpty() && deleteIds.isEmpty()) {
                return;
            }

            // 1. 先处理删除（下架/删除的文章）
            if (!deleteIds.isEmpty()) {
                List<Long> ids = toLongList(deleteIds);
                if (agentClient.deleteArticles(ids)) {
                    stringRedisTemplateArticle.opsForSet().remove(deleteKey, deleteIds.toArray(new Object[0]));
                    log.info("agent sync: deleted {} article(s) from knowledge base", ids.size());
                } else {
                    log.warn("agent sync: delete failed, {} id(s) kept for retry", ids.size());
                }
            }

            // 2. 再处理入库/更新（已发布的文章）
            if (!ingestIds.isEmpty()) {
                List<Integer> ids = toIntList(ingestIds);
                List<com.articleTraceBack.rpc.gen.Article> protoArticles = buildProtoArticles(ids);
                if (!protoArticles.isEmpty()) {
                    BatchIngestReply reply = agentClient.batchIngestArticles(protoArticles);
                    if (reply.getOk()) {
                        stringRedisTemplateArticle.opsForSet().remove(ingestKey, ingestIds.toArray(new Object[0]));
                        log.info("agent sync: ingested {} article(s) into knowledge base", protoArticles.size());
                    } else {
                        log.warn("agent sync: ingest failed, {} id(s) kept for retry: {}",
                                ids.size(), reply.getMessage());
                    }
                } else {
                    // 文章已被删除或非发布状态，直接清掉无意义的待入库标记
                    stringRedisTemplateArticle.opsForSet().remove(ingestKey, ingestIds.toArray(new Object[0]));
                }
            }
        } catch (Exception e) {
            log.error("agent sync task failed", e);
        }
    }

    /**
     * 批量读取文章并构造 proto 消息（只推已发布状态）。
     */
    private List<com.articleTraceBack.rpc.gen.Article> buildProtoArticles(List<Integer> ids) {
        List<com.articleTraceBack.rpc.gen.Article> result = new ArrayList<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Article> articles = articleMapper.selectBatchIds(ids);
        for (Article a : articles) {
            if (a.getState() == null || a.getState() != 1) {
                continue;
            }
            try {
                String content = rustFsUtil.getContent(a.getContent());
                String authorName = null;
                User author = userService.findUserById(a.getCreateUser());
                if (author != null) {
                    authorName = author.getNickname();
                }
                String categoryName = null;
                if (a.getCategoryId() != null) {
                    Category category = categoryService.findById(a.getCategoryId());
                    if (category != null) {
                        categoryName = category.getCategoryName();
                    }
                }
                String coverUrl = null;
                if (a.getCoverImg() != null && !a.getCoverImg().isEmpty()) {
                    coverUrl = rustFsUtil.getPciUrl(a.getCoverImg());
                }
                result.add(ArticleProtoMapper.toProto(a, content, authorName, categoryName, coverUrl));
            } catch (Exception e) {
                log.warn("build proto article failed: articleId={}", a.getId(), e);
            }
        }
        return result;
    }

    private List<Long> toLongList(Set<String> ids) {
        List<Long> result = new ArrayList<>();
        for (String id : ids) {
            try {
                result.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                log.warn("invalid article id in redis: {}", id);
            }
        }
        return result;
    }

    private List<Integer> toIntList(Set<String> ids) {
        List<Integer> result = new ArrayList<>();
        for (String id : ids) {
            try {
                result.add(Integer.parseInt(id));
            } catch (NumberFormatException e) {
                log.warn("invalid article id in redis: {}", id);
            }
        }
        return result;
    }
}
