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

/**
 * A stack-based event type of the current profile that can be exported to an OpenTelemetry (OTLP) file.
 */
export default interface OtlpExportEventType {
  /** Event type code, e.g. `jdk.ExecutionSample`. */
  code: string;
  /** Human-readable label, e.g. `Samples`. */
  label: string;
  /** Total sample count. */
  samples: number;
  /** Total weight (bytes/nanoseconds) or `null` when the event carries no weight. */
  weight: number | null;
  /** True when the event has a weight dimension that can become a second OTLP value type. */
  hasWeight: boolean;
  /** The weight dimension's OTLP `type/unit` (e.g. `cpu/nanoseconds`, `alloc/bytes`), or `null` when no weight. */
  weightSampleType: string | null;
  /** Category label, e.g. `CPU`, `Allocation`, `Blocking`, `Wall`. */
  category: string;
  /** The OTLP `sample_type` this event maps to, e.g. `samples / count`. */
  sampleType: string;
}
