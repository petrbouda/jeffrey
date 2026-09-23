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
      { find: '@shared', replacement: fileURLToPath(new URL('../../shared/ui/common/src', import.meta.url)) },
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      { find: /^vue$/, replacement: fileURLToPath(new URL('./node_modules/vue', import.meta.url)) },
      { find: /^axios$/, replacement: fileURLToPath(new URL('./node_modules/axios', import.meta.url)) }
    ]
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: './target/dist/pages-hub'
  }
});
