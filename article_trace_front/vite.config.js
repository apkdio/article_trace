import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import {ElementPlusResolver} from "unplugin-vue-components/resolvers";

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // 自动导入 Element Plus 相关 API，例如 ElMessage 等
    AutoImport({
      resolvers: [ElementPlusResolver(
          { locale: 'zh-cn',}
      )],
    }),
    // 自动按需引入组件
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
    },
  // 配置代理跨域
  server:{
    proxy: {
      "/api":{
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite:(path) => path.replace(/^\/api/,'')
      }
    }
  },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              return 'vendor';
            }
          }
        }
      }
    }
})
