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

import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import EventTypes from '@/services/EventTypes';
import DifferentialFlamegraphTooltip from '@/services/flamegraphs/tooltips/DifferentialFlamegraphTooltip';
import BasicFlamegraphTooltip from '@/services/flamegraphs/tooltips/BasicFlamegraphTooltip';

export default class FlamegraphTooltipFactory {
  static create(eventType: string, useWeight: boolean, isDifferential: boolean): FlamegraphTooltip {
    if (isDifferential) {
      return new DifferentialFlamegraphTooltip(eventType, useWeight);
    } else if (
      EventTypes.isExecutionEventType(eventType) ||
      EventTypes.isCpuTimeSample(eventType)
    ) {
      return new BasicFlamegraphTooltip(eventType, useWeight, null, null, true);
    } else if (
      EventTypes.isAllocationEventType(eventType) ||
      EventTypes.isMallocAllocationEventType(eventType) ||
      EventTypes.isNativeLeakEventType(eventType)
    ) {
      return new BasicFlamegraphTooltip(
        eventType,
        useWeight,
        'Allocated',
        FlamegraphTooltip.format_bytes
      );
    } else if (EventTypes.isMethodTraceEventType(eventType)) {
      return new BasicFlamegraphTooltip(
        eventType,
        useWeight,
        'Latency',
        FlamegraphTooltip.format_duration
      );
    } else if (EventTypes.isBlockingEventType(eventType)) {
      return new BasicFlamegraphTooltip(
        eventType,
        useWeight,
        'Blocked Time',
        FlamegraphTooltip.format_duration
      );
    } else {
      return new BasicFlamegraphTooltip(eventType, useWeight);
    }
  }
}
