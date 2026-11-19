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

enum AxisFormatType {
  NUMBER = 'NUMBER',
  BYTES = 'BYTES',
  DURATION_IN_NANOS = 'DURATION_IN_NANOS',
  DURATION_IN_MILLIS = 'DURATION_IN_MILLIS',
  /** Values transported as percent × 100 (basis points), e.g. 442 renders as 4.4% */
  PERCENT_IN_HUNDREDTHS = 'PERCENT_IN_HUNDREDTHS'
}

export default AxisFormatType;
