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
import { attributeRows, spanDetail } from '@/services/trace/spanAttributes';
import type { EventFieldRow } from '@/services/api/model/trace/TraceModels';

function field(
  name: string,
  label: string,
  contentType: string | null = null,
  description: string | null = null
): EventFieldRow {
  return { field: name, label, description, contentType };
}

describe('spanDetail', () => {
  it('has nothing to show for a span that recorded neither', () => {
    expect(spanDetail(null, null)).toEqual({ attributes: [], eventFields: [], sql: null });
  });

  it('keeps attributes and event fields apart', () => {
    // The whole point of the split: an attribute map is the developer's, event fields are the
    // event type's schema. Merging them makes `sql` look like something attached by hand.
    const detail = spanDetail('{"scopeId":"abc"}', '{"rows":6}');

    expect(detail.attributes.map((row) => row.key)).toEqual(['scopeId']);
    expect(detail.eventFields.map((row) => row.key)).toEqual(['rows']);
  });

  it('keeps the recording\'s own field order rather than sorting the keys', () => {
    // An exchange records method, then URI, then status code; reading them in that order is how
    // the request is reconstructed. Alphabetising would put the status first and the method last.
    const detail = spanDetail(null, '{"method":"GET","uri":"/api/config","statusCode":200}');

    expect(detail.eventFields.map((row) => row.key)).toEqual(['method', 'uri', 'statusCode']);
  });

  it('pulls the statement out so it can be drawn as code', () => {
    const detail = spanDetail(null, '{"sql":"SELECT 1\\nFROM spans","rows":6}');

    expect(detail.sql).toBe('SELECT 1\nFROM spans');
    expect(detail.eventFields.map((row) => row.key)).toEqual(['rows']);
  });

  it('ignores an empty statement rather than drawing an empty code block', () => {
    expect(spanDetail(null, '{"sql":"","rows":0}').sql).toBeNull();
  });

  describe('labels', () => {
    it('heads an event field with the label the recording gave it', () => {
      const detail = spanDetail(null, '{"rows":42}', [
        field('rows', 'Affected/Returned Rows', null, 'The number of affected/returned rows')
      ]);

      expect(detail.eventFields[0].label).toBe('Affected/Returned Rows');
      expect(detail.eventFields[0].key).toBe('rows');
      expect(detail.eventFields[0].description).toBe('The number of affected/returned rows');
    });

    it('falls back to the field name when the recording described no such field', () => {
      // A recording can predate the event class it came from; the field must still render.
      const detail = spanDetail(null, '{"newField":1}', [field('rows', 'Affected/Returned Rows')]);

      expect(detail.eventFields[0].label).toBe('newField');
      expect(detail.eventFields[0].description).toBeNull();
    });

    it('never relabels an attribute, because the map has no schema behind it', () => {
      const detail = spanDetail('{"rows":42}', null, [field('rows', 'Affected/Returned Rows')]);

      expect(detail.attributes[0].label).toBe('rows');
      expect(detail.attributes[0].description).toBeNull();
    });
  });

  describe('formatting by content type', () => {
    it('renders a byte count as bytes', () => {
      const detail = spanDetail(null, '{"responseLength":2048}', [
        field('responseLength', 'Response Body Length', 'jdk.jfr.DataAmount')
      ]);

      expect(detail.eventFields[0].value).toBe('2.00 KiB');
    });

    it('renders a timespan as a duration', () => {
      const detail = spanDetail(null, '{"waited":1500000}', [
        field('waited', 'Waited', 'jdk.jfr.Timespan')
      ]);

      expect(detail.eventFields[0].value).toBe('1ms 500us');
    });

    it('treats a negative measurement as one that was never taken', () => {
      // Every HTTP exchange in a recording reports requestLength -1 when no body was read. As a
      // byte count that would read "-1 B", which claims a measurement that does not exist.
      const detail = spanDetail(null, '{"requestLength":-1}', [
        field('requestLength', 'Request Body Length', 'jdk.jfr.DataAmount')
      ]);

      expect(detail.eventFields[0]).toMatchObject({ value: 'not reported', absent: true });
    });

    it('leaves a number alone when the recording gave it no content type', () => {
      const detail = spanDetail(null, '{"statusCode":200}', [field('statusCode', 'Response Status')]);

      expect(detail.eventFields[0].value).toBe('200');
    });

    it('leaves a negative number alone when it is not a measurement', () => {
      // Without a content type there is no claim that the number is a quantity of anything, so -1
      // may well be the value itself.
      const detail = spanDetail(null, '{"offset":-1}');

      expect(detail.eventFields[0]).toMatchObject({ value: '-1', absent: false });
    });
  });

  describe('absent values', () => {
    it('distinguishes never recorded from recorded blank', () => {
      const detail = spanDetail('{"mediaType":null,"scopeId":""}', null);

      expect(detail.attributes).toEqual([
        { key: 'mediaType', label: 'mediaType', description: null, value: 'not set', absent: true },
        { key: 'scopeId', label: 'scopeId', description: null, value: 'empty', absent: true }
      ]);
    });

    it('reads an empty serialised map as a word rather than as braces', () => {
      const detail = spanDetail(null, '{"queryParams":"{}"}');

      expect(detail.eventFields[0]).toMatchObject({ value: 'none', absent: true });
    });

    it('renders zero and false as themselves', () => {
      // A zero row count is one of the most interesting values a JDBC span can carry, and a false
      // flag is not a missing one; treating either as falsy blanks out the spans worth looking at.
      const detail = spanDetail(null, '{"rows":0,"cached":false}');

      expect(detail.eventFields.map((row) => row.value)).toEqual(['0', 'false']);
      expect(detail.eventFields.every((row) => !row.absent)).toBe(true);
    });
  });

  describe('malformed payloads', () => {
    it('surfaces text it cannot parse instead of dropping it', () => {
      const detail = spanDetail('not json at all', null);

      expect(detail.attributes).toEqual([
        { key: 'raw', label: 'raw', description: null, value: 'not json at all', absent: false }
      ]);
    });

    it('surfaces JSON that is not an object the same way', () => {
      expect(spanDetail(null, '[1,2,3]').eventFields[0].value).toBe('[1,2,3]');
    });

    it('renders a nested object as its JSON rather than as [object Object]', () => {
      const detail = spanDetail(null, '{"pathParams":{"recordingId":"019ffc3c"}}');

      expect(detail.eventFields[0].value).toBe('{"recordingId":"019ffc3c"}');
    });
  });
  describe('attributeRows', () => {
    it('reads a bare map, keeping the order the emitter chose', () => {
      const rows = attributeRows('{"upstream":"acme-pay","failures":5,"open":true}');

      expect(rows.map((row) => row.key)).toEqual(['upstream', 'failures', 'open']);
      expect(rows.map((row) => row.value)).toEqual(['acme-pay', '5', 'true']);
    });

    it('reads nothing from a carrier that attached nothing', () => {
      expect(attributeRows(null)).toEqual([]);
      expect(attributeRows(undefined)).toEqual([]);
      expect(attributeRows('{}')).toEqual([]);
    });

    it('surfaces text it cannot parse, the same way a span\'s attributes do', () => {
      expect(attributeRows('not json at all')).toEqual([
        { key: 'raw', label: 'raw', description: null, value: 'not json at all', absent: false }
      ]);
    });
  });
});
