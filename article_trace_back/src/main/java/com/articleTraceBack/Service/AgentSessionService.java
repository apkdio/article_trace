package com.articleTraceBack.Service;

import java.util.List;

/**
 * 用户 ↔ agent 会话 的索引管理。
 *
 * <p>会话数据（历史 jsonl）由 agent 侧持久化，本服务只维护 Redis 中的归属索引
 * （{@code agent:session:user:{userId}} → Set&lt;sessionId&gt;}），并在用户注销等
 * 场景下联动清理 agent 侧的会话数据。</p>
 */
public interface AgentSessionService {

    /** 记录会话归属（幂等）；userId / sessionId 为空时忽略 */
    void remember(String userId, String sessionId);

    /** 判断会话是否归属于该用户 */
    boolean owns(String userId, String sessionId);

    /** 列出该用户的所有会话 ID */
    List<String> listSessionIds(String userId);

    /** 仅从索引中移除单个会话（不删除 agent 侧数据） */
    void forget(String userId, String sessionId);

    /** 移除该用户的全部会话：逐个删除 agent 侧数据，再清空索引 */
    void clearAll(String userId);
}
