package com.articleTraceBack.Service;

import com.articleTraceBack.rpc.ArticleAgentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 会话索引管理实现：Redis Set 维护「用户 ↔ 会话」归属，删除时联动 agent 侧数据。
 *
 * <p>索引故意不设 TTL —— 它指向 agent 侧持久保存的会话记录，过期会导致用户
 * 凭空看不到历史会话。因此清理只发生在显式删除（用户删会话 / 账号注销）。</p>
 */
@Slf4j
@Service
public class AgentSessionServiceImpl implements AgentSessionService {

    private final StringRedisTemplate stringRedisTemplateArticle;
    private final ArticleAgentClient agentClient;

    @Value("${rpc.agent.sessionKeyPrefix:agent:session:user:}")
    private String sessionKeyPrefix;

    public AgentSessionServiceImpl(
            @Qualifier("stringRedisTemplateArticle") StringRedisTemplate stringRedisTemplateArticle,
            ArticleAgentClient agentClient) {
        this.stringRedisTemplateArticle = stringRedisTemplateArticle;
        this.agentClient = agentClient;
    }

    @Override
    public void remember(String userId, String sessionId) {
        if (!agentClient.isEnabled() || userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            stringRedisTemplateArticle.opsForSet().add(sessionKey(userId), sessionId);
        } catch (Exception e) {
            log.warn("remember agent session failed: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    @Override
    public boolean owns(String userId, String sessionId) {
        if (!agentClient.isEnabled() || userId == null || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplateArticle.opsForSet().isMember(sessionKey(userId), sessionId));
    }

    @Override
    public List<String> listSessionIds(String userId) {
        if (!agentClient.isEnabled() || userId == null) {
            return Collections.emptyList();
        }
        Set<String> ids = stringRedisTemplateArticle.opsForSet().members(sessionKey(userId));
        return ids == null ? Collections.emptyList() : new ArrayList<>(ids);
    }

    @Override
    public void forget(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        stringRedisTemplateArticle.opsForSet().remove(sessionKey(userId), sessionId);
    }

    @Override
    public void clearAll(String userId) {
        if (userId == null) {
            return;
        }
        try {
            if (agentClient.isEnabled()) {
                for (String sessionId : listSessionIds(userId)) {
                    // agent 侧删除幂等，失败也不阻断（索引照常清掉，避免残留脏映射）
                    agentClient.deleteSession(sessionId);
                }
            }
            // 索引照常清掉：即使 agent 已禁用，也不留本地残留映射
            stringRedisTemplateArticle.delete(sessionKey(userId));
        } catch (Exception e) {
            log.warn("clear agent sessions failed: userId={}", userId, e);
        }
    }

    private String sessionKey(String userId) {
        return sessionKeyPrefix + userId;
    }
}
