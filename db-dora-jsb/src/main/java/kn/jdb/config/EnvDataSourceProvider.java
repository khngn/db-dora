package kn.jdb.config;

import jakarta.annotation.PreDestroy;
import kn.jdb.datasource.AbstractCachingDataSourceProvider;
import kn.jdb.datasource.ConnectionSettings;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a {@link javax.sql.DataSource} per environment code, e.g. "E1", "E4", "E6", and
 * per database within that environment.
 * <p>
 * Connection settings for a given code are read from environment variables prefixed with
 * the (upper-cased) code, e.g. {@code E1_DATASOURCE_HOST}. When no environment code is
 * supplied, the un-prefixed variables (e.g. {@code DATASOURCE_HOST}) are used as the default.
 * <p>
 * DataSources are built lazily on first use and cached/pooled per (environment, database) pair
 * so that connections are reused across requests.
 */
@Component
@Profile("lambda")
public class EnvDataSourceProvider extends AbstractCachingDataSourceProvider {

    private final KmsClient kmsClient = KmsClient.create();
    private final Map<String, ConnectionSettings> settingsByEnvironment = new ConcurrentHashMap<>();

    @Override
    protected String normalizeEnvironment(String environment) {
        return environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    protected ConnectionSettings resolveSettings(String environmentKey) {
        return settingsByEnvironment.computeIfAbsent(environmentKey, this::buildSettings);
    }

    private ConnectionSettings buildSettings(String environmentKey) {
        String prefix = environmentKey.isEmpty() ? "" : environmentKey + "_";

        String protocol = requiredEnv(prefix, "DATASOURCE_PROTOCOL");
        String host = requiredEnv(prefix, "DATASOURCE_HOST");
        String port = requiredEnv(prefix, "DATASOURCE_PORT");
        String database = requiredEnv(prefix, "DATASOURCE_DATABASE");
        String encryptedUsername = requiredEnv(prefix, "DATASOURCE_USERNAME_BY_KMS");
        String encryptedPassword = requiredEnv(prefix, "DATASOURCE_PASSWORD_BY_KMS");

        String username = decryptKmsCiphertext(encryptedUsername);
        String password = decryptKmsCiphertext(encryptedPassword);

        return new ConnectionSettings(protocol, host, port, database, username, password);
    }

    private String decryptKmsCiphertext(String base64Ciphertext) {
        byte[] ciphertext = Base64.getDecoder().decode(base64Ciphertext);

        DecryptRequest request = DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
                .build();

        return kmsClient.decrypt(request)
                .plaintext()
                .asString(StandardCharsets.UTF_8);
    }

    private static String requiredEnv(String prefix, String name) {
        String fullName = prefix + name;
        String value = System.getenv(fullName);
        if (value == null || value.isBlank()) {
            if (prefix.isEmpty()) {
                throw new IllegalStateException("Missing environment variable: " + fullName);
            }
            value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Unknown environment: " + prefix.substring(0, prefix.length() - 1));
            }
        }
        return value;
    }

    @PreDestroy
    public void close() {
        kmsClient.close();
    }
}
