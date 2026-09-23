package com.articleTraceBack.constant;

/** Redis key 常量：只收「跨类共用」的 key，单类内部使用的留在原类；配置文件驱动的 key（如 viewKey）继续留在 yml。 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 浏览量 Top10 热门文章缓存（ArticleServiceImpl 写入，SyncRedisToDbTask 同步后失效） */
    public static final String ARTICLE_HOT_TOP10 = "article:hot:top10";

    /** 昵称改名锁定期（后接 userId）：成功改名或审核通过后 7 天内不能再改 */
    public static final String PROFILE_NICKNAME_LOCK = "profile:nickname:lock:";
}
