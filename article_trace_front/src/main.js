// 先引入 Element 的暗色变量，再由 main.scss 的 html.dark 覆盖主色；反过来会把金箔色压回默认蓝
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/main.scss'
import './assets/quill-content.scss'
import 'katex/dist/katex.min.css'
import {createApp} from 'vue'
import router from '@/router/index.js'
import App from './App.vue'
import {createPinia} from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const app = createApp(App)
const pinia = createPinia()

pinia.use(piniaPluginPersistedstate)
app.use(pinia)
app.use(router)
app.mount("#app")
