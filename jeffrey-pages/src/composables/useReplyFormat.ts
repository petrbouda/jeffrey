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

import { ref, type Ref } from 'vue';

// How a run's reply is shown: as the text the agent wrote, or rendered as Markdown. Text unless this browser chose
// Markdown before, so the next case opens in the view last picked. Storage may be missing or refuse; the page
// then simply starts on text.

export const REPLY_FORMATS = ['text', 'markdown'] as const;
export type ReplyFormat = (typeof REPLY_FORMATS)[number];

export const REPLY_FORMAT_KEY = 'jeffrey.agent-plugin.reply-format';
const DEFAULT_FORMAT: ReplyFormat = 'text';

function stored(): ReplyFormat {
  try {
    const value = window.localStorage.getItem(REPLY_FORMAT_KEY);
    return REPLY_FORMATS.find((format) => format === value) ?? DEFAULT_FORMAT;
  } catch {
    return DEFAULT_FORMAT;
  }
}

let shared: Ref<ReplyFormat> | null = null;

export function useReplyFormat(): { format: Ref<ReplyFormat>; choose: (format: ReplyFormat) => void } {
  if (shared === null) {
    shared = ref(stored());
  }
  const format = shared;
  function choose(next: ReplyFormat): void {
    format.value = next;
    try {
      window.localStorage.setItem(REPLY_FORMAT_KEY, next);
    } catch {
      // Not remembered in this browser; the choice still holds on this page.
    }
  }
  return { format, choose };
}

/** For tests: forget the choice held in memory, so the next use reads storage again. */
export function resetReplyFormat(): void {
  shared = null;
}
