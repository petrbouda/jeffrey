import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared': fileURLToPath(new URL('../../shared/ui/common/src', import.meta.url)),
      '@workspaces': fileURLToPath(new URL('../../shared/ui/workspaces/ui', import.meta.url)),
      '@instances': fileURLToPath(new URL('../../shared/ui/instances/src', import.meta.url))
    }
  },
  test: {
    globals: true
  }
});
