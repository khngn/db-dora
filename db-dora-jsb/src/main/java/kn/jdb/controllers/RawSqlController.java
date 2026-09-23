package kn.jdb.controllers;

import kn.jdb.datasource.DataSourceProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/raw-sql")
public class RawSqlController {

    private final DataSourceProvider dataSourceProvider;

    public RawSqlController(DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @PostMapping
    public Object executeRawSql(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String schema,
            @RequestBody String sql
    ) {
        DataSource dataSource = dataSourceProvider.getDataSource(environment, database);
        try (Connection conn = dataSource.getConnection()) {
            // Pooled connections (Hikari) are reused across requests, so a schema change here
            // must be reverted before the connection is returned to the pool - otherwise a
            // later request that doesn't specify a schema could inherit this one's setting.
            String originalSchema = null;
            boolean schemaChanged = false;
            if (schema != null && !schema.isBlank()) {
                // So unqualified table names in the raw SQL resolve against the requested
                // schema, the same way /databases/{database}/tables/... does, instead of
                // whatever the connection's default search_path happens to be.
                originalSchema = conn.getSchema();
                conn.setSchema(schema);
                schemaChanged = true;
            }
            try (Statement stmt = conn.createStatement()) {
                boolean hasResultSet = stmt.execute(sql);

                if (!hasResultSet) {
                    return Map.of("updateCount", stmt.getUpdateCount());
                }

                try (ResultSet rs = stmt.getResultSet()) {
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
            } finally {
                if (schemaChanged) {
                    conn.setSchema(originalSchema);
                }
            }
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to execute SQL", e);
        }
    }
}
