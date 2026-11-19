/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import EventTypes from '@/services/EventTypes';
import AxisFormatType from '@/services/timeseries/AxisFormatType.ts';

export default class TimeseriesEventAxeFormatter {
  static resolveAxisFormatter(useWeight: boolean, eventTypeCode: string): AxisFormatType {
    if (!useWeight) {
      return AxisFormatType.NUMBER;
    }

    if (
      EventTypes.isBlockingEventType(eventTypeCode) ||
      EventTypes.isWallClock(eventTypeCode) ||
      EventTypes.isMethodTraceEventType(eventTypeCode) ||
      EventTypes.isCpuTimeSample(eventTypeCode)
    ) {
      return AxisFormatType.DURATION_IN_NANOS;
    } else if (EventTypes.isAllocationEventType(eventTypeCode)) {
      return AxisFormatType.BYTES;
    } else {
      return AxisFormatType.NUMBER;
    }
  }
}
