<script setup>
import {ref, nextTick} from 'vue'
import {ChatDotRound, Clock, Close, Delete, Plus} from '@element-plus/icons-vue'
import {askAgentStream, deleteSession, getSessionMessages, listSessions} from "@/api/agent.js";

const open = ref(false)
const input = ref('')
const streaming = ref(false)
const messages = ref([])
const streamingContent = ref('')
const matchedArticles = ref([])
const chatBoxRef = ref(null)

// 多会话管理
const sessionId = ref('')
const sessions = ref([])
const showHistory = ref(false)
const loadingHistory = ref(false)

function toggle() {
    open.value = !open.value
    if (open.value) {
        showHistory.value = false
        scrollToBottom()
    }
}

async function send() {
    const query = input.value.trim()
    if (!query || streaming.value) return
    showHistory.value = false

    messages.value.push({role: 'user', content: query})
    input.value = ''
    streaming.value = true
    streamingContent.value = ''
    matchedArticles.value = []
    scrollToBottom()

    try {
        await askAgentStream(
            {query, sessionId: sessionId.value || undefined},
            {
                onSession: (sid) => {
                    if (sid) sessionId.value = sid
                },
                onArticles: (list) => {
                    matchedArticles.value = list || []
                },
                onDelta: (text) => {
                    streamingContent.value += text
                    scrollToBottom()
                },
                onError: (msg) => {
                    if (!streamingContent.value) {
                        streamingContent.value = msg || 'AI 助手暂时不可用。'
                    }
                }
            }
        )
        messages.value.push({
            role: 'assistant',
            content: streamingContent.value,
            articles: matchedArticles.value
        })
    } catch (e) {
        ElMessage.error('对话失败，请稍后重试')
        if (!streamingContent.value) {
            messages.value.push({role: 'assistant', content: '抱歉，AI 助手暂时不可用。'})
        }
    } finally {
        streaming.value = false
        streamingContent.value = ''
        matchedArticles.value = []
        scrollToBottom()
    }
}

function newSession() {
    if (streaming.value) return
    sessionId.value = ''
    messages.value = []
    streamingContent.value = ''
    matchedArticles.value = []
    showHistory.value = false
}

function toggleHistory() {
    if (showHistory.value) {
        showHistory.value = false
        return
    }
    showHistory.value = true
    loadSessions()
}

async function loadSessions() {
    loadingHistory.value = true
    try {
        const res = await listSessions()
        if (res.code === 0) {
            sessions.value = res.data || []
        } else {
            ElMessage.error(res.message || '加载会话列表失败')
        }
    } catch (e) {
        ElMessage.error('加载会话列表失败')
    } finally {
        loadingHistory.value = false
    }
}

async function switchSession(session) {
    if (streaming.value) return
    sessionId.value = session.sessionId
    showHistory.value = false
    messages.value = []
    try {
        const res = await getSessionMessages(session.sessionId)
        if (res.code === 0) {
            messages.value = (res.data || []).map(m => ({role: m.role, content: m.content}))
        } else {
            ElMessage.error(res.message || '加载会话记录失败')
        }
    } catch (e) {
        ElMessage.error('加载会话记录失败')
    }
    scrollToBottom()
}

async function removeSession(session) {
    if (streaming.value) return
    try {
        const res = await deleteSession(session.sessionId)
        if (res.code !== 0) {
            ElMessage.error(res.message || '删除会话失败')
            return
        }
        sessions.value = sessions.value.filter(s => s.sessionId !== session.sessionId)
        if (sessionId.value === session.sessionId) {
            sessionId.value = ''
            messages.value = []
        }
        ElMessage.success('已删除会话')
    } catch (e) {
        ElMessage.error('删除会话失败')
    }
}

function goArticle(id) {
    window.open(`/article/${id}`, '_blank')
}

function scrollToBottom() {
    nextTick(() => {
        if (chatBoxRef.value) {
            chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
        }
    })
}

