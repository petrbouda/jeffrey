/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import { marked } from 'marked';
import DOMPurify from 'dompurify';

/**
 * Renders model-authored markdown into HTML that is safe to hand to `v-html`.
 *
 * Marked does not sanitize, and this markdown is not merely model-authored — it quotes source files
 * the model just read from a cloned repository. A comment or string literal in any analyzed codebase
 * would otherwise be a path straight into the DOM, so every render goes through DOMPurify.
 */
export default class MarkdownRenderer {
  /**
   * Markdown to sanitized HTML. Returns an empty string for empty input so callers can bind the result
   * directly without guarding.
   */
  static render(markdown: string | null | undefined): string {
    if (!markdown) {
      return '';
    }
    const html = marked.parse(markdown, { breaks: true }) as string;
    return DOMPurify.sanitize(html);
  }
}
