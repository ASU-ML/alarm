import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    https: false,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:8443',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
