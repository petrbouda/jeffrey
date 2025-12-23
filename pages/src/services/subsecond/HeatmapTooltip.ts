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

import FormattingService from "@/services/FormattingService";
import EventTypes from "@/services/EventTypes";

export default class HeatmapTooltip {

    private readonly eventType: string;
    private readonly useWeight: boolean;

    constructor(eventType: string, useWeight: boolean) {
        this.eventType = eventType;
        this.useWeight = useWeight;
    }

    generate(value: number, second: number, millis: string): string {
        const headerTitle = this.#getEventTypeDisplayName();
        const valueRow = this.#generateValueRow(value);

        return `
            <div class="card shadow-sm" style="min-width: 180px; font-size: 0.85rem;">
                <div class="card-header py-1 px-2 text-center bg-light border-bottom">
                    <div class="fw-bold small">${headerTitle}</div>
                </div>
                <div class="card-body p-0 pt-1">
                    <div class="px-2 pb-1">
                        ${valueRow}
                        <div class="d-flex justify-content-between align-items-center py-0">
                            <span class="small text-muted">Time:</span>
                            <span class="small fw-semibold ms-2">${second}s ${millis}ms</span>
                        </div>
                    </div>
                </div>
            </div>`;
    }

    #getEventTypeDisplayName(): string {
        if (EventTypes.isExecutionEventType(this.eventType)) {
            return 'Execution Sample';
        } else if (EventTypes.isAllocationEventType(this.eventType)) {
            return 'Allocation';
        } else if (EventTypes.isBlockingEventType(this.eventType)) {
            return 'Blocking Event';
        } else if (EventTypes.isMethodTraceEventType(this.eventType)) {
            return 'Method Trace';
        } else if (EventTypes.isWallClock(this.eventType)) {
            return 'Wall Clock';
        } else if (EventTypes.isMallocAllocationEventType(this.eventType)) {
            return 'Native Allocation';
        } else if (EventTypes.isNativeLeakEventType(this.eventType)) {
            return 'Native Leak';
        }
        return 'Event';
    }

    #generateValueRow(value: number): string {
        const { label, formattedValue } = this.#getValueLabelAndFormat(value);

        return `
            <div class="d-flex justify-content-between align-items-center py-0">
                <span class="small text-muted">${label}:</span>
                <span class="small fw-semibold ms-2">${formattedValue}</span>
            </div>`;
    }

    #getValueLabelAndFormat(value: number): { label: string; formattedValue: string } {
        if (EventTypes.isAllocationEventType(this.eventType) && this.useWeight) {
            return {
                label: 'Allocated',
                formattedValue: FormattingService.formatBytes(value)
            };
        } else if (EventTypes.isBlockingEventType(this.eventType) && this.useWeight) {
            return {
                label: 'Blocked Time',
                formattedValue: FormattingService.formatDuration(value)
            };
        } else {
            return {
                label: 'Samples',
                formattedValue: FormattingService.formatNumber(value)
            };
        }
    }
}
