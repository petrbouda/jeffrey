import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    // Array form so we can use exact-match regex aliases. `@shared` sources live above the app
    // root and have no node_modules of their own, so their bare `vue`/`axios` imports are pinned
    // to THIS app's node_modules — exact `^vue$`/`^axios$` keeps a single instance without
    // disturbing subpath imports.
    alias: [
      {
        find: '@instances',
        replacement: fileURLToPath(new URL('../../shared/ui/instances/src', import.meta.url))
      },
      {
        find: '@workspaces',
        replacement: fileURLToPath(new URL('../../shared/ui/workspaces/ui', import.meta.url))
      },
      {
        find: '@shared',
        replacement: fileURLToPath(new URL('../../shared/ui/common/src', import.meta.url))
      },
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      { find: /^vue$/, replacement: fileURLToPath(new URL('./node_modules/vue', import.meta.url)) },
      {
        find: /^axios$/,
        replacement: fileURLToPath(new URL('./node_modules/axios', import.meta.url))
      },
      // Pinned for shared chart components (e.g. @shared DonutWithLegend) that bare-import apexcharts.
      {
        find: /^apexcharts$/,
        replacement: fileURLToPath(new URL('./node_modules/apexcharts', import.meta.url))
      },
      // Pinned for @shared MarkdownRenderer, which renders model-authored markdown through both.
      {
        find: /^marked$/,
        replacement: fileURLToPath(new URL('./node_modules/marked', import.meta.url))
      },
      {
        find: /^dompurify$/,
        replacement: fileURLToPath(new URL('./node_modules/dompurify', import.meta.url))
      },
      // Prefix (not exact) match: @shared DiffViewer imports highlight.js subpaths such as
      // `highlight.js/lib/core` and `highlight.js/lib/languages/java`.
      {
        find: 'highlight.js',
        replacement: fileURLToPath(new URL('./node_modules/highlight.js', import.meta.url))
      }
    ]
  },
  server: {
    fs: {
      // Allow Vite's dev server to read the shared sources living above the app root.
      allow: ['../..']
    },
    proxy: {
      '/api': 'http://localhost:8585'
    }
  },
  build: {
    outDir: './target/dist/pages'
  }
});
