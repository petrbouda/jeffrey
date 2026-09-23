/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    // app root and have no node_modules of their own, so their bare `axios` imports must be resolved
    // against THIS app's node_modules — without the pins, any test that reaches a shared service
    // fails to resolve rather than fails an assertion.
    alias: [
      {
        find: '@instances',
        replacement: fileURLToPath(new URL('../ui-instances/src', import.meta.url))
      },
      {
        find: '@hubs',
        replacement: fileURLToPath(new URL('../ui-hubs/ui', import.meta.url))
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
      // Prefix (not exact) match: SourceViewerModal imports highlight.js subpaths.
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
    include: [
      'src/**/*.{test,spec}.ts',
      '../../shared/ui/**/*.{test,spec}.ts',
      '../ui-hubs/**/*.{test,spec}.ts',
      '../ui-instances/**/*.{test,spec}.ts'
    ],
    globals: true,
    environment: 'node'
  }
});
