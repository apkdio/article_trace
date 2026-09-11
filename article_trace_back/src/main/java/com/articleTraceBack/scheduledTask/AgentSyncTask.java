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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 * 知识库定时同步任务。
 *
 * <p>增量同步（每 5 分钟）：业务侧（ArticleServiceImpl）在文章增删改、审核时只往
 * Redis 写入待处理的文章 id，本任务批量读取并统一推送到 article_trace_agent，成功
 * 后清空 Redis，失败则保留下次重试。目的是把「每篇一次」的推送合并为「每批一次」，
 * 避免频繁的 BM25 索引重建与知识库 IO。</p>
 *
 * <p>全量对账（每天凌晨 1 点）：兜底机制——若 Redis 丢失或增量同步漏推，定时把全部
 * 已发布文章重新推送一遍（agent 侧按 article.id 幂等覆盖，重复推送无害）。</p>
 *
 * <p>所有批量推送均按 batchSize 分批，避免单次 RPC 体量过大。</p>
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

    @Value("${rpc.agent.enabled:true}")
    private boolean agentEnabled;
    @Value("${rpc.agent.sync.enabled:true}")
    private boolean enabled;
    @Value("${rpc.agent.sync.batchSize:100}")
    private int batchSize;
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
        if (!agentEnabled || !enabled) {
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

            // 2. 再处理入库/更新（已发布的文章），按批次上限分批推送
            if (!ingestIds.isEmpty()) {
                List<Integer> ids = toIntList(ingestIds);
                if (ingestInBatches(ids)) {
                    stringRedisTemplateArticle.opsForSet().remove(ingestKey, ingestIds.toArray(new Object[0]));
                    log.info("agent sync: cleared {} pending ingest id(s)", ids.size());
                } else {
                    log.warn("agent sync: ingest partially failed, pending kept for retry");
                }
            }
        } catch (Exception e) {
            log.error("agent sync task failed", e);
        }
    }

    /**
     * 全量对账：每天凌晨 1 点，把全部已发布文章重新推送一遍（幂等覆盖，兜底增量同步的丢失）。
     */
    @Scheduled(cron = "${rpc.agent.sync.fullSyncCron:0 0 1 * * ?}")
    public void fullSync() {
        if (!agentEnabled || !enabled) {
            return;
        }
        try {
            QueryWrapper<Article> wrapper = new QueryWrapper<>();
            wrapper.eq("state", 1);
            List<Article> published = articleMapper.selectList(wrapper);
            if (published.isEmpty()) {
                log.info("full sync: no published article, skip");
                return;
            }
            List<Integer> ids = new ArrayList<>();
            for (Article a : published) {
                ids.add(a.getId());
            }
            ingestInBatches(ids);
            log.info("full sync: pushed {} published article(s)", ids.size());
        } catch (Exception e) {
            log.error("full sync failed", e);
        }
    }

    /**
     * 按 batchSize 分批构造并推送文章，返回是否全部成功。
     */
    private boolean ingestInBatches(List<Integer> ids) {
        int size = batchSize > 0 ? batchSize : 100;
        boolean allOk = true;
        int totalBatches = (ids.size() + size - 1) / size;
        for (int i = 0; i < ids.size(); i += size) {
            List<Integer> batch = ids.subList(i, Math.min(i + size, ids.size()));
            List<com.articleTraceBack.rpc.gen.Article> protoArticles = buildProtoArticles(batch);
            if (protoArticles.isEmpty()) {
                continue;
            }
            BatchIngestReply reply = agentClient.batchIngestArticles(protoArticles);
            if (reply.getOk()) {
                log.info("agent sync: ingested {} article(s) (batch {}/{})",
                        protoArticles.size(), i / size + 1, totalBatches);
            } else {
                log.warn("agent sync: ingest batch {}/{} failed: {}",
                        i / size + 1, totalBatches, reply.getMessage());
                allOk = false;
            }
        }
        return allOk;
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
