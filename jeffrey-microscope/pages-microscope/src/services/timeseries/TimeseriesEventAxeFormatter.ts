/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
      EventTypes.isMethodTraceEventType(eventTypeCode)
    ) {
      return AxisFormatType.DURATION_IN_NANOS;
    } else if (EventTypes.isAllocationEventType(eventTypeCode)) {
      return AxisFormatType.BYTES;
    } else {
      return AxisFormatType.NUMBER;
    }
  }
}
