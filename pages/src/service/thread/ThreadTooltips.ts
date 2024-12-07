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

import ThreadRectangle from "@/service/thread/ThreadRectangle";
import EventMetadata from "@/service/thread/model/EventMetadata";
import FormattingService from "@/service/FormattingService";

export default class ThreadTooltips {
    static header(javaName: string): string {
        return `
            <div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">${javaName}</div>
            <hr class="mt-1">`;
    }

    static basic(metadata: EventMetadata, segments: ThreadRectangle[], colorRgb: string): string {
        let typeFragment = "";
        let firstValues = segments[0].period.values;

        let fields = "";
        metadata.fields.forEach((threadField, index) => {
            const field = `
                <tr>
                    <th class="text-right">${threadField.name}</th>
                    <td>${FormattingService.format(firstValues[index], threadField.type)}<td>
                </tr>`

            fields += field;
        });

        return `
            ${ThreadTooltips.divider(metadata.label, segments.length, colorRgb)}
            <table class="pl-1 pr-1 text-sm">
                ${typeFragment}
                ${fields}
            </table>`;
    }

    private static divider(text: string, eventCount: number, colorRgb: string): string {
        return `<div class="m-2 italic text-gray-500 text-sm flex flex-row">
                    <div class="mr-2 w-1rem h-1rem border-1" style="background-color: ${colorRgb}"></div> 
                    <div>${text} <span class="text-black-alpha-60">(# of events ${eventCount})</span></div>
                </div>`;
    }
}
