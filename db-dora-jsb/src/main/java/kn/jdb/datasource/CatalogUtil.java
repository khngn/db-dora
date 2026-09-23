package kn.jdb.datasource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Lists the databases/schemas visible to a connection, and resolves which schema a query
 * should target.
 */
public final class CatalogUtil {

    private CatalogUtil() {
    }

    public static Set<String> listDatabases(Connection conn) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty()) {
            names.addAll(listSchemas(conn));
        }
        return names;
    }

    /**
     * All schemas visible within the connection's current database (e.g. Postgres
     * {@code public}, {@code app}, ...). Drivers with no schema concept (e.g. MySQL, where
     * "database" and "catalog" are effectively the same thing) typically return an empty set.
     */
    public static Set<String> listSchemas(Connection conn) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_SCHEM");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Resolves the schema a table/column/row lookup should target: the explicitly requested
     * {@code schema} if present, otherwise the connection's current default schema (e.g.
     * Postgres' {@code search_path} first entry, typically {@code public}).
     * <p>
     * This matters because {@link java.sql.DatabaseMetaData#getTables} with a {@code null}
     * schema pattern returns tables from <em>every</em> schema in the database, while a plain
     * {@code SELECT * FROM table} only resolves against the connection's default schema. Without
     * pinning both operations to the same schema, a table listed by one may not be the same
     * table (or may not be visible at all) when queried by the other.
     */
    public static String resolveSchema(Connection conn, String schema) throws SQLException {
        if (schema != null && !schema.isBlank()) {
            return schema;
        }
        return conn.getSchema();
    }
}

