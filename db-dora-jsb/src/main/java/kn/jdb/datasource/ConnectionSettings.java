package kn.jdb.datasource;

/**
 * Connection details needed to build a JDBC {@code DataSource} for any specific database
 * within a given server/cluster (host, port, credentials are shared; only the database
 * segment of the JDBC URL changes).
 */
public record ConnectionSettings(
        String protocol,
        String host,
        String port,
        String defaultDatabase,
        String username,
        String password
) {

    public String jdbcUrl(String database) {
        return protocol + "://" + host + ":" + port + "/" + database;
    }
}
