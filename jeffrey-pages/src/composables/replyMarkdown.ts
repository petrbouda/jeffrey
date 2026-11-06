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

import { Marked, type Tokens } from 'marked';

// A run's final reply, read as the Markdown the agent wrote it in. The reply is a model's text, so nothing in it
// becomes markup it did not ask Markdown for: raw HTML is shown as text, an image as its alt text, and a link only
// keeps its address when it is a web, in-page or site-relative one.

const SAFE_HREF = /^(?:https?:\/\/|#|\/(?!\/))/i;
const TABLE_OPEN = '<table>';
const TABLE_CLOSE = '</table>';
const TABLE_WRAP_OPEN = '<div class="reply-md-table">';
const TABLE_WRAP_CLOSE = '</div>';

const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (character) => ESCAPES[character]);
}

const markdown = new Marked({ gfm: true, breaks: false, async: false });

markdown.use({
  renderer: {
    html({ text }: Tokens.HTML | Tokens.Tag): string {
      return escapeHtml(text);
    },
    image({ text }: Tokens.Image): string {
      return escapeHtml(text);
    },
    link({ href, title, tokens }: Tokens.Link): string {
      const label = this.parser.parseInline(tokens);
      if (!SAFE_HREF.test(href)) {
        return label;
      }
      const titled = title ? ` title="${escapeHtml(title)}"` : '';
      return `<a href="${escapeHtml(href)}"${titled} target="_blank" rel="noopener noreferrer">${label}</a>`;
    }
  }
});

/** The reply as HTML; each table sits in its own scrolling wrapper so a wide one never widens the page. */
export function replyMarkdown(reply: string): string {
  const html = markdown.parse(reply, { async: false });
  return html.split(TABLE_OPEN).join(TABLE_WRAP_OPEN + TABLE_OPEN).split(TABLE_CLOSE).join(TABLE_CLOSE + TABLE_WRAP_CLOSE);
}
