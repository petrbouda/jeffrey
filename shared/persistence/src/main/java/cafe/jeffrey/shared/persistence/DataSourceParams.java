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

package cafe.jeffrey.shared.persistence;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public record DataSourceParams(
        String url,
        int maxPoolSize,
        Integer minIdle,
        Duration maxLifetime,
        Duration keepAliveTime,
        String poolName,
        boolean enableMetrics,
        Map<String, String> additionalProperties) {

    public DataSourceParams {
        if (additionalProperties == null) {
            throw new IllegalArgumentException("additionalProperties cannot be null");
        }
        if (maxLifetime == null || maxLifetime.isNegative()) {
            throw new IllegalArgumentException("maxLifetime must be a positive duration, or zero for an infinite lifetime");
        }
        if (minIdle != null && minIdle < 0) {
            throw new IllegalArgumentException("minIdle cannot be negative");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private int maxPoolSize = 50;
        private Integer minIdle;
        private Duration maxLifetime = Duration.ofHours(1);
        private Duration keepaliveTime = Duration.ofSeconds(30);
        private String poolName = "unnamed-pool";
        private boolean enableMetrics = false;
        private Map<String, String> additionalProperties = new HashMap<>();

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        public Builder maxLifetime(Duration maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }

        public Builder keepAliveTime(Duration keepaliveTime) {
            this.keepaliveTime = keepaliveTime;
            return this;
        }

        public Builder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        public Builder additionalProperties(Map<String, String> additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        public Builder additionalProperty(String key, String value) {
            this.additionalProperties.put(key, value);
            return this;
        }

        public DataSourceParams build() {
            return new DataSourceParams(
                    url, maxPoolSize, minIdle, maxLifetime, keepaliveTime, poolName, enableMetrics, additionalProperties);
        }
    }
}
