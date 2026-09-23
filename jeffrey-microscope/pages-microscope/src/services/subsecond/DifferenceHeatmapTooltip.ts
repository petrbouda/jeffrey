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

import FormattingService from '@shared/services/FormattingService';
import EventTypes from '@/services/EventTypes';

export default class DifferenceHeatmapTooltip {
  private readonly eventType: string;
  private readonly useWeight: boolean;

  constructor(eventType: string, useWeight: boolean) {
    this.eventType = eventType;
    this.useWeight = useWeight;
  }

  generate(
    value: number,
    primary: number,
    secondary: number,
    second: number,
    millis: string
  ): string {
    const isPositive = value > 0;
    const isNegative = value < 0;
    const colorClass = isPositive ? 'text-danger' : isNegative ? 'text-success' : 'text-muted';
    const sign = isPositive ? '+' : '';

    const formattedValue = this.#formatValue(value);
    const formattedPrimary = this.#formatValue(primary);
    const formattedSecondary = this.#formatValue(secondary);
    const changeLabel = this.#getChangeLabel(value);

    return `
            <div class="card shadow-sm" style="min-width: 220px; font-size: 0.85rem;">
                <div class="card-header py-1 px-2 text-center bg-light border-bottom">
                    <div class="fw-bold small">Difference</div>
                </div>
                <div class="card-body p-0 pt-1">
                    <div class="px-2 pb-1">
                        <div class="d-flex justify-content-between align-items-center py-0">
                            <span class="small text-muted">Primary:</span>
                            <span class="small fw-semibold ms-2">${formattedPrimary}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center py-0">
                            <span class="small text-muted">Secondary:</span>
                            <span class="small fw-semibold ms-2">${formattedSecondary}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center py-0 border-top mt-1 pt-1">
                            <span class="small text-muted">Change:</span>
                            <span class="small fw-semibold ms-2 ${colorClass}">${sign}${formattedValue}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center py-0">
                            <span class="small text-muted">Status:</span>
                            <span class="small fw-semibold ms-2 ${colorClass}">${changeLabel}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center py-0">
                            <span class="small text-muted">Time:</span>
                            <span class="small fw-semibold ms-2">${second}s ${millis}ms</span>
                        </div>
                    </div>
                </div>
            </div>`;
  }

  #formatValue(value: number): string {
    const absValue = Math.abs(value);

    if (EventTypes.isAllocationEventType(this.eventType) && this.useWeight) {
      return FormattingService.formatBytes(absValue);
    } else if (EventTypes.isBlockingEventType(this.eventType) && this.useWeight) {
      return FormattingService.formatDuration(absValue);
    } else {
      return FormattingService.formatNumber(absValue);
    }
  }

  #getChangeLabel(value: number): string {
    if (value > 0) {
      return 'Increased in Primary';
    } else if (value < 0) {
      return 'Decreased in Primary';
    } else {
      return 'No change';
    }
  }
}
