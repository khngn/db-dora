package kn.jdb.datasource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * Resolves a {@link DataSource} for a given environment code (e.g. "E1", "E4", "E6").
 * <p>
 * When {@code environment} is {@code null} or blank, implementations should fall back
 * to a default DataSource.
 */
public interface DataSourceProvider {

    /**
     * DataSource for an environment's default/configured database.
     */
    DataSource getDataSource(String environment);

    /**
     * DataSource connected to a specific {@code database} within the given environment.
     * <p>
     * A single JDBC connection can only ever be bound to one physical database (this is
     * strictly true for e.g. PostgreSQL, which has no cross-database queries), so switching
     * databases means opening a distinct connection whose JDBC URL targets that database,
     * not merely qualifying identifiers in SQL.
     * <p>
     * When {@code database} is {@code null} or blank, this returns the same as
     * {@link #getDataSource(String)}. Implementations that support multiple databases per
     * environment should reject unknown database names (e.g. with {@link IllegalArgumentException}).
     */
    default DataSource getDataSource(String environment, String database) {
        return getDataSource(environment);
    }

    /**
     * The set of databases (or schemas, for drivers that don't expose catalogs) visible in
     * the given environment.
     */
    default Set<String> getAvailableDatabases(String environment) {
        try (Connection conn = getDataSource(environment).getConnection()) {
            return CatalogUtil.listDatabases(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list databases", e);
        }
    }
}
