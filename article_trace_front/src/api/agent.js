import {tokenStorage} from "@/stores/tokenStorage.js";
import request from "@/utils/request.js";

/**
 * 流式问答：调用 /agent/ask（SSE 流式），通过回调逐段接收 LLM 答案。
 *
 * 事件序列：session（会话 ID，首条，可选）→ articles（命中文章，可选）→ delta（答案增量，多次）→ done / error。
 *
 * @param {Object} params - { query, sessionId?, categoryId?, topK? }
 * @param {Object} callbacks - { onSession?, onArticles?, onDelta?, onError?, onDone? }
 */
export async function askAgentStream(params, callbacks) {
    const {query, sessionId, categoryId, topK} = params;
    const {onSession, onArticles, onDelta, onError, onDone} = callbacks;

    const resp = await fetch('/api/agent/ask', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': tokenStorage().token || ''
        },
        body: JSON.stringify({query, sessionId, categoryId, topK})
    });

    if (!resp.ok) {
        throw new Error('HTTP ' + resp.status);
    }

    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
        const {done, value} = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, {stream: true});

        // SSE 事件以空行分隔
        const chunks = buffer.split('\n\n');
        buffer = chunks.pop();

        for (const raw of chunks) {
            let event = 'message';
            const dataLines = [];
            for (const line of raw.split('\n')) {
                if (line.startsWith('event:')) {
                    event = line.slice(6).trim();
                } else if (line.startsWith('data:')) {
                    dataLines.push(line.slice(5).trim());
                }
            }
            const data = dataLines.join('\n');
            if (!data) continue;

            switch (event) {
                case 'session':
                    onSession?.(data);
                    break;
                case 'articles':
                    try {
                        onArticles?.(JSON.parse(data));
                    } catch (e) {
                        // 忽略解析失败
                    }
                    break;
                case 'delta':
                    onDelta?.(data);
                    break;
                case 'error':
                    onError?.(data);
                    break;
                case 'done':
                    onDone?.();
                    break;
            }
        }
    }
}

/**
 * 列出当前用户的会话（按更新时间倒序）。
 */
export function listSessions() {
    return request.get("/agent/sessions")
}

/**
 * 获取指定会话的历史消息。
 */
export function getSessionMessages(sessionId, limit = 0) {
    return request.get("/agent/sessions/" + sessionId + "/messages", {params: {limit}})
}

/**
 * 删除指定会话。
 */
export function deleteSession(sessionId) {
    return request.delete("/agent/sessions/" + sessionId)
}
