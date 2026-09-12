# 文迹 · 前端（article_trace_front）

基于 **Vue 3 + Vite + Element Plus** 的文章平台前端，采用 Pinia 状态管理、Vue Router 路由，Axios 封装后端接口请求。

## 技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue | 3.5.x |
| 构建 | Vite | 7.3.x |
| UI 组件 | Element Plus | 2.13.x |
| 状态管理 | Pinia（+ 持久化插件） | 3.x |
| 路由 | Vue Router | 4.6.x |
| HTTP | Axios | 1.13.x |
| 富文本 | @vueup/vue-quill | 1.2.x |
| 样式 | Sass | 1.97.x |

## 目录结构

```
article_trace_front/
├── index.html
├── package.json                    # 依赖与脚本
├── vite.config.js                  # Vite 配置（代理/别名/自动导入）
└── src/
    ├── main.js                     # 应用入口（注册 Pinia / Router）
    ├── App.vue                     # 根组件
    ├── api/                        # 接口请求封装
    │   ├── article.js              #   文章相关接口
    │   ├── category.js             #   分类相关接口
    │   ├── user.js                 #   用户相关接口
    │   ├── agent.js                #   AI 问答 + 多会话管理 + 可用性探测
    │   ├── notification.js         #   站内通知（列表/未读数/已读）
    │   ├── checkPersonInfo.js      #   个人信息校验
    │   ├── confirmDeleteAccount.js #   账号注销确认
    │   └── registerSuccess.js      #   注册成功回调
    ├── assets/                     # 静态资源
    │   ├── main.scss               #   全局样式
    │   └── *.jpg / *.png           #   封面/Logo/背景图
    ├── components/                 # 公共组件
    │   ├── UserLayout.vue          #   用户中心布局（侧边栏+header+通知铃铛）
    │   ├── UserTypeTag.vue         #   角色标签（站长/作者/读者）
    │   ├── PageHeader.vue          #   页面标题栏
    │   ├── StatCard.vue            #   统计卡片
    │   ├── TopArticlesList.vue     #   热门文章榜
    │   ├── AuthorCard.vue          #   作者/站长名片
    │   ├── InfoFormShell.vue       #   用户信息/改密表单外壳
    │   └── AgentChat.vue           #   文迹 AI 聊天窗（多会话，含未启用降级）
    ├── router/
    │   └── index.js                # 路由配置（公共区/读者中心/作者后台）
    ├── stores/                     # Pinia 状态仓库
    │   ├── tokenStorage.js         #   Token 持久化
    │   ├── userInfo.js             #   用户信息
    │   └── searchConditions.js     #   搜索条件
    ├── utils/                      # 工具函数
    │   ├── request.js              #   Axios 实例 + 拦截器（401 自动登出）
    │   ├── loginCheck.js           #   登录状态校验
    │   └── timeCheck.js            #   时间格式化
    └── views/                      # 页面组件
        ├── public/                 # 公共区
        │   ├── login.vue           #   登录页
        │   ├── publicHome.vue      #   公共首页（文章列表）
        │   ├── articleInfo.vue     #   文章详情页
        │   └── readerHome.vue      #   读者个人中心布局
        ├── user/                   # 用户/后台区
        │   ├── mainPage.vue        #   作者后台主布局（侧边栏）
        │   ├── home.vue            #   后台首页（数据统计）
        │   ├── UserInfo.vue        #   个人信息
        │   ├── UserLogo.vue        #   头像修改
        │   ├── UserResetPassword.vue # 修改密码
        │   └── AccountManage.vue   #   账号管理（站长）
        ├── article/                # 文章管理区
        │   ├── ArticleCategory.vue #   分类管理
        │   └── ArticleManage.vue   #   文章管理（撰写/编辑/审核）
        └── error.vue               # 404 / 错误页
```

## 模块说明

### 路由（router/index.js）

按角色划分为三大区域：

