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

import FormattingService from '@shared/services/FormattingService';
import type { EventFieldRow } from '@/services/api/model/trace/TraceModels';

/**
 * One row of a span's detail, ready to render.
 *
 * Values arrive as JSON text because which keys a span carries depends on the event that produced
 * it, so there is no typed shape to map onto. Turning each into its display form here keeps that
 * formatting in one testable place rather than spread across template expressions.
 */
export interface SpanDetailRow {
  /** The key as recorded — an attribute's own name, or an event field's name. */
  key: string;
  /**
   * What to show as the row's heading. For an event field this is the label JFR recorded ("Affected/
   * Returned Rows"); for an attribute it is the key itself, since an attribute map has no schema.
   */
  label: string;
  /** The field's recorded description, shown on hover. Attributes never have one. */
  description: string | null;
  value: string;
  /**
   * The span recorded nothing here. Rendered muted, because a key whose value is missing says
   * something different from one whose value happens to read like data.
   */
  absent: boolean;
}

/**
 * A span's detail, split into the parts the panel treats differently.
 *
 * {@link attributes} and {@link eventFields} are deliberately separate. They are different kinds of
 * thing: an attribute map is whatever the developer passed to `Tracer`, with keys of their choosing,
 * while event fields are the declared, labelled schema of the event that produced the span. Mixing
 * them makes `sql` look like something someone attached by hand.
 */
export interface SpanDetail {
  attributes: SpanDetailRow[];
  eventFields: SpanDetailRow[];
  /** The statement a JDBC span ran, drawn as code rather than squeezed into a table cell. */
  sql: string | null;
}

/**
 * The one field name given special treatment, keyed on the name rather than on the event type: all
 * six JDBC events carry it, and so would any future event that runs a statement.
 */
const SQL_FIELD = 'sql';

/** What an absent value reads as — "never recorded" and "recorded blank" are not the same fact. */
const NOT_SET = 'not set';
const EMPTY = 'empty';
const NONE = 'none';
const NOT_REPORTED = 'not reported';

/**
 * JFR's content-type annotations, which the recording stores per field. Formatting from these means
 * a byte count renders as a byte count for every event type at once — including ones instrumented
 * after this was written — rather than from a list of field names kept in step by hand.
 */
const DATA_AMOUNT = 'jdk.jfr.DataAmount';
const TIMESPAN = 'jdk.jfr.Timespan';
const TIMESTAMP = 'jdk.jfr.Timestamp';
const PERCENTAGE = 'jdk.jfr.Percentage';

/** A JSON object serialised to text with nothing in it, which reads better as a word. */
const EMPTY_JSON = '{}';

const PERCENT_SCALE = 100;

const EMPTY_DETAIL: SpanDetail = { attributes: [], eventFields: [], sql: null };

/**
 * Parses the two JSON objects the backend sends into rows the panel can draw.
 *
 * Key order is the recording's own field order, which is deliberate — an exchange records method,
 * then URI, then status, and reading them in that order is how the request is reconstructed. So the
 * keys are never sorted.
 *
 * Text that does not parse is surfaced as a single row rather than dropped: a span whose detail the
 * UI cannot read is a bug worth seeing, and showing the raw string is what makes it visible.
 */
export function spanDetail(
  attributesJson: string | null | undefined,
  eventFieldsJson: string | null | undefined,
  fields: EventFieldRow[] = []
): SpanDetail {
  if (!attributesJson && !eventFieldsJson) {
    return EMPTY_DETAIL;
  }

  const byName = new Map(fields.map((field) => [field.field, field]));
  const eventEntries = entriesOf(eventFieldsJson);
  const sql = eventEntries.find(([key]) => key === SQL_FIELD)?.[1];

  return {
    attributes: entriesOf(attributesJson).map(([key, value]) => attributeRow(key, value)),
    eventFields: eventEntries
      .filter(([key]) => key !== SQL_FIELD)
      .map(([key, value]) => eventFieldRow(key, value, byName.get(key))),
    sql: typeof sql === 'string' && sql.length > 0 ? sql : null
  };
}

/**
 * The object's own entries, or a single `raw` entry when the text is not a JSON object — including
 * when it is valid JSON of some other shape, which is equally unrenderable as key/value rows.
 */
function entriesOf(json: string | null | undefined): [string, unknown][] {
  if (!json) {
    return [];
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    return [['raw', json]];
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return [['raw', json]];
  }
  return Object.entries(parsed as Record<string, unknown>);
}

/** An attribute has no schema behind it, so its key is its own label and it is never reformatted. */
function attributeRow(key: string, value: unknown): SpanDetailRow {
  const rendered = plainValue(value);
  return { key, label: key, description: null, ...rendered };
}

/**
 * An event field is formatted by the content type the recording gave it, and headed by its recorded
 * label. A field the metadata does not describe still renders — a recording can be older than the
 * event class it came from — it just falls back to its own name and no formatting.
 */
function eventFieldRow(key: string, value: unknown, field: EventFieldRow | undefined): SpanDetailRow {
  return {
    key,
    label: field?.label ?? key,
    description: field?.description ?? null,
    ...typedValue(value, field?.contentType ?? null)
  };
}

function typedValue(value: unknown, contentType: string | null): { value: string; absent: boolean } {
  if (typeof value === 'number' && contentType !== null) {
    // A negative measurement was not taken. JFR's own convention, and the reason an HTTP exchange
    // that never read a body reports -1 rather than 0 -- which would claim an empty body.
    if (value < 0) {
      return { value: NOT_REPORTED, absent: true };
    }
    return { value: formatByContentType(value, contentType), absent: false };
  }
  return plainValue(value);
}

function formatByContentType(value: number, contentType: string): string {
  switch (contentType) {
    case DATA_AMOUNT:
      return FormattingService.formatBytes(value);
    case TIMESPAN:
      return FormattingService.formatDuration2Units(value);
    case TIMESTAMP:
      return FormattingService.formatTimestamp(value);
    case PERCENTAGE:
      return (value * PERCENT_SCALE).toFixed(1) + '%';
    default:
      return String(value);
  }
}

function plainValue(value: unknown): { value: string; absent: boolean } {
  if (value === null || value === undefined) {
    return { value: NOT_SET, absent: true };
  }
  if (typeof value === 'string') {
    if (value.length === 0) {
      return { value: EMPTY, absent: true };
    }
    // Several fields carry a serialised map that is usually empty -- an exchange with no query
    // parameters, a statement with no bound values. "{}" is not something to read.
    if (value === EMPTY_JSON) {
      return { value: NONE, absent: true };
    }
    return { value, absent: false };
  }
  if (typeof value === 'object') {
    return { value: JSON.stringify(value), absent: false };
  }
  return { value: String(value), absent: false };
}
