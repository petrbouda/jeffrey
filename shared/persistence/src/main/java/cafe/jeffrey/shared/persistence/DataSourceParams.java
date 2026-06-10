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