| 路由 | 区域 | 说明 |
|---|---|---|
| `/publicHome`、`/article/:id`、`/login` | 公共区 | 无需登录访问 |
| `/reader/home` | 读者中心 | 个人信息、头像、改密 |
| `/mainPage` | 作者/站长后台 | 首页统计、文章管理、分类管理、账号管理 |

### 请求封装（utils/request.js）

- Axios 实例 `baseURL = /api`，开发环境由 Vite 代理转发到后端 `localhost:8080`。
- 请求拦截器自动携带 `Authorization` Token。
- 响应拦截器统一处理：401 时提示「登录已失效」、清除 Token 与用户信息并跳转首页。

### 状态管理（stores/）

| 仓库 | 用途 |
|---|---|
| `tokenStorage` | Token 持久化存储 |
| `userInfo` | 当前登录用户信息 |
| `searchConditions` | 跨页面搜索条件 |

### AI 问答（AgentChat.vue）

悬浮式聊天窗（挂载于 `publicHome`），支持多会话管理：

- **流式问答**：`api/agent.js` 的 `askAgentStream` 用 fetch + SSE，回调 `session` / `articles` / `delta` / `error` / `done` 事件。
- **多会话**：头部提供「新建会话」与「历史会话」入口；历史列表调 `GET /agent/sessions`，切换会话调 `GET /agent/sessions/{id}/messages` 加载记录，删除调 `DELETE /agent/sessions/{id}`。
- **会话 ID**：首次提问不带 `sessionId`，后端通过 SSE `session` 事件回传新建的会话 ID，前端保存后，后续提问带上以维持多轮上下文。
- **可用性探测与降级**：挂载时调用 `GET /agent/health` 读取后端的 `enabled` 字段（对应后端总开关 `rpc.agent.enabled`）。未启用时窗口**照常渲染**，仅内容区提示「暂未启用 AI 功能」并隐藏操作按钮与输入框；探测用原生 `fetch` 而非 axios 实例，避免未登录时的 401 触发全局登出跳转。

### 站内通知（UserLayout.vue）

通知中心挂在用户中心的 header 上，不单独占页面：

- **铃铛 + 未读角标**：进入用户中心时拉一次 `GET /notification/unreadCount`，未读为 0 时隐藏角标；
- **抽屉列表**：点击铃铛打开 `el-drawer`，调 `GET /notification/list` 分页展示；
- **已读**：点击单条调 `PATCH /notification/read/{id}`，角标即时递减；「全部已读」调 `PATCH /notification/readAll`；
- **展示规则**：`senderId === -1` 为系统通知（当前全部通知均为系统发送），本期不做业务跳转。

### 页面视图（views/）

| 页面 | 功能 |
|---|---|
| `publicHome.vue` | 文章公开列表（分类筛选 / 搜索 / 热门 Top10） |
| `articleInfo.vue` | 文章详情 + 评论 |
| `login.vue` | 登录（含记住我） |
| `mainPage.vue` | 后台侧边栏布局 |
| `home.vue` | 后台数据统计（文章总数 / 待审核 / 已发布 / 驳回） |
| `ArticleManage.vue` | 文章撰写、编辑、删除、审核 |
| `ArticleCategory.vue` | 分类增删改查 |
| `AccountManage.vue` | 账号管理（站长） |

## 启动与构建

```bash
cd article_trace_front

# 安装依赖（npm 或 bun）
npm install        # 或 bun install

# 开发模式（热更新，Vite 代理 /api → localhost:8080）
npm run dev        # 或 bun run dev

# 生产构建（产物在 dist/）
npm run build      # 或 bun run build

# 本地预览构建产物
npm run preview
```

## 部署到 Nginx

前端 `dist/` 由 Nginx 托管静态资源，`/api` 反向代理到后端 `8080` 端口。开发环境无需额外配置代理（`vite.config.js` 已内置 `/api` 代理）。
