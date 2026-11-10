package dev.tally;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres@sha256:ff87138949ad9a46f32ff23bd6852b52ce024a13dffe9b4087be98292cdd3b4f")
                .asCompatibleSubstituteFor("postgres"));
    }

    /** Flyway migrates as the container superuser; the app itself connects as tally_app, like in production. */
    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
        };
    }
}
