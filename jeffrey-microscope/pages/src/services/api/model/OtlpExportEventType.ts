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
