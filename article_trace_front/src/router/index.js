import mainPage from "@/views/user/mainPage.vue"
import loginPage from "@/views/public/login.vue"
import {createRouter, createWebHistory} from "vue-router";
import articleCategory from "@/views/article/ArticleCategory.vue"
import articleList from "@/views/article/ArticleManage.vue"
import UserLogo from "@/views/user/UserLogo.vue"
import userInfo from "@/views/user/UserInfo.vue"
import updateUserPassword from "@/views/user/UserResetPassword.vue"
import home from "@/views/user/home.vue"
import accountManage from '@/views/user/AccountManage.vue'
import avatarReview from '@/views/user/AvatarReview.vue'
import reportManage from '@/views/user/ReportManage.vue'
import reviewCenter from '@/views/user/ReviewCenter.vue'
import publicHome from "@/views/public/publicHome.vue";
import readerHome from "@/views/public/readerHome.vue";
import authorApply from "@/views/public/AuthorApply.vue";
import articleInfo from "@/views/public/articleInfo.vue";


const routes = [
    // 顶层必须写 "/"：空串只对嵌套子路由生效，写 "" 匹配不到根路径，会直接掉进兜底 error 页
    {path: "/", redirect: "/publicHome"},
    {path: "/publicHome", component: publicHome, name: "PublicHome", meta: {title: "首页"}},
    {
        path: "/article/:id", component: articleInfo, name: "ArticleInfo", props: true,
        // 标题由页面取到文章后自己写，这里只是加载期间的兜底
        meta: {noTransition: true, title: "文章"}
    },
    {
        path: "/reader/home", component: readerHome, redirect: "/reader/info", name: "ReaderHome", children: [
            {path: "/reader/info", component: userInfo, name: "ReaderInfo", meta: {title: "个人信息"}},
            {
                path: "/reader/apply",
                component: authorApply,
                name: "ReaderApply",
                meta: {noTransition: true, title: "申请成为作者"}
            },
            {
                path: "/reader/updateLogo",
                component: UserLogo,
                name: "ReaderLogo",
                meta: {noTransition: true, title: "修改头像"}
            },
            {
                path: "/reader/updatePassword",
                component: updateUserPassword,
                name: "UpdateReaderPassword",
                meta: {noTransition: true, title: "修改密码"}
            }
        ]
    },
    {path: "/login", component: loginPage, name: "Login", meta: {title: "登录"}},
    {
        path: "/mainPage", component: mainPage, redirect: "/home", name: "MainPage", children: [
            {path: "/home", component: home, name: "Home", meta: {title: "后台首页"}},
            {
                path: "/article/category",
                component: articleCategory,
                name: "ArticleCategory",
                meta: {noTransition: true, title: "分类管理"}
            },
            {
                path: "/article/list",
                component: articleList,
                name: "ArticleList",
                meta: {noTransition: true, title: "文章管理"}
            },
            {path: "/user/info", component: userInfo, name: "UserInfo", meta: {noTransition: true, title: "个人信息"}},
            {
                path: "/user/updateLogo",
                component: UserLogo,
                name: "UserLogo",
                meta: {noTransition: true, title: "修改头像"}
            },
            {
                path: "/user/updatePassword",
                component: updateUserPassword,
                name: "UpdateUserPassword",
                meta: {noTransition: true, title: "修改密码"}
            },
            {
                path: "/user/accountManage",
                component: accountManage,
                name: "AccountManage",
                meta: {noTransition: true, title: "账号管理"}
            },
            {
                path: "/avatar/review",
                component: avatarReview,
                name: "AvatarReview",
                meta: {noTransition: true, title: "头像审核"}
            },
            {
                path: "/report/manage",
                component: reportManage,
                name: "ReportManage",
                meta: {noTransition: true, title: "举报处理"}
            },
            {
                path: "/review/center",
                component: reviewCenter,
                name: "ReviewCenter",
                meta: {noTransition: true, title: "审核中心"}
            }
        ],
    },
    // 这条必须给 path：不写 path 时 vue-router 会把它当成 "/"，从而抢走根路径（表现为打开裸地址落到出错页）
    {path: "/error", component: () => import('@/views/error.vue'), name: "ErrorPage", meta: {noTransition: true, title: "出错了"}},
    {
        path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/error.vue'),
        meta: {noTransition: true, title: "出错了"}
    }
]
const router = createRouter({
    history: createWebHistory(),
    routes: routes,
})

const DEFAULT_TITLE = '文迹'

/**
 * 页面标题统一在这里落：路由给静态标题，页面自己设的（文章标题、站名、登录页的三种状态）
 * 在挂载后覆盖它——afterEach 先于组件渲染。不加站点后缀，按功能页名即可。
 */
router.afterEach((to) => {
    document.title = (to.meta && to.meta.title) || DEFAULT_TITLE
})

export default router
