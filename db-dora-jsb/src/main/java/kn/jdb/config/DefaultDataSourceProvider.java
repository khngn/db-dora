package kn.jdb.config;

import com.zaxxer.hikari.HikariDataSource;
import kn.jdb.datasource.AbstractCachingDataSourceProvider;
import kn.jdb.datasource.ConnectionSettings;
import kn.jdb.datasource.DataSourceProvider;
import kn.jdb.datasource.JdbcUrlUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Non-lambda (e.g. local) {@link DataSourceProvider} that ignores the requested environment
 * code (there's only one local connection) but supports switching {@code database} within
 * that single server by reusing the host/port/credentials of the Spring Boot auto-configured
 * DataSource (from {@code spring.datasource.*}).
 * <p>
 * In normal (non-test) operation, Spring Boot always auto-configures that DataSource as a real
 * {@link HikariDataSource}, so its JDBC URL/username/password can be read and {@code database}
 * switching works exactly like {@link kn.jdb.datasource.AbstractCachingDataSourceProvider}
 * elsewhere. The only case where {@code database} is ignored is when the injected
 * {@code DataSource} isn't a {@code HikariDataSource} - e.g. a Mockito-mocked bean in tests,
 * which has no real JDBC URL to derive other databases' connection settings from. That fallback
 * exists purely so the Spring context can still start under test; it is not expected to be hit
 * in production.
 */
@Component
@Profile("!lambda")
public class DefaultDataSourceProvider implements DataSourceProvider {

    private final DataSource dataSource;
    private final AbstractCachingDataSourceProvider delegate;

    public DefaultDataSourceProvider(DataSource dataSource) {
        this.dataSource = dataSource;
        this.delegate = dataSource instanceof HikariDataSource hikari ? buildDelegate(hikari) : null;
    }

    private static AbstractCachingDataSourceProvider buildDelegate(HikariDataSource hikari) {
        JdbcUrlUtil.UrlParts parts = JdbcUrlUtil.parse(hikari.getJdbcUrl());
        ConnectionSettings settings = new ConnectionSettings(
                parts.protocol(), parts.host(), parts.port(), parts.database(),
                hikari.getUsername(), hikari.getPassword());

        return new AbstractCachingDataSourceProvider() {
            @Override
            protected String normalizeEnvironment(String environment) {
                return "";
            }

            @Override
            protected ConnectionSettings resolveSettings(String environmentKey) {
                return settings;
            }
        };
    }

    @Override
    public DataSource getDataSource(String environment) {
        return dataSource;
    }

    @Override
    public DataSource getDataSource(String environment, String database) {
        // delegate is only null when the injected DataSource isn't a HikariDataSource (test
        // mocks); in real runs it's always a Hikari pool and this always switches databases.
        if (delegate == null || database == null || database.isBlank()) {
            return dataSource;
        }
        return delegate.getDataSource(environment, database);
    }

    @Override
    public Set<String> getAvailableDatabases(String environment) {
        if (delegate != null) {
            return delegate.getAvailableDatabases(environment);
        }
        return DataSourceProvider.super.getAvailableDatabases(environment);
    }
}
