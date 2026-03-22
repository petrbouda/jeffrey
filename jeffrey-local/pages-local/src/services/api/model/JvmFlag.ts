/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
 * Represents a single flag value change with timestamp.
 */
export interface FlagValueChange {
  /** The flag value at this point in time */
  value: string;
  /** When this value was recorded (ISO format) */
  timestamp: string;
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
