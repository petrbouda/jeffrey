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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import ReplayReply from './ReplayReply.vue';
import { REPLY_FORMAT_KEY, resetReplyFormat } from '@/composables/useReplyFormat';

const REPLY = 'Most CPU goes to **one query**.\n\n| Method | CPU share |\n|---|---|\n| `PersonRepository#count` | 14.05% |\n';

function show(replyChars: number | null = null) {
  return render(ReplayReply, { props: { reply: REPLY, replyChars } });
}

beforeEach(() => {
  window.localStorage.clear();
  resetReplyFormat();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('ReplayReply', () => {
  it('opens on the text the agent wrote', () => {
    const { container } = show();
    expect(screen.getByRole('tab', { name: 'Text' }).getAttribute('aria-selected')).toBe('true');
    expect(container.querySelector('.reply-text')?.textContent).toBe(REPLY);
    expect(container.querySelector('table')).toBeNull();
  });

  it('renders the reply as Markdown on its tab, and remembers the choice', async () => {
    const user = userEvent.setup();
    const { container } = show();
    await user.click(screen.getByRole('tab', { name: 'Markdown' }));
    expect(screen.getByRole('tab', { name: 'Markdown' }).getAttribute('aria-selected')).toBe('true');
    expect(container.querySelector('.reply-md strong')?.textContent).toBe('one query');
    expect(container.querySelector('.reply-md td code')?.textContent).toBe('PersonRepository#count');
    expect(window.localStorage.getItem(REPLY_FORMAT_KEY)).toBe('markdown');

    cleanup();
    resetReplyFormat();
    const again = show();
    expect(again.container.querySelector('.reply-md table')).not.toBeNull();
  });

  it('moves between the tabs with the arrow keys', async () => {
    const user = userEvent.setup();
    show();
    screen.getByRole('tab', { name: 'Text' }).focus();
    await user.keyboard('{ArrowRight}');
    const markdown = screen.getByRole('tab', { name: 'Markdown' });
    expect(markdown.getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(markdown);
    await user.keyboard('{ArrowLeft}');
    expect(screen.getByRole('tab', { name: 'Text' }).getAttribute('aria-selected')).toBe('true');
  });

  it('says how much of the reply it keeps', () => {
    show(9_812);
    expect(screen.getByText(`final message · the first ${REPLY.length} of 9,812 characters`)).toBeTruthy();
    cleanup();
    show(REPLY.length);
    expect(screen.getByText(`final message · ${REPLY.length} characters`)).toBeTruthy();
  });

  it('starts on text when the browser refuses storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    show();
    expect(screen.getByRole('tab', { name: 'Text' }).getAttribute('aria-selected')).toBe('true');
  });
});
