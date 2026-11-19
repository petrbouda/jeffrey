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
import { createSSRApp, h } from 'vue';
import { renderToString } from 'vue/server-renderer';
import ThresholdBadge from '@/components/settings/ThresholdBadge.vue';
import type { ThresholdState } from '@/components/settings/thresholdState';

/** The badge's markup, rendered without a DOM: the app's tests run in the node environment. */
function render(state: ThresholdState): Promise<string> {
  return renderToString(createSSRApp({ render: () => h(ThresholdBadge, { state }) }));
}

describe('ThresholdBadge', () => {
  it('renders "record everything" in amber, the state that floods a recording', async () => {
    const html = await render({ label: 'every call', recordsEverything: true });

    expect(html).toContain('badge-warning');
    expect(html).not.toContain('badge-primary');
    expect(html).toContain('every call');
  });

  it('renders a real threshold in the neutral primary colour', async () => {
    const html = await render({ label: '≥ 5 ms', recordsEverything: false });

    expect(html).toContain('badge-primary');
    expect(html).not.toContain('badge-warning');
    expect(html).toContain('≥ 5 ms');
  });

  it('keeps the label in its own case, so units such as µs and KiB read correctly', async () => {
    const html = await render({ label: 'one sample per 512 KiB', recordsEverything: false });

    expect(html).toContain('badge-no-uppercase');
    expect(html).toContain('badge-s');
  });
});
