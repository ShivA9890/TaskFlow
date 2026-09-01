import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Two backends, so routes are split by path. The browser only ever talks to
    // 5173, which is why no CORS config is needed on the Java side.
    // In Kubernetes an Ingress does this same job with the same paths.
    proxy: {
      // identity-service
      '/api/v1/auth': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/me': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/users': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/invites': { target: 'http://localhost:8081', changeOrigin: true },
      '/api/v1/teams': { target: 'http://localhost:8081', changeOrigin: true },
      // task-service
      '/api/v1/boards': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/v1/columns': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/v1/tasks': { target: 'http://localhost:8082', changeOrigin: true },
    },
  },
})