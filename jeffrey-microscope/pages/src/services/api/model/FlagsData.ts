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

import JvmFlag from './JvmFlag';

/**
 * Response data for the JVM Flags dashboard.
 */
export default interface FlagsData {
  /** Map of flags grouped by origin (Default, Ergonomic, Command line, Management) */
  flagsByOrigin: Record<string, JvmFlag[]>;
  /** Total number of flags */
  totalFlags: number;
  /** Number of flags whose values changed during the recording */
  changedFlags: number;
}