/** 会话列表的时间展示：今天显示 HH:mm，否则显示 MM-DD */
function formatSessionTime(ts) {
    if (!ts) return ''
    const d = new Date(ts)
    if (isNaN(d.getTime())) return ''
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    if (d.toDateString() === new Date().toDateString()) {
        return `${hh}:${mm}`
    }
    const M = String(d.getMonth() + 1).padStart(2, '0')
    const D = String(d.getDate()).padStart(2, '0')
    return `${M}-${D}`
}
</script>

<template>
    <div class="agent-chat">
        <!-- 悬浮按钮 -->
        <button class="chat-fab" type="button" @click="toggle">
            <el-icon v-if="!open" :size="24">
                <ChatDotRound/>
            </el-icon>
            <el-icon v-else :size="24">
                <Close/>
            </el-icon>
        </button>

        <!-- 聊天面板 -->
        <transition name="chat-panel">
            <div v-if="open" class="chat-panel">
                <div class="chat-header">
                    <div class="header-text">
                        <span class="chat-title">文迹 AI 助手</span>
                        <span class="chat-subtitle">基于站内文章的知识问答</span>
                    </div>
                    <div class="header-actions">
                        <el-tooltip content="新建会话" placement="bottom">
                            <el-icon class="action-btn" @click="newSession">
                                <Plus/>
                            </el-icon>
                        </el-tooltip>
                        <el-tooltip content="历史会话" placement="bottom">
                            <el-icon class="action-btn" @click="toggleHistory">
                                <Clock/>
                            </el-icon>
                        </el-tooltip>
                    </div>
                </div>

                <!-- 历史会话列表 -->
                <div v-if="showHistory" class="session-list">
                    <div v-if="loadingHistory" class="session-tip">加载中...</div>
                    <div v-else-if="sessions.length === 0" class="session-tip">暂无历史会话</div>
                    <div v-for="s in sessions" v-else :key="s.sessionId"
                         :class="['session-item', {active: s.sessionId === sessionId}]"
                         @click="switchSession(s)">
                        <div class="session-main">
                            <div class="session-title">{{ s.title || '（空会话）' }}</div>
                            <div class="session-meta">{{ s.messageCount }} 条 · {{ formatSessionTime(s.updatedAt) }}</div>
                        </div>
                        <el-icon class="session-del" @click.stop="removeSession(s)">
                            <Delete/>
                        </el-icon>
                    </div>
                </div>

                <!-- 消息区 -->
                <div v-else class="chat-messages" ref="chatBoxRef">
                    <div v-if="messages.length === 0 && !streaming" class="chat-empty">
                        <el-icon :size="40">
                            <ChatDotRound/>
                        </el-icon>
                        <p>你好，我是文迹 AI 助手</p>
                        <p class="empty-tip">可以问我「有什么关于 xxx 的文章」</p>
                    </div>

                    <div v-for="(msg, index) in messages" :key="index" :class="['msg', msg.role]">
                        <div class="bubble">{{ msg.content }}</div>
                        <div v-if="msg.articles && msg.articles.length" class="refs">
                            <div v-for="a in msg.articles" :key="a.id" class="ref-item"
                                 @click="goArticle(a.id)">
                                <span class="ref-title">{{ a.title }}</span>
                            </div>
                        </div>
                    </div>

                    <!-- 流式输出中的答案 -->
                    <div v-if="streaming" class="msg assistant">
                        <div class="bubble">{{ streamingContent }}<span class="cursor">▍</span></div>
                    </div>
                </div>

                <div v-if="!showHistory" class="chat-input">
                    <el-input
                        v-model="input"
                        placeholder="问我关于文章的问题..."
                        :disabled="streaming"
                        @keyup.enter="send"
                    />
                    <el-button type="primary" :loading="streaming" @click="send">发送</el-button>
                </div>
            </div>
        </transition>
    </div>
</template>

