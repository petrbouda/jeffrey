/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
 * Represents a single flag value change with timestamp.
 */
export interface FlagValueChange {
  /** The flag value at this point in time */
  value: string;
  /** When this value was recorded, as UTC epoch millis */
  timestamp: number;
}

/**
 * Represents a JVM flag with its current value and change history.
 */
export default interface JvmFlag {
  /** Flag name (e.g., "UseG1GC", "MaxHeapSize") */
  name: string;
  /** Current (latest) flag value */
  value: string;
  /** Flag type: Boolean, Int, UnsignedInt, Long, String */
  type: string;
  /** How the flag was set: Default, Ergonomic, Command line, Management */
  origin: string;
  /** Previous values if the flag changed during recording */
  previousValues: string[];
  /** Whether the flag value changed during the recording */
  hasChanged: boolean;
  /** Optional description of the flag from OpenJDK documentation */
  description?: string;
  /** Chronological list of value changes (latest first), only populated if hasChanged is true */
  changeHistory?: FlagValueChange[];
}
