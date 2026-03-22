/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.server.core.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("jeffrey.server.job")
public class JobProperties {

    private Duration defaultPeriod = Duration.ofMinutes(1);
    private Map<String, Duration> period = new HashMap<>();

    public Duration getDefaultPeriod() {
        return defaultPeriod;
    }

    public void setDefaultPeriod(Duration defaultPeriod) {
        this.defaultPeriod = defaultPeriod;
    }

    public Map<String, Duration> getPeriod() {
        return period;
    }

    public void setPeriod(Map<String, Duration> period) {
        this.period = period;
    }

    public Duration resolvePeriod(String jobName) {
        return period.getOrDefault(jobName, defaultPeriod);
    }

    public Duration resolvePeriod(String jobName, Duration fallback) {
        return period.getOrDefault(jobName, fallback);
    }
}
