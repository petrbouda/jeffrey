import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [vue()],
  server: {
    fs: {
      // The shared UI specs live above the app root; without this, vitest serves them via /@fs and
      // refuses the path.
      allow: ['../..']
    }
  },
  resolve: {
    // Mirrors vite.config.ts, including the exact-match regex pins. `@shared` sources live above the
    // app root and have no node_modules of their own, so their bare `axios`/`marked`/`dompurify`
    // imports must be resolved against THIS app's node_modules — without the pins, any test that
    // reaches a shared service fails to resolve rather than fails an assertion.
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
      {
        find: /^apexcharts$/,
        replacement: fileURLToPath(new URL('./node_modules/apexcharts', import.meta.url))
      },
      {
        find: /^marked$/,
        replacement: fileURLToPath(new URL('./node_modules/marked', import.meta.url))
      },
      {
        find: /^dompurify$/,
        replacement: fileURLToPath(new URL('./node_modules/dompurify', import.meta.url))
      },
      // Prefix (not exact) match: @shared DiffViewer imports highlight.js subpaths.
      {
        find: 'highlight.js',
        replacement: fileURLToPath(new URL('./node_modules/highlight.js', import.meta.url))
      }
    ]
  },
  test: {
    // The app's own tests plus the shared UI modules' — shared/ui has no test runner of its own, so
    // its specs run here, next to the only environment configured to resolve them. The glob stays
    // module-root-agnostic because the shared modules disagree on it (common uses src/, workspaces ui/).
    include: ['src/**/*.{test,spec}.ts', '../../shared/ui/**/*.{test,spec}.ts'],
    globals: true,
    // jsdom rather than node: the markdown renderer sanitizes against a real DOM, and its tests assert
    // that a hostile attribute is stripped — not merely escaped, which is what a string check would
    // let through.
    environment: 'jsdom'
  }
});
