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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import PerformanceCounter from '@/services/api/model/PerformanceCounter.ts';
import PerformanceCounterEnhanced from '@/services/api/model/PerformanceCounterEnhanced.ts';
import PerformanceCounterDataType from '@/services/api/model/PerformanceCounterDataType.ts';
import FormattingService from '@/services/FormattingService.ts';

export default class ProfilePerformanceCountersClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'perfcounters');
  }

  async exists(): Promise<Boolean> {
    return super.get<Boolean>('/exists');
  }

  async getAll(): Promise<PerformanceCounterEnhanced[]> {
    const counters = await super.get<PerformanceCounter[]>('');
    return counters.map((counter: PerformanceCounter) =>
      ProfilePerformanceCountersClient.enhanceCounter(counter)
    );
  }

  private static enhanceCounter(counter: PerformanceCounter): PerformanceCounterEnhanced {
    const category = ProfilePerformanceCountersClient.getCategoryFromKey(counter.key);
    const formattedValue = ProfilePerformanceCountersClient.formatValue(counter);

    return new PerformanceCounterEnhanced(
      counter.key,
      counter.value,
      formattedValue,
      category,
      counter.datatype,
      counter.description
    );
  }

  private static getCategoryFromKey(key: string): string {
    const secondPart = ProfilePerformanceCountersClient.extractKeySecondPart(key);

    // Map specific categories to a common category to merge them
    if (secondPart === 'urlClassLoader' || secondPart === 'cls' || secondPart === 'classloader') {
      return 'classloader'; // Merge all classloader-related categories
    }

    return secondPart || 'unknown';
  }

  private static extractKeySecondPart(key: string): string | null {
    const parts = key.split('.');
    if (parts.length > 1) {
      return parts[1];
    }
    return null;
  }

  private static formatValue(counter: PerformanceCounter): string {
    if (!counter.datatype || !counter.value) {
      return counter.value;
    }

    switch (counter.datatype) {
      case PerformanceCounterDataType.bytes:
        const bytes = parseInt(counter.value);
        return isNaN(bytes) ? counter.value : FormattingService.formatBytes(bytes);

      case PerformanceCounterDataType.duration:
        const duration = parseInt(counter.value);
        if (isNaN(duration)) {
          return counter.value;
        }

        let durationInNanos = 0;
        // Special case for lastEntryTime and lastExitTime counters (in milliseconds)
        if (counter.key.endsWith('.lastEntryTime') || counter.key.endsWith('.lastExitTime')) {
          durationInNanos = duration * 1_000;
        } else {
          durationInNanos = duration;
        }

        return FormattingService.formatDuration2Units(durationInNanos);

      case PerformanceCounterDataType.timestamp:
        const timestamp = parseInt(counter.value);
        return isNaN(timestamp)
          ? counter.value
          : FormattingService.formatTimestamp(timestamp).replace('T', ' ');

      case PerformanceCounterDataType.count:
        // For count type, we use the raw value if it's a number
        return counter.value;

      case PerformanceCounterDataType.string:
        // For string type, we use the raw value
        return counter.value;

      default:
        return counter.value;
    }
  }
}
