import react from '@vitejs/plugin-react'
import { seedDesignPlugin } from '@seed-design/vite-plugin'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), seedDesignPlugin()],
  resolve: {
    tsconfigPaths: true,
  },
  server: {
    host: true,
    port: 5174,
  },
})
