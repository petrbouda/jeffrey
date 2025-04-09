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

import ThreadRectangle from "@/services/thread/ThreadRectangle";
import EventMetadata from "@/services/thread/model/EventMetadata";
import FormattingService from "@/services/FormattingService";

export default class ThreadTooltips {
    static header(javaName: string): string {
        return `
            <div class="tooltip-header p-3 border-bottom">
                <h5 class="m-0 text-dark font-weight-bold">${javaName}</h5>
            </div>`;
    }

    static basic(metadata: EventMetadata, segments: ThreadRectangle[], colorRgb: string): string {
        let typeFragment = "";
        let firstValues = segments[0].period.values;

        let fields = "";
        metadata.fields.forEach((threadField, index) => {
            const field = `
                <div class="tooltip-row d-flex px-3 py-2">
                    <span class="field-name text-secondary font-weight-medium">${threadField.name}:</span>
                    <span class="field-value text-dark">${FormattingService.format(firstValues[index], threadField.type)}</span>
                </div>`

            fields += field;
        });

        return `
            ${ThreadTooltips.divider(metadata.label, segments.length, colorRgb)}
            <div class="tooltip-content">
                ${typeFragment}
                ${fields}
            </div>`;
    }

    private static divider(text: string, eventCount: number, colorRgb: string): string {
        return `<div class="tooltip-category d-flex align-items-center px-3 py-2 bg-light">
                    <div class="color-indicator mr-3" style="width: 12px; height: 12px; border-radius: 3px; background-color: ${colorRgb}"></div> 
                    <div class="d-flex justify-content-between w-100">
                        <span class="category-name font-weight-medium">${text}</span>
                        <span class="event-count text-muted small">${eventCount} event${eventCount !== 1 ? 's' : ''}</span>
                    </div>
                </div>`;
    }
}
