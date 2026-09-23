package kn.jdb.datasource;

/**
 * Parses the {@code protocol://host:port/database} shape of a JDBC URL so that the database
 * segment can be swapped out to connect to a different database on the same server.
 */
public final class JdbcUrlUtil {

    private JdbcUrlUtil() {
    }

    public record UrlParts(String protocol, String host, String port, String database) {
    }

    public static UrlParts parse(String jdbcUrl) {
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalArgumentException("Unsupported JDBC URL (missing '://'): " + jdbcUrl);
        }
        String protocol = jdbcUrl.substring(0, schemeEnd);
        String rest = jdbcUrl.substring(schemeEnd + 3);

        int slashIdx = rest.indexOf('/');
        String hostPort = slashIdx >= 0 ? rest.substring(0, slashIdx) : rest;
        String dbAndQuery = slashIdx >= 0 ? rest.substring(slashIdx + 1) : "";

        int queryIdx = dbAndQuery.indexOf('?');
        String database = queryIdx >= 0 ? dbAndQuery.substring(0, queryIdx) : dbAndQuery;

        int colonIdx = hostPort.indexOf(':');
        String host = colonIdx >= 0 ? hostPort.substring(0, colonIdx) : hostPort;
        String port = colonIdx >= 0 ? hostPort.substring(colonIdx + 1) : "";

        return new UrlParts(protocol, host, port, database);
    }
}
