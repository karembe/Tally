package dev.tally;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** One shared Spring context and Postgres container for every test class that extends this. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tally.admin-token=" + IntegrationTest.ADMIN_TOKEN)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String ADMIN_TOKEN = "test-admin-token";
}
