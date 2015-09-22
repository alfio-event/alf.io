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
package alfio.config.support;

import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * For handling the various differences between the cloud providers.
 * <p>
 * Supported:
 * - Openshift : pgsql only
 * - Cloud Foundry: pgsql (elephantdb)
 * - Heroku
 * - local use with system properties
 */
public enum PlatformProvider {
    DEFAULT,

    /**
     * See https://developers.openshift.com/en/managing-environment-variables.html
     **/
    OPENSHIFT {
        @Override
        public String getDriveClassName(Environment env) {
            return POSTGRESQL_DRIVER;
        }

        @Override
        public String getUrl(Environment env) {
            String dbHost = Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_HOST"), "OPENSHIFT_POSTGRESQL_DB_HOST env variable is missing");
            String port = Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_PORT"), "OPENSHIFT_POSTGRESQL_DB_PORT env variable is missing");
            String dbName = Objects.requireNonNull(System.getenv("OPENSHIFT_APP_NAME"), "OPENSHIFT_APP_NAME env variable is missing");

            return "jdbc:postgresql://" + dbHost + ":" + port + "/" + dbName;
        }

        @Override
        public String getUsername(Environment env) {
            return Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_USERNAME"), "OPENSHIFT_POSTGRESQL_DB_USERNAME env variable is missing");
        }


        @Override
        public String getPassword(Environment env) {
            return Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_PASSWORD"), "OPENSHIFT_POSTGRESQL_DB_PASSWORD env variable is missing");
        }

        @Override
        public String getValidationQuery(Environment env) {
            return "SELECT 1";
        }

        @Override
        public String getDialect(Environment env) {
            return PGSQL;
        }

        @Override
        public boolean isHosting(Environment env) {
            return ofNullable(env.getProperty("OPENSHIFT_APP_NAME")).isPresent();
        }
    },

    /**
     * Cloud Foundry configuration.
     * see https://docs.cloudfoundry.org/buildpacks/java/spring-service-bindings.html
     * <p>
     * We assume that the "ElephantSQL" has already been bound to the application.
     * Anyway, since we use Spring, the Cloud Foundry engine should replace the "DataSource" bean with the right one.
     */
    CLOUD_FOUNDRY {
        @Override
        public String getDriveClassName(Environment env) {
            return POSTGRESQL_DRIVER;
        }

        @Override
        public String getUrl(Environment env) {
            return env.getRequiredProperty("vcap.services.elephantsql.credentials.uri");
        }

        @Override
        public String getUsername(Environment env) {
            return "";
        }


        @Override
        public String getPassword(Environment env) {
            return "";
        }

        @Override
        public String getValidationQuery(Environment env) {
            return "SELECT 1";
        }

        @Override
        public String getDialect(Environment env) {
            return PGSQL;
        }

        @Override
        public boolean isHosting(Environment env) {
            return ofNullable(env.getProperty("VCAP_APPLICATION")).isPresent();
        }
    },

    HEROKU {

        @Override
        public String getDriveClassName(Environment env) {
            return POSTGRESQL_DRIVER;
        }

        @Override
        public String getUrl(Environment env) {
            URI uri = resolveURI(env);
            return String.format("%s://%s:%s%s", "jdbc:postgresql", uri.getHost(), uri.getPort(), uri.getPath());
        }

        @Override
        public String getUsername(Environment env) {
            return resolveURI(env).getUserInfo().split(":")[0];
        }


        @Override
        public String getPassword(Environment env) {
            return resolveURI(env).getUserInfo().split(":")[1];
        }

        @Override
        public String getValidationQuery(Environment env) {
            return "SELECT 1";
        }

        @Override
        public String getDialect(Environment env) {
            return PGSQL;
        }

        @Override
        public boolean isHosting(Environment env) {
            return ofNullable(env.getProperty("DYNO")).isPresent();
        }

        private URI resolveURI(Environment env) {
            try {
                return new URI(env.getRequiredProperty("DATABASE_URL"));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

    },

    DOCKER {
        @Override
        public String getDriveClassName(Environment env) {
            return POSTGRESQL_DRIVER;
        }

        @Override
        public String getUrl(Environment env) {
            String dbHost = Objects.requireNonNull(System.getenv("DB_PORT_5432_TCP_ADDR"), "DB_PORT_5432_TCP_ADDR env variable is missing");
            String port = Objects.requireNonNull(System.getenv("DB_PORT_5432_TCP_PORT"), "DB_PORT_5432_TCP_PORT env variable is missing");
            String dbName = Objects.requireNonNull(System.getenv("DB_ENV_POSTGRES_DB"), "DB_ENV_POSTGRES_DB env variable is missing");
            return "jdbc:postgresql://" + dbHost + ":" + port + "/" + dbName;
        }

        @Override
        public String getUsername(Environment env) {
            return Objects.requireNonNull(System.getenv("DB_ENV_POSTGRES_USERNAME"), "DB_ENV_POSTGRES_USERNAME env variable is missing");
        }


        @Override
        public String getPassword(Environment env) {
            return Objects.requireNonNull(System.getenv("DB_ENV_POSTGRES_PASSWORD"), "DB_ENV_POSTGRES_PASSWORD env variable is missing");
        }

        @Override
        public String getValidationQuery(Environment env) {
            return "SELECT 1";
        }

        @Override
        public String getDialect(Environment env) {
            return PGSQL;
        }

        @Override
        public boolean isHosting(Environment env) {
            return ofNullable(env.getProperty("DB_ENV_POSTGRES_DB")).isPresent();
        }
    };

    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";
    public static final String PGSQL = "PGSQL";


    public String getDriveClassName(Environment env) {
        return env.getRequiredProperty("datasource.driver");
    }

    public String getUrl(Environment env) {
        return env.getRequiredProperty("datasource.url");
    }

    public String getUsername(Environment env) {
        return env.getRequiredProperty("datasource.username");
    }

    public String getPassword(Environment env) {
        return env.getRequiredProperty("datasource.password");
    }

    public String getValidationQuery(Environment env) {
        return env.getRequiredProperty("datasource.validationQuery");
    }

    public String getDialect(Environment env) {
        return env.getRequiredProperty("datasource.dialect");
    }

    public boolean isHosting(Environment env) {
        return true;
    }
}
