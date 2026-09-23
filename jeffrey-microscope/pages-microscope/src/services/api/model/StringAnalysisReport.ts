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

import StringDeduplicationEntry from './StringDeduplicationEntry';
import StringInstanceEntry from './StringInstanceEntry';
import StringTopEntry from './StringTopEntry';

/**
 * Represents a JVM flag related to String handling.
 */
export interface JvmStringFlag {
  name: string;
  value: string;
  type: string;
  origin: string;
  description: string;
}

/**
 * Complete report for string deduplication analysis.
 */
export default interface StringAnalysisReport {
  totalStrings: number;
  totalStringShallowSize: number;
  uniqueArrays: number;
  sharedArrays: number;
  totalSharedStrings: number;
  memorySavedByDedup: number;
  potentialSavings: number;
  topByRetained: StringTopEntry[];
  topInstancesByRetained: StringInstanceEntry[];
  alreadyDeduplicated: StringDeduplicationEntry[];
  opportunities: StringDeduplicationEntry[];
  jvmFlags: JvmStringFlag[];
}
