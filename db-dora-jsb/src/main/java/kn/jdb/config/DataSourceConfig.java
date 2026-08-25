package kn.jdb.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@Profile("lambda")
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        String protocol = requiredEnv("DATASOURCE_PROTOCOL");
        String host = requiredEnv("DATASOURCE_HOST");
        String port = requiredEnv("DATASOURCE_PORT");
        String database = requiredEnv("DATASOURCE_DATABASE");
        String encryptedUsername = requiredEnv("DATASOURCE_USERNAME_BY_KMS");
        String encryptedPassword = requiredEnv("DATASOURCE_PASSWORD_BY_KMS");

        String username = decryptKmsCiphertext(encryptedUsername);
        String password = decryptKmsCiphertext(encryptedPassword);

        HikariConfig config = new HikariConfig();
        //config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(protocol + "://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);

        return new HikariDataSource(config);
    }

    private static String decryptKmsCiphertext(String base64Ciphertext) {
        byte[] ciphertext = Base64.getDecoder().decode(base64Ciphertext);

        try (KmsClient kms = KmsClient.create()) {
            DecryptRequest request = DecryptRequest.builder()
                    .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
                    .build();

            return kms.decrypt(request)
                    .plaintext()
                    .asString(StandardCharsets.UTF_8);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
