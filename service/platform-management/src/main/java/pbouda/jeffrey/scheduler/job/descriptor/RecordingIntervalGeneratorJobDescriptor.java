/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.scheduler.job.descriptor;

import pbouda.jeffrey.common.model.job.JobType;

import java.time.LocalTime;
import java.util.Map;

public record RecordingIntervalGeneratorJobDescriptor(
        String filePattern,
        LocalTime at,
        LocalTime from,
        LocalTime to
) implements JobDescriptor<RecordingIntervalGeneratorJobDescriptor> {
    /**
     * { "filePattern": "generated/recording-%t.jfr", "at": "17:00", "from": "10:00", "to": "12:00" }
     * `at` can be missing, and it's automatically 1 minute after `to`
     */
    private static final String PARAM_FILE_PATTERN = "filePattern";
    private static final String PARAM_AT = "at";
    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";

    @Override
    public Map<String, String> params() {
        return Map.of();
    }

    @Override
    public JobType type() {
        return JobType.INTERVAL_RECORDING_GENERATOR;
    }

    private record JobParams(String filePattern, LocalTime at, LocalTime from, LocalTime to) {
        private static JobParams parse(Map<String, String> params) {
            LocalTime from = requiredTime(params, PARAM_FROM);
            LocalTime to = requiredTime(params, PARAM_TO);
            LocalTime at = requiredTime(params, PARAM_AT);
            return new JobParams(params.get(PARAM_FILE_PATTERN), at, from, to);
        }

        private static LocalTime requiredTime(Map<String, String> params, String paramName) {
            String paramValue = params.get(paramName);
            if (paramValue == null) {
                throw new IllegalArgumentException("Missing parameter: " + paramName);
            }
            return LocalTime.parse(paramValue);
        }
    }
}
