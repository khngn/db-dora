package kn.jdb.controllers;

import kn.jdb.datasource.CatalogUtil;
import kn.jdb.datasource.DataSourceProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@RestController
@RequestMapping("/databases")
public class DatabasesController {

    private final DataSourceProvider dataSourceProvider;

    public DatabasesController(DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @GetMapping
    public List<String> getDatabases(@RequestParam(required = false) String environment) {
        try {
            List<String> databases = new ArrayList<>(dataSourceProvider.getAvailableDatabases(environment));
            databases.sort(String::compareTo);
            return databases;
        } catch (IllegalStateException e) {
            throw databaseOperationFailed("list databases", e);
        }
    }

    @GetMapping("/{database}/schemas")
    public List<String> getSchemas(@PathVariable String database, @RequestParam(required = false) String environment) {
        DataSource dataSource = dataSourceProvider.getDataSource(environment, database);
        try (Connection conn = dataSource.getConnection()) {
            List<String> schemas = new ArrayList<>(CatalogUtil.listSchemas(conn));
            schemas.sort(String::compareTo);
            return schemas;
        } catch (SQLException e) {
            throw databaseOperationFailed("list schemas for database " + database, e);
        }
    }

    @GetMapping("/{database}/tables")
    public List<String> getTables(
            @PathVariable String database,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String environment
    ) {
        DataSource dataSource = dataSourceProvider.getDataSource(environment, database);
        try (Connection conn = dataSource.getConnection()) {
            String resolvedSchema = CatalogUtil.resolveSchema(conn, schema);
            Set<String> names = new HashSet<>();
            try (ResultSet rs = conn.getMetaData().getTables(null, resolvedSchema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isBlank()) {
                        names.add(name);
                    }
                }
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
            @PathVariable String table,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String environment
    ) {
        DataSource dataSource = dataSourceProvider.getDataSource(environment, database);
        try (Connection conn = dataSource.getConnection()) {
            String resolvedSchema = CatalogUtil.resolveSchema(conn, schema);
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getColumns(null, resolvedSchema, table, "%")) {
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
            columns.sort(Comparator.comparing(i -> ((String) i.get("name"))));
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
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String environment
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than 0");
        }

        DataSource dataSource = dataSourceProvider.getDataSource(environment, database);
        try (Connection conn = dataSource.getConnection()) {
            String quote = conn.getMetaData().getIdentifierQuoteString();
            if (quote == null || quote.isBlank()) {
                quote = "\"";
            }
            // Qualify with the same schema getTables/getColumns resolved to, so we always
            // read from the exact table that was listed/described - not whatever table the
            // connection's default search_path happens to resolve to.
            String resolvedSchema = CatalogUtil.resolveSchema(conn, schema);
            String qualifiedTable = resolvedSchema == null || resolvedSchema.isBlank()
                    ? quoteIdentifier(table, quote)
                    : quoteIdentifier(resolvedSchema, quote) + "." + quoteIdentifier(table, quote);
            String sql = "SELECT * FROM " + qualifiedTable + " LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, size);
                stmt.setInt(2, page * size);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> rows = readRows(rs);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("page", page);
                    response.put("count", rows.size());
                    response.put("rows", rows);
                    return response;
                }
            }
        } catch (SQLException e) {
            throw databaseOperationFailed("fetch rows for table " + table, e);
        }
    }

    private static ResponseStatusException databaseOperationFailed(String operation, Exception cause) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to " + operation, cause);
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

    private static String quoteIdentifier(String identifier, String quote) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