<style lang="scss" scoped>
.agent-chat {
    position: fixed;
    right: 24px;
    bottom: 24px;
    z-index: 1000;

    .chat-fab {
        position: absolute;
        right: 0;
        bottom: 0;
        width: 56px;
        height: 56px;
        border-radius: 50%;
        border: none;
        background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
        color: #fff;
        cursor: pointer;
        box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
        display: flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.2s;

        &:hover {
            transform: scale(1.08);
        }
    }

    .chat-panel {
        position: absolute;
        right: 0;
        bottom: 68px;
        width: 360px;
        height: 500px;
        background: #fff;
        border-radius: 16px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.16);
        display: flex;
        flex-direction: column;
        overflow: hidden;

        .chat-header {
            background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
            color: #fff;
            padding: 14px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;

            .header-text {
                display: flex;
                flex-direction: column;
                gap: 2px;
            }

            .chat-title {
                font-size: 16px;
                font-weight: 600;
            }

            .chat-subtitle {
                font-size: 12px;
                opacity: 0.85;
            }

            .header-actions {
                display: flex;
                gap: 12px;

                .action-btn {
                    font-size: 18px;
                    cursor: pointer;
                    opacity: 0.9;
                    transition: opacity 0.2s;

                    &:hover {
                        opacity: 1;
                    }
                }
            }
        }

        .session-list {
            flex: 1;
            overflow-y: auto;
            background: #f5f7fa;
            padding: 8px;

            .session-tip {
                margin: 40px auto 0;
                text-align: center;
                font-size: 13px;
                color: #909399;
            }

            .session-item {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: 8px;
                padding: 10px 12px;
                border-radius: 8px;
                cursor: pointer;
                transition: background 0.2s;

                &:hover {
                    background: #ecf5ff;

                    .session-del {
                        color: #c0c4cc;
                    }
                }

                &.active {
                    background: #e1efff;
                }

                .session-main {
                    flex: 1;
                    min-width: 0;
                }

                .session-title {
                    font-size: 13px;
                    color: #303133;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }

                .session-meta {
                    margin-top: 2px;
                    font-size: 11px;
                    color: #909399;
                }

                .session-del {
                    flex-shrink: 0;
                    font-size: 15px;
                    color: transparent;
                    transition: color 0.2s;

                    &:hover {
                        color: #f56c6c;
                    }
                }
            }
        }

        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            background: #f5f7fa;
            display: flex;
            flex-direction: column;
            gap: 12px;

            .chat-empty {
                margin: auto;
                text-align: center;
                color: #909399;

                p {
                    margin: 8px 0 0;
                    font-size: 14px;
                    color: #606266;
                }

                .empty-tip {
                    font-size: 12px;
                    color: #a8abb2;
                }
            }

            .msg {
                display: flex;
                flex-direction: column;
                max-width: 85%;

                &.user {
                    align-self: flex-end;

                    .bubble {
                        background: #409eff;
                        color: #fff;
                        border-radius: 12px 12px 4px 12px;
                    }
                }

                &.assistant {
                    align-self: flex-start;

                    .bubble {
                        background: #fff;
                        color: #303133;
                        border-radius: 12px 12px 12px 4px;
                        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
                    }
                }

                .bubble {
                    padding: 10px 14px;
                    font-size: 14px;
                    line-height: 1.6;
                    word-break: break-word;
                    white-space: pre-wrap;
                }

                .cursor {
                    animation: blink 1s step-end infinite;
                }

                .refs {
                    margin-top: 6px;
                    display: flex;
                    flex-direction: column;
                    gap: 6px;

                    .ref-item {
                        background: #fff;
                        border-left: 3px solid #409eff;
                        border-radius: 6px;
                        padding: 6px 10px;
                        font-size: 12px;
                        color: #409eff;
                        cursor: pointer;
                        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
                        overflow: hidden;
                        text-overflow: ellipsis;
                        white-space: nowrap;

                        &:hover {
                            background: #ecf5ff;
                        }
                    }
                }
            }
        }

        .chat-input {
            display: flex;
            gap: 8px;
            padding: 12px 16px;
            border-top: 1px solid #ebeef5;
            background: #fff;
        }
    }
}

@keyframes blink {
    0%, 100% {
        opacity: 1;
    }
    50% {
        opacity: 0;
    }
}

.chat-panel-enter-active,
.chat-panel-leave-active {
    transition: all 0.25s ease;
}

.chat-panel-enter-from,
.chat-panel-leave-to {
    opacity: 0;
    transform: translateY(16px) scale(0.96);
}
</style>
