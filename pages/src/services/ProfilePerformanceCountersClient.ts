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

import axios from "axios";
import HttpUtils from "./HttpUtils";
import GlobalVars from "./GlobalVars";
import PerformanceCounter from "@/services/model/PerformanceCounter.ts";
import PerformanceCounterEnhanced from "@/services/model/PerformanceCounterEnhanced.ts";
import PerformanceCounterDataType from "@/services/model/PerformanceCounterDataType.ts";
import FormattingService from "@/services/FormattingService.ts";

export default abstract class ProfilePerformanceCountersClient {

    public static async exists(projectId: string, profileId: string): Promise<Boolean> {
        let baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/perfcounters/exists';
        return axios.get<Boolean>(baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }

    public static async get(projectId: string, profileId: string): Promise<PerformanceCounterEnhanced[]> {
        let baseUrl = GlobalVars.url + '/projects/' + projectId + '/profiles/' + profileId + '/perfcounters';
        return axios.get<PerformanceCounter[]>(baseUrl, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
            .then(counters => counters.map(
                (counter: PerformanceCounterEnhanced) => this.enhanceCounter(counter)));
    }

    private static enhanceCounter(counter: PerformanceCounter): PerformanceCounterEnhanced {
        const category = this.getCategoryFromKey(counter.key);
        const formattedValue = this.formatValue(counter);

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
        const secondPart = this.extractKeySecondPart(key);

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
                const nanoseconds = parseInt(counter.value);
                return isNaN(nanoseconds) ? counter.value : FormattingService.formatDuration2Units(nanoseconds);

            case PerformanceCounterDataType.timestamp:
                const timestamp = parseInt(counter.value);
                return isNaN(timestamp) ? counter.value : FormattingService.formatTimestamp(timestamp).replace('T', ' ');

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
