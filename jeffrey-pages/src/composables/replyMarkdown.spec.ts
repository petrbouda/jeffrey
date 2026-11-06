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

import { describe, expect, it } from 'vitest';
import { replyMarkdown } from './replyMarkdown';
import { usePluginUsage } from './usePluginUsage';

function rendered(markdown: string): HTMLElement {
  const holder = document.createElement('div');
  holder.innerHTML = replyMarkdown(markdown);
  return holder;
}

describe('replyMarkdown', () => {
  it('renders the Markdown a reply is written in', () => {
    const html = rendered('In **persons dom**, `PersonRepository#count` leads.\n\n- one\n- two');
    expect(html.querySelector('strong')?.textContent).toBe('persons dom');
    expect(html.querySelector('code')?.textContent).toBe('PersonRepository#count');
    expect(html.querySelectorAll('li')).toHaveLength(2);
  });

  it('puts each table in its own scrolling wrapper', () => {
    const html = rendered('| Method | CPU share |\n|---|---|\n| `ServerApplication#<clinit>` | 0.6% |\n');
    const table = html.querySelector('.reply-md-table > table');
    expect(table).not.toBeNull();
    expect(table?.querySelector('th')?.textContent).toBe('Method');
    expect(table?.querySelector('td code')?.textContent).toBe('ServerApplication#<clinit>');
  });

  it('shows the raw HTML a reply holds as text, never as markup', () => {
    const html = rendered('Before <script>alert(1)</script> and <img src=x onerror="alert(2)"> after.\n\n<div>block</div>');
    expect(html.querySelector('script, img, div')).toBeNull();
    expect(html.textContent).toContain('<script>alert(1)</script>');
    expect(html.textContent).toContain('<div>block</div>');
  });

  it('opens web links in a new tab and drops any other address', () => {
    const html = rendered(
      '[graph ↗](http://localhost:8585/profiles/a/flamegraph-view?eventType=jdk.ExecutionSample&graphMode=PRIMARY) ' +
        '[bad](javascript:alert(1)) [data](data:text/html,x) [page](#top) ![chart](https://example.com/x.png)'
    );
    const links = [...html.querySelectorAll('a')];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      'http://localhost:8585/profiles/a/flamegraph-view?eventType=jdk.ExecutionSample&graphMode=PRIMARY',
      '#top'
    ]);
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
    expect(html.textContent).toContain('bad');
    expect(html.querySelector('img')).toBeNull();
    expect(html.textContent).toContain('chart');
  });

  it('renders every recorded reply on the page without letting markup through', () => {
    const replies = usePluginUsage()
      .data.replays.flatMap((replay) => replay.runs)
      .map((run) => run.reply)
      .filter((reply): reply is string => typeof reply === 'string');
    expect(replies.length).toBeGreaterThan(0);
    for (const reply of replies) {
      const html = rendered(reply);
      expect(html.querySelector('script, img, iframe, style, [onerror], [onclick]')).toBeNull();
      for (const link of html.querySelectorAll('a')) {
        expect(link.getAttribute('href')).toMatch(/^(?:https?:\/\/|#|\/)/);
      }
    }
  });
});
