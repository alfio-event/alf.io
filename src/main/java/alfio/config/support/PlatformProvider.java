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

import org.apache.tomcat.jdbc.pool.PoolProperties;
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
 * - ElephantDB: runs on Openshift and Cloud Foundry
 * - Cloud Foundry: postgres, mysql (injected)
 * - Heroku
 * - local use with system properties
 */
public enum PlatformProvider {
    DEFAULT,

    //see
    // https://developers.openshift.com/external-services/elephantsql.html
    // http://docs.run.pivotal.io/marketplace/services/elephantsql.html
    ELEPHANTSQL {
        @Override
        public String getDriveClassName(Environment env) {
            return POSTGRESQL_DRIVER;
        }

        @Override
        public String getUrl(Environment env) {
            if(isCloudFoundry(env)) {
                return "";
            }
            URI uri = resolveURI(env, "ELEPHANTSQL_URI");
            return String.format("%s://%s:%s%s", "jdbc:postgresql", uri.getHost(), uri.getPort(), uri.getPath());
        }

        @Override
        public String getUsername(Environment env) {
            return isCloudFoundry(env) ? "" : resolveURI(env, "ELEPHANTSQL_URI").getUserInfo().split(":")[0];
        }


        @Override
        public String getPassword(Environment env) {
            return isCloudFoundry(env) ? "" : resolveURI(env, "ELEPHANTSQL_URI").getUserInfo().split(":")[1];
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
            return ofNullable(env.getProperty("ELEPHANTSQL_URI")).isPresent() || ofNullable(env.getProperty("VCAP_SERVICES")).filter(s -> s.contains("elephantsql")).isPresent();
        }

        @Override
        public int getMaxConnections(Environment env) {
            return ofNullable(env.getProperty("ELEPHANTSQL_MAX_CONNS")).map(Integer::parseInt).orElseGet(() -> super.getMaxConnections(env));
        }
    },

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
     * We assume that either the "MySql" service or "ElephantSql" have already been bound to the application.
     * Anyway, since we use Spring, the Cloud Foundry engine should replace the "DataSource" bean with the right one.
     */
    CLOUD_FOUNDRY {

        @Override
        public String getDriveClassName(Environment env) {
            return isMySql(env) ? MYSQL_DRIVER : POSTGRESQL_DRIVER;
        }

        @Override
        public String getUrl(Environment env) { return ""; }

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
            return isMySql(env) ? MYSQL : PGSQL;
        }

        @Override
        public boolean isHosting(Environment env) {
            //check if json object for services is returned
            //example payload
            //{
            //"staging_env_json": {},
            //"running_env_json": {},
            //"system_env_json": {
            //    "VCAP_SERVICES": {
            //        "p-mysql": [
            //        {
            //            "name": "alfio-db",
            return env.getProperty("VCAP_SERVICES") != null;
        }

        private boolean isMySql(Environment env) {
            return ofNullable(env.getProperty("VCAP_SERVICES")).map(props -> props.contains("mysql://")).orElse(false);
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
            return resolveURI(env, "DATABASE_URL");
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

    private static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";
    public static final String MYSQL = "MYSQL";


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

    public int getMaxConnections(Environment env) {
        return PoolProperties.DEFAULT_MAX_ACTIVE;
    }

    public boolean isHosting(Environment env) {
        return true;
    }

    static URI resolveURI(Environment env, String propertyName) {
        try {
            return new URI(env.getRequiredProperty(propertyName));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static boolean isCloudFoundry(Environment env) {
        return env.getProperty("VCAP_SERVICES") != null;
    }
}
