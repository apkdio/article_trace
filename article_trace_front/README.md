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
├── public/                  # 原样拷贝到 dist 根（含邮件 logo：/logo2.png）
├── Dockerfile               # 构建产物 → Nginx 托管
├── nginx.conf               # Nginx 配置
├── index.html
├── package.json                    # 依赖与脚本
├── vite.config.js                  # Vite 配置（代理/别名/自动导入）
└── src/
    ├── main.js                     # 应用入口（注册 Pinia / Router）
    ├── App.vue                     # 根组件
    ├── api/                        # 接口请求封装
    │   ├── article.js              #   文章相关接口
    │   ├── category.js             #   分类相关接口
    │   ├── user.js                 #   用户相关接口（含待审头像的提交）
    │   ├── agent.js                #   AI 问答 + 多会话管理 + 可用性探测
    │   ├── notification.js         #   站内通知（列表可按 system/apply/avatar 筛选、未读数、已读、删除）
    │   ├── apply.js                #   作者申请（提交/查询/审批）
    │   ├── avatar.js               #   头像审核（查自己的待审状态 + 站长侧列表/审批）
    │   ├── checkPersonInfo.js      #   个人信息校验
    │   ├── confirmDeleteAccount.js #   账号注销确认
    ├── assets/                     # 静态资源
    │   ├── main.scss               #   全局样式
    │   └── *.jpg / *.png           #   封面/Logo/背景图
    ├── components/                 # 公共组件
    │   ├── UserLayout.vue          #   用户中心布局（侧边栏 + header）
    │   ├── UserTypeTag.vue         #   角色标签（站长/作者/读者）
    │   ├── PageHeader.vue          #   页面标题栏
    │   ├── StatCard.vue            #   统计卡片
    │   ├── TopArticlesList.vue     #   热门文章榜
    │   ├── AuthorCard.vue          #   作者/站长名片
    │   ├── InfoFormShell.vue       #   用户信息/改密表单外壳
    │   ├── NotificationBell.vue    #   站内通知铃铛 + 抽屉（后台与门户页共用）
    │   └── AgentChat.vue           #   文迹 AI 聊天窗（多会话，含未启用降级）
    ├── router/
    │   └── index.js                # 路由配置（公共区/读者中心/作者后台）
    ├── stores/                     # Pinia 状态仓库
    │   ├── userInfo.js             #   用户信息（令牌不存这里）
    │   └── searchConditions.js     #   搜索条件
    ├── utils/                      # 工具函数
    │   ├── request.js              #   Axios 实例 + 拦截器（401 自动登出）
    │   ├── session.js              #   401 去重标记（模块级，刷新即重置）
    │   ├── loginCheck.js           #   登录状态校验
    │   ├── timeCheck.js            #   时间格式化
    │   ├── auth.js                 #   登录态判断（配合 UserLayout）
    │   ├── confirm.js              #   通用确认弹窗
    │   ├── date.js                 #   日期处理
    │   ├── upload.js               #   上传辅助
    │   └── validators.js           #   表单校验器（如两次密码一致）
    └── views/                      # 页面组件
        ├── public/                 # 公共区
        │   ├── login.vue           #   登录 / 注册 / 找回密码（统一入口）
        │   ├── publicHome.vue      #   公共首页（文章列表）
        │   ├── articleInfo.vue     #   文章详情页
        │   ├── readerHome.vue      #   读者个人中心布局
        │   └── AuthorApply.vue     #   申请成为作者（提交 + 查看审核状态）
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
| `/reader/home`、`/reader/apply` | 读者中心 | 个人信息、头像、改密；申请成为作者 |
| `/mainPage` | 作者/站长后台 | 首页统计、文章管理、分类管理、账号管理 |

### 请求封装（utils/request.js）

- Axios 实例 `baseURL = /api`（`withCredentials: true`），开发环境由 Vite 代理转发到后端 `localhost:8080`。
- **没有请求拦截器**：令牌放在 HttpOnly Cookie 里，由浏览器自动携带，前端拿不到也不该拿。以前往 `Authorization` 头里塞 Token，等于把凭证暴露给同源 JS，XSS 一打就丢。
- 响应拦截器统一处理：401 时提示「登录已失效」、清除用户信息并跳转首页。去重标记在 `utils/session.js`——并发请求会同时拿到 401，不拦一下会弹一串提示、跳转多次；它刻意用模块级变量而非 pinia persist，因为必须随页面刷新重置。

### 登录态判断

前端**没有令牌可看**，只能问后端：`/user/loginCheck` 返回用户信息即已登录，401 即失效。
`localStorage.userInfo` 里的用户名只用于快速渲染、避免刷新时闪一下未登录态，**不作鉴权依据**。

### 状态管理（stores/）

| 仓库 | 用途 |
|---|---|
| `userInfo` | 当前登录用户信息（**仅展示字段，不含凭证**）|
| `searchConditions` | 跨页面搜索条件 |

### AI 问答（AgentChat.vue）

悬浮式聊天窗（挂载于 `publicHome`），支持多会话管理：

