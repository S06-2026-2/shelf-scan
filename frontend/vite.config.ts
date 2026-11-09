import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // O database-service ainda não libera CORS. No desenvolvimento o navegador chama o próprio Vite em
    // /api e ele repassa a http://localhost:8321 (use VITE_DATABASE_API_URL=/api/v1).
    proxy: { '/api': 'http://localhost:8321' },
  },
})
