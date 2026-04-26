package cafe.jeffrey.shared.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.PoolStats;
import cafe.jeffrey.shared.persistence.metrics.JfrHikariDataSource;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolMetricsTracker;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;

public abstract class DuckDBDataSourceProvider {

    public static DataSource open(DataSourceParams params) {
        HikariConfig config = new HikariConfig();
        if (params.enableMetrics()) {
            config.setMetricsTrackerFactory((String poolName, PoolStats poolStats) -> {
                JfrPoolStatisticsPeriodicRecorder.addPool(poolName, poolStats);
                return new JfrPoolMetricsTracker(poolName);
            });
        }
        config.setPoolName(params.poolName());
        config.setKeepaliveTime(params.keepAliveTime().toMillis());
        config.setMaximumPoolSize(params.maxPoolSize());
        config.setMaxLifetime(params.maxLifetime().toMillis());
        config.setJdbcUrl(params.url());
        params.additionalProperties().forEach(config::addDataSourceProperty);
        return new JfrHikariDataSource(config);
    }
}
