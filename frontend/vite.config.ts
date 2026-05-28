import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3001,
    headers: {
      'Cache-Control': 'no-cache'
    },
    hmr: {
      timeout: 30000
    },
    proxy: {
      '/api/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/api/stock': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      '/api/trade': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      '/api/analysis': {
        target: 'http://localhost:8084',
        changeOrigin: true
      },
      '/api/ai': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      '/api/recharge': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      // 新浪财经API代理
      '/sina-api': {
        target: 'https://hq.sinajs.cn',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/sina-api/, ''),
        headers: {
          'Referer': 'https://finance.sina.com.cn',
          'User-Agent': 'Mozilla/5.0'
        }
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: ''
      }
    }
  }
})
