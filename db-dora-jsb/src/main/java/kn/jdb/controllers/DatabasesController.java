package kn.jdb.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            Set<String> names = new LinkedHashSet<>();
            collectDatabases(conn, names);
            if (names.isEmpty()) {
                collectSchemas(conn, names);
            }
            return new ArrayList<>(names);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/{database}/tables")
    public List<String> getTables(@PathVariable String database) {
        try (Connection conn = dataSource.getConnection()) {
            Set<String> names = new LinkedHashSet<>();
            // Because JDBC drivers disagree on where a “database” name belongs in getTables(...).
            // Some drivers expose it as catalog (common in MySQL), others as schema (common in PostgreSQL).
            collectTables(conn, database, null, names);
            if (names.isEmpty()) {
                collectTables(conn, null, database, names);
            }
            return new ArrayList<>(names);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/{database}/tables/{table}/columns")
    public List<Map<String, Object>> getColumns(@PathVariable String database,
                                               @PathVariable String table) {
        try (Connection conn = dataSource.getConnection()) {
            List<Map<String, Object>> columns = new ArrayList<>();
            collectColumns(conn, database, table, columns);
            if (columns.isEmpty()) {
                collectColumns(conn, null, table, columns);
            }
            return columns;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
}
