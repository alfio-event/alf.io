/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import java.util.Map;

import static alfio.BaseTestConfiguration.POSTGRES_DB;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MigrationValidatorTest {

    private static final Logger log = LoggerFactory.getLogger(MigrationValidatorTest.class);
    public static final String DB_ALIAS = "db";
    private PostgreSQLContainer<?> postgres;
    private final Network network = Network.SHARED;
    private final Map<String, String> envVariables = Map.ofEntries(
        entry("POSTGRES_PORT_5432_TCP_PORT", "5432"),
        entry("POSTGRES_PORT_5432_TCP_ADDR", DB_ALIAS),
        entry("POSTGRES_ENV_POSTGRES_DB", POSTGRES_DB),
        entry("POSTGRES_ENV_POSTGRES_USERNAME", "alfio_user"),
        entry("POSTGRES_ENV_POSTGRES_PASSWORD", "password"),
        entry("SPRING_PROFILES_ACTIVE", "jdbc-session")
    );

    @BeforeAll
    static void beforeAll() {
        Assumptions.assumeTrue("true".equals(System.getenv("MIGRATION_TEST")));
    }

    @BeforeEach
    void setUp() {
        postgres = new PostgreSQLContainer<>("postgres:10")
            .withDatabaseName(POSTGRES_DB)
            .withNetwork(network)
            .withNetworkAliases(DB_ALIAS)
            .withInitScript("init-db-user.sql");
        postgres.start();
    }

    @AfterEach
    void tearDown() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void testMigration() {
        // attempting to start alf.io
        assertNotNull(postgres);
        try (var alfioLatest = new GenericContainer<>("alfio/alf.io:latest");
             var alfioCurrent = new GenericContainer<>("ghcr.io/alfio-event/alf.io/dev-main:latest")) {

            log.info("starting stable version");
            alfioLatest.withEnv(envVariables)
                .withImagePullPolicy(PullPolicy.alwaysPull())
                .withNetwork(network)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/healthz").forStatusCode(200))
                .withStartupAttempts(1)
                .start();

            log.info("started and healthy");

            // start alfio current
            log.info("starting dev version");
            try {
                alfioCurrent.withEnv(envVariables)
                    .withImagePullPolicy(PullPolicy.alwaysPull())
                    .withExposedPorts(8080)
                    .withNetwork(network)
                    .waitingFor(Wait.forHttp("/healthz").forStatusCode(200))
                    .withStartupAttempts(1)
                    .start();
            } catch (Exception e) {
                throw new IllegalStateException("cannot start alf.io dev. Message from container: \n" + alfioCurrent.getLogs(OutputFrame.OutputType.STDOUT));
            }
            log.info("started and healthy");
        }
    }
}
