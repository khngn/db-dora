package kn.jdb.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@RestController
@RequestMapping("/databases")
public class DatabasesController {

    private final DataSource dataSource;

    public DatabasesController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public List<String> getDatabases() {
        try (Connection conn = dataSource.getConnection()) {
            Set<String> names = new HashSet<>();
            collectDatabases(conn, names);
            if (names.isEmpty()) {
                collectSchemas(conn, names);
            }
            List<String> databases = new ArrayList<>(names);
            databases.sort(String::compareTo);
            return databases;
        } catch (SQLException e) {
            throw databaseOperationFailed("list databases", e);
        }
    }

    @GetMapping("/{database}/tables")
    public List<String> getTables(@PathVariable String database) {
        try (Connection conn = dataSource.getConnection()) {
            Set<String> names = new HashSet<>();
            // Because JDBC drivers disagree on where a “database” name belongs in getTables(...).
            // Some drivers expose it as catalog (common in MySQL), others as schema (common in PostgreSQL).
            collectTables(conn, database, null, names);
            if (names.isEmpty()) {
                collectTables(conn, null, database, names);
            }
            List<String> tables = new ArrayList<>(names);
            tables.sort(String::compareTo);
            return tables;
        } catch (SQLException e) {
            throw databaseOperationFailed("list tables for database " + database, e);
        }
    }

    @GetMapping("/{database}/tables/{table}/columns")
    public List<Map<String, Object>> getColumns(
            @PathVariable String database,
            @PathVariable String table
    ) {
        try (Connection conn = dataSource.getConnection()) {
            List<Map<String, Object>> columns = new ArrayList<>();
            collectColumns(conn, database, table, columns);
            if (columns.isEmpty()) {
                collectColumns(conn, null, table, columns);
            }
            columns.sort((i, j) -> ((String) i.get("name")).compareTo((String) j.get("name")));
            return columns;
        } catch (SQLException e) {
            throw databaseOperationFailed("list columns for table " + table, e);
        }
    }

    @GetMapping("/{database}/tables/{table}/rows")
    public Map<String, Object> getRows(
            @PathVariable String database,
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than 0");
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String qualifiedTable = qualifyTableName(metaData, database, table);
            String sql = "SELECT * FROM " + qualifiedTable + " LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, size);
                stmt.setInt(2, page * size);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> rows = readRows(rs);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("page", page);
                    response.put("size", size);
                    response.put("rows", rows);
                    return response;
                }
            }
        } catch (SQLException e) {
            throw databaseOperationFailed("fetch rows for table " + table, e);
        }
    }

    private static ResponseStatusException databaseOperationFailed(String operation, SQLException cause) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to " + operation, cause);
    }

    private static void collectDatabases(Connection conn, Set<String> names) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
    }

    private static void collectSchemas(Connection conn, Set<String> names) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_SCHEM");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
    }

    private static void collectTables(Connection conn, String catalog, String schema, Set<String> names) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
    }

    private static void collectColumns(Connection conn, String catalog, String table, List<Map<String, Object>> columns) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, null, table, "%")) {
            while (rs.next()) {
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("name", rs.getString("COLUMN_NAME"));
                column.put("type", rs.getString("TYPE_NAME"));
                column.put("jdbcType", rs.getInt("DATA_TYPE"));
                column.put("nullable", rs.getInt("NULLABLE") == 1);
                column.put("size", rs.getObject("COLUMN_SIZE"));
                column.put("defaultValue", rs.getObject("COLUMN_DEF"));
                columns.add(column);
            }
        }
    }

    private static List<Map<String, Object>> readRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new TreeMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }

        return rows;
    }

    private static String qualifyTableName(DatabaseMetaData metaData, String database, String table) throws SQLException {
        String quote = metaData.getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            quote = "\"";
        }

        return quoteIdentifier(database, quote) + "." + quoteIdentifier(table, quote);
    }

    private static String quoteIdentifier(String identifier, String quote) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
