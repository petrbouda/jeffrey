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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import PerformanceCounter from '@/services/api/model/PerformanceCounter.ts';
import PerformanceCounterEnhanced from '@/services/api/model/PerformanceCounterEnhanced.ts';
import PerformanceCounterDataType from '@/services/api/model/PerformanceCounterDataType.ts';
import FormattingService from '@shared/services/FormattingService.ts';

export default class ProfilePerformanceCountersClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'perfcounters');
  }

  async exists(): Promise<boolean> {
    return super.get<boolean>('/exists');
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
      case PerformanceCounterDataType.bytes: {
        const bytes = parseInt(counter.value);
        return isNaN(bytes) ? counter.value : FormattingService.formatBytes(bytes);
      }

      case PerformanceCounterDataType.duration: {
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
      }

      case PerformanceCounterDataType.timestamp: {
        const timestamp = parseInt(counter.value);
        return isNaN(timestamp)
          ? counter.value
          : FormattingService.formatTimestamp(timestamp).replace('T', ' ');
      }

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