- **流式问答**：`api/agent.js` 的 `askAgentStream` 用 fetch + SSE，回调 `session` / `articles` / `delta` / `error` / `done` 事件。
- **多会话**：头部提供「新建会话」与「历史会话」入口；历史列表调 `GET /agent/sessions`，切换会话调 `GET /agent/sessions/{id}/messages` 加载记录，删除调 `DELETE /agent/sessions/{id}`。
- **会话 ID**：首次提问不带 `sessionId`，后端通过 SSE `session` 事件回传新建的会话 ID，前端保存后，后续提问带上以维持多轮上下文。
- **可用性探测与降级**：挂载时调用 `GET /agent/health` 读取后端的 `enabled` 字段（对应后端总开关 `rpc.agent.enabled`）。未启用时窗口**照常渲染**，仅内容区提示「暂未启用 AI 功能」并隐藏操作按钮与输入框；探测用原生 `fetch` 而非 axios 实例，避免未登录时的 401 触发全局登出跳转。

### 站内通知（NotificationBell.vue）

站内信在三处出现：后台布局 `UserLayout`（被 `readerHome` 与 `mainPage` 复用），以及门户页 `publicHome`、`articleInfo`。所以收在一个组件里。
复制两份的话，轮询逻辑一旦不一致就会出现「一边响一边不响」。

- **铃铛 + 未读角标**：挂载时拉一次 `GET /notification/unreadCount`，未读为 0 时隐藏角标；
- **实时提醒**：每 60 秒轮询一次未读数，出现新增时轻提示（首次加载不提示，避免刚进页面就弹）；
- **抽屉列表**：点击铃铛打开 `el-drawer`，调 `GET /notification/list` 分页展示，可按类型筛选（全部 / 系统 / 作者申请 / 头像审核）；
- **已读**：点击单条调 `PATCH /notification/read/{id}`，角标即时递减；「全部已读」调 `PATCH /notification/readAll`；
- **删除**：单条调 `DELETE /notification/{id}`，删掉当前页最后一条时自动回退一页；
- **展示规则**：`senderId === -1` 为系统通知，本期不做业务跳转。

**外观由使用方决定**：组件通过作用域插槽暴露 `open` / `unread`——不传插槽时用默认图标样式（后台布局），
传插槽可换成其他控件（门户页用圆形按钮，与旁边的搜索/重置保持一致）。

> **抽屉样式必须放在非 scoped 的 `<style>` 块里**：`el-drawer` 的内容会 teleport 到 `body`，
> scoped 选择器命中不到。文件里因此有两个 style 块——铃铛用 scoped，抽屉用独立 class 限定。

### 页面视图（views/）

| 页面 | 功能 |
|---|---|
| `publicHome.vue` | 文章公开列表（分类筛选 / 搜索 / 热门 Top10 / 站内通知铃铛，未登录不显示）|
| `articleInfo.vue` | 文章详情 + 评论 + 站内通知铃铛 |
| `login.vue` | 登录 / 注册 / 找回密码（注册与找回密码的图形码走弹窗，登录的图形码内联）|
| `readerHome.vue` | 读者中心布局（复用 `UserLayout`，含站内通知）|
| `mainPage.vue` | 后台侧边栏布局（复用 `UserLayout`，含站内通知）|
| `UserInfo.vue` | 用户信息查看与修改 |
| `UserLogo.vue` | 头像提交与重置（读者端与后台端共用）。待审期间头像框打上「审核中」角标、上传与重置按钮锁住，并展示待审或上次被拒的理由。**站长自己的头像免审核**，提交即生效，提示文案也按角色区分（提交后仍会重新拉一次当前头像）|
| `UserResetPassword.vue` | 修改密码 |
| `AvatarReview.vue` | 头像审核（站长）：缩略图点开看原图、通过/拒绝，拒绝弹窗收理由 |
| `home.vue` | 后台数据统计（文章总数 / 待审核 / 已发布 / 驳回） |
| `ArticleManage.vue` | 文章撰写、编辑、删除、审核。界面只有「存为草稿 / 立即发布」两个按钮，因此**已发布文章的任何编辑都会走「重新送审」（`1→2`）**，不存在「改了但不送审」的路径。命中违禁词的稿件在状态列显示红色「命中违禁词」标记、预览抽屉顶部列出命中的词；**站长保存命中稿件后会弹窗告知已转入待审**（作者侧不提示，避免拿词表试探）。标题规则允许中间空格、禁止首尾空格 |
| `ArticleCategory.vue` | 分类增删改查 |
| `AuthorApply.vue` | 申请成为作者（提交申请 + 查看审核状态）|
| `AccountManage.vue` | 账号管理（站长） |

## 页面布局与滚动

页面级滚动容器是 `components/UserLayout.vue` 里的 `.router-content`（`flex: 1; overflow: auto`）。

**页面自身不要再写 `height: 100% + overflow: hidden`** —— `height:100%` 会让页面恰好撑满容器（永不「超出」→ 外层不产生滚动），`overflow:hidden` 又把超出部分裁掉，两者一夹就是「内容超出窗口却滚不动」。需要占满时用 `min-height: 100%`。

`assets/main.scss` 里的 `width: 100% !important` 是**为抵消 Element Plus 给 body 加的滚动条补偿宽度**（`calc(100% - 15px)`）——它与常驻滚动条叠加会多减 15px 造成横向抖动。这条不能删。

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

容器化部署时这份配置由 `nginx.conf` 提供（见 `Dockerfile`）。它只监听容器内的 80、**不处理 TLS**——
公网入口的证书终止与域名分流由宿主机上的另一层 Nginx 负责，完整步骤见根 [README](../README.md)
的「Docker 部署」一节。

> 容器内这层的 SSE 关缓冲（`proxy_buffering off`）**不能省**。它逐跳生效，宿主机那层也要各自配一份，
> 否则 AI 问答的打字机效果会在其中一跳被攒成一批返回。
