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

import IdeClient from '@/services/api/IdeClient';
import MessageBus from '@/services/MessageBus';

/**
 * Per-(profile, class) cached check of whether the connected IDE contains a class. Used to enable the
 * flamegraph IDE buttons in JFR Profiler Plugin mode (they render disabled and enable only when the
 * class is present). Results are cached so re-hovering the same frame doesn't re-query the IDE; any
 * failure resolves to false so the buttons stay disabled.
 *
 * The cache is emptied whenever the linked window changes: an answer is only true of the window that
 * gave it, so keeping it across a re-link leaves every button in the new window disabled on the
 * strength of what the old one said.
 */
const cache = new Map<string, boolean>();

MessageBus.on(MessageBus.IDE_TARGET_CHANGED, () => {
  cache.clear();
});

function key(profileId: string, fqn: string): string {
  return `${profileId}::${fqn}`;
}

async function check(profileId: string, fqn: string): Promise<boolean> {
  const cacheKey = key(profileId, fqn);
  const cached = cache.get(cacheKey);
  if (cached !== undefined) {
    return cached;
  }
  try {
    const response = await new IdeClient().hasClass(profileId, fqn);
    const found = response?.found === true;
    cache.set(cacheKey, found);
    return found;
  } catch {
    return false;
  }
}

export default {
  check
};
