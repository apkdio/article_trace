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
