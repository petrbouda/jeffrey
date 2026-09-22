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
