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

// Flat ESLint config (ESLint 9) migrated from the legacy .eslintrc.json. It reproduces the
// previous rule intent: eslint:recommended + plugin:vue/vue3-recommended +
// @typescript-eslint/@vue typescript recommended + plugin:prettier/recommended, plus the
// project-specific rule overrides at the bottom.
import js from '@eslint/js';
import globals from 'globals';
import pluginVue from 'eslint-plugin-vue';
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript';
import prettierRecommended from '@vue/eslint-config-prettier';

export default defineConfigWithVueTs(
  {
    name: 'jeffrey/files-to-ignore',
    ignores: [
      'target/**',
      'dist/**',
      'coverage/**',
      // Generated protobuf module (npm run proto:generate) — never hand-edited, not linted.
      'src/proto/**'
    ]
  },
  {
    name: 'jeffrey/files-to-lint',
    files: ['**/*.{vue,js,jsx,cjs,mjs,ts,tsx}']
  },
  js.configs.recommended,
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  prettierRecommended,
  {
    name: 'jeffrey/language-options',
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2021
      }
    }
  },
  {
    name: 'jeffrey/project-rules',
    rules: {
      'no-console': 'warn',
      'no-debugger': 'warn',
      // CLAUDE.md: every if/else/for/while body is braced, even a one-line early return. Enforced
      // rather than reviewed, because the dangling-statement form is invisible in a diff until the
      // second statement is added under it.
      curly: ['error', 'all'],
      'vue/component-name-in-template-casing': ['error', 'PascalCase'],
      'vue/no-v-html': 'off',
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-explicit-any': 'warn',
      // Underscore-prefixed identifiers are the project convention for intentionally unused
      // parameters/variables (e.g. abstract-method implementations that ignore arguments).
      // TODO: pre-existing unused variables remain in several views/components — clean them up
      // and restore this rule to 'error'.
      '@typescript-eslint/no-unused-vars': [
        'warn',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_'
        }
      ],
      // Single-extends empty interfaces are used as documented aliases (e.g. ProcessingStep).
      '@typescript-eslint/no-empty-object-type': [
        'error',
        { allowInterfaces: 'with-single-extends' }
      ],
      // TODO: pre-existing v-if/v-for combinations (FlamegraphCardGrid, ProfilePerformanceCounters)
      // need template restructuring — fix them and restore this rule to 'error'.
      'vue/no-use-v-if-with-v-for': 'warn'
    }
  }
);
