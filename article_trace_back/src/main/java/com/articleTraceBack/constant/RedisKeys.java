package com.articleTraceBack.constant;

/**
 * Redis key 常量。
 *
 * <p>只收「跨类共用」的 key——同一个 key 在多处拼写，改一处漏一处就会产生写进去读不到的幽灵 key。
 * 仅单个类内部使用的 key 留在原类，就近维护，不往这里搬。</p>
 *
 * <p>另有一批 key 由配置文件驱动（{@code spring.data.redis.viewKey}、
 * {@code rpc.agent.sessionKeyPrefix} 等），那些属于部署时可能调整的参数，继续留在 yml。</p>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 浏览量 Top10 热门文章缓存（ArticleServiceImpl 写入，SyncRedisToDbTask 同步后失效） */
    public static final String ARTICLE_HOT_TOP10 = "article:hot:top10";
}
