package kn.jdb.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base {@link DataSourceProvider} that, given a way to resolve {@link ConnectionSettings}
 * per environment code, lazily builds and caches one pooled {@link DataSource} per
 * (environment, database) pair, validating requested databases against the environment's
 * actual available databases.
 */
public abstract class AbstractCachingDataSourceProvider implements DataSourceProvider {

    private final Map<String, DataSource> dataSourcesByKey = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> availableDatabasesByEnvironment = new ConcurrentHashMap<>();

    /**
     * Resolves the shared connection settings (host/port/credentials/default database) for
     * the given normalized environment key. Implementations should throw if the environment
     * is unknown/misconfigured.
     */
    protected abstract ConnectionSettings resolveSettings(String environmentKey);

    /**
     * Normalizes a raw (possibly null/blank) environment value into a cache key.
     */
    protected abstract String normalizeEnvironment(String environment);

    @Override
    public DataSource getDataSource(String environment) {
        String key = normalizeEnvironment(environment);
        ConnectionSettings settings = resolveSettings(key);
        return getOrBuildDataSource(key, settings.defaultDatabase(), settings);
    }

    @Override
    public DataSource getDataSource(String environment, String database) {
        if (database == null || database.isBlank()) {
            return getDataSource(environment);
        }

        String key = normalizeEnvironment(environment);
        ConnectionSettings settings = resolveSettings(key);

        Set<String> available = getAvailableDatabases(environment);
        if (!available.contains(database)) {
            throw new IllegalArgumentException("Unknown database: " + database);
        }

        return getOrBuildDataSource(key, database, settings);
    }

    @Override
    public Set<String> getAvailableDatabases(String environment) {
        String key = normalizeEnvironment(environment);
        return availableDatabasesByEnvironment.computeIfAbsent(key, k -> fetchAvailableDatabases(environment));
    }

    /**
     * All DataSources created so far, for lifecycle hooks (e.g. Lambda SnapStart) that need
     * to act on every pooled connection.
     */
    public Collection<DataSource> getManagedDataSources() {
        return dataSourcesByKey.values();
    }

    private Set<String> fetchAvailableDatabases(String environment) {
        DataSource dataSource = getDataSource(environment);
        try (Connection conn = dataSource.getConnection()) {
            return CatalogUtil.listDatabases(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list databases for environment "
                    + (environment == null || environment.isBlank() ? "(default)" : environment), e);
        }
    }

    private DataSource getOrBuildDataSource(String environmentKey, String database, ConnectionSettings settings) {
        String cacheKey = environmentKey + "|" + database;
        return dataSourcesByKey.computeIfAbsent(cacheKey, k -> buildDataSource(settings, database));
    }

    private static DataSource buildDataSource(ConnectionSettings settings, String database) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.jdbcUrl(database));
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMinimumIdle(0);
        // Fix for:
        // HikariDataSource (HikariPool-1) is not configured to allow pool suspension.
        // This will cause problems when the application is checkpointed. Please configure allow-pool-suspension to fix this!
        config.setAllowPoolSuspension(true);
        return new HikariDataSource(config);
    }
}
