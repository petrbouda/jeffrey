/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import pluginVue from "eslint-plugin-vue";
import vueTsEslintConfig from "@vue/eslint-config-typescript";

export default [
    ...pluginVue.configs["flat/essential"],
    {
        files: ['**/*.ts', '**/*.tsx', '**/*.mts', '**/*.vue'],
        ignorePatterns: ['**/public/**', '**/dist/**'],
        paths: { "@/*":["./src/*"] },
        rules: {
            // Turn on other rules that you need.
            '@typescript-eslint/require-array-sort-compare': 'error'
        }
    },
    ...vueTsEslintConfig({ extends: ['recommendedTypeChecked'] }),
    {
        files: ['**/*.ts', '**/*.tsx', '**/*.mts', '**/*.vue'],
        ignorePatterns: ['**/public/**', '**/dist/**'],
        paths: { "@/*":["./src/*"] },
        rules: {
            // Turn off the recommended rules that you don't need.
            '@typescript-eslint/no-redundant-type-constituents': 'off',
        }
    }
]
