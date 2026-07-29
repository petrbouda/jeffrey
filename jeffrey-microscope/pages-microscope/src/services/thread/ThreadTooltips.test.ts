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
import ThreadTooltips from '@/services/thread/ThreadTooltips';
import type { CategoryTooltip } from '@/services/thread/ThreadTooltips';
import EventMetadata from '@/services/api/model/EventMetadata';
import ThreadField from '@/services/api/model/ThreadField';

const COLOR = 'rgb(228,33,33)';
const ONE_MILLI_NANOS = 1_000_000;

function socketRead(): EventMetadata {
  return new EventMetadata('Socket Read', 'SOCKET_READ', [
    new ThreadField('Duration', 'jdk.jfr.Timespan'),
    new ThreadField('Remote Host', 'String')
  ]);
}

function render(tooltip: CategoryTooltip): string {
  return ThreadTooltips.basic(socketRead(), COLOR, tooltip);
}

describe('ThreadTooltips', () => {
  describe('counts', () => {
    it('shows the count for the hovered window, with the window it covers', () => {
      const html = render({
        kind: 'events',
        count: 142,
        windowNanos: 316 * ONE_MILLI_NANOS,
        values: ['1658000', 'db.internal']
      });

      expect(html).toContain('142 events');
      expect(html).not.toContain('70810');
    });

    it('renders a single event without the plural', () => {
      const html = render({ kind: 'events', count: 1, windowNanos: ONE_MILLI_NANOS, values: [] });

      expect(html).toContain('1 event ');
      expect(html).not.toContain('1 events');
    });

    it('renders a missing field value as a dash, not as the word undefined', () => {
      const html = render({ kind: 'events', count: 1, windowNanos: ONE_MILLI_NANOS, values: [] });

      expect(html).not.toContain('undefined');
    });

    /**
     * The band's own total is a whole run of activity, so showing it while the lookup is in flight
     * would flash a number orders of magnitude too large and then correct itself.
     */
    it('shows a placeholder rather than a number while the lookup is pending', () => {
      const html = render({ kind: 'pending' });

      expect(html).toContain('…');
      expect(html).not.toMatch(/\d+ events?/);
    });

    it('marks a failed lookup instead of leaving it pending forever', () => {
      expect(render({ kind: 'failed' })).toContain('—');
    });
  });

  describe('windows with no starts', () => {
    it('says so when nothing starts in the window and nothing spans it', () => {
      const html = render({ kind: 'empty', windowNanos: 32 * ONE_MILLI_NANOS });

      expect(html).toContain('no events start here');
      expect(html).toContain('No event starts in this');
    });

    /**
     * The middle of a long park holds no start of its own. A coloured bar under the pointer must
     * still describe the event that is running through it.
     */
    it('falls back to the event still running through the window', () => {
      const html = render({
        kind: 'ongoing',
        startOffset: 4 * ONE_MILLI_NANOS,
        values: ['1658000', 'db.internal']
      });

      expect(html).toContain('in progress');
      expect(html).toContain('Still running — started at');
      expect(html).toContain('db.internal');
    });
  });

  describe('escaping', () => {
    it('escapes field values, which come straight out of the recording', () => {
      const html = render({
        kind: 'events',
        count: 1,
        windowNanos: ONE_MILLI_NANOS,
        values: ['1658000', '<img src=x onerror=alert(1)>']
      });

      expect(html).not.toContain('<img src=x');
      expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;');
    });

    it('escapes the category label and the field names', () => {
      const metadata = new EventMetadata('<script>Socket</script>', 'SOCKET_READ', [
        new ThreadField('<b>Duration</b>', 'String')
      ]);

      const html = ThreadTooltips.basic(metadata, COLOR, {
        kind: 'events',
        count: 1,
        windowNanos: ONE_MILLI_NANOS,
        values: ['ok']
      });

      expect(html).not.toContain('<script>');
      expect(html).not.toContain('<b>Duration</b>');
    });

    it('escapes the thread name in the header', () => {
      expect(ThreadTooltips.header('<img src=x>')).not.toContain('<img src=x>');
    });
  });
});
