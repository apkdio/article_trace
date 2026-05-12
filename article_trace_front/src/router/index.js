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
import publicHome from "@/views/public/publicHome.vue";
import readerHome from "@/views/public/readerHome.vue";
import articleInfo from "@/views/public/articleInfo.vue";


const routes = [
    {path: "", redirect: "/publicHome"},
    {path: "/publicHome", component: publicHome, name: "PublicHome"},
    {path: "/article/:id", component: articleInfo, name: "ArticleInfo", props: true, meta: {noTransition: true}},
    {
        path: "/reader/home", component: readerHome, redirect: "/reader/info", name: "ReaderHome", children: [
            {path: "/reader/info", component: userInfo, name: "ReaderInfo"},
            {path: "/reader/updateLogo", component: UserLogo, name: "ReaderLogo", meta: {noTransition: true}},
            {
                path: "/reader/updatePassword",
                component: updateUserPassword,
                name: "UpdateReaderPassword",
                meta: {noTransition: true}
            }
        ]
    },
    {path: "/login", component: loginPage, name: "Login"},
    {
        path: "/mainPage", component: mainPage, redirect: "/home", name: "MainPage", children: [
            {path: "/home", component: home, name: "Home"},
            {path: "/article/category", component: articleCategory, name: "ArticleCategory",meta:{noTransition:true}},
            {path: "/article/list", component: articleList, name: "ArticleList",meta:{noTransition:true}},
            {path: "/user/info", component: userInfo, name: "UserInfo",meta:{noTransition:true}},
            {path: "/user/updateLogo", component: UserLogo, name: "UserLogo",meta:{noTransition:true}},
            {path: "/user/updatePassword", component: updateUserPassword, name: "UpdateUserPassword",meta:{noTransition:true}},
            {path: "/user/accountManage", component: accountManage, name: "AccountManage",meta:{noTransition:true}}
        ],
    },
    {component: () => import('@/views/error.vue'), name: "ErrorPage",meta:{noTransition:true}},
    {path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/error.vue'),meta:{noTransition:true}}
]
const router = createRouter({
    history: createWebHistory(),
    routes: routes,
})
export default router