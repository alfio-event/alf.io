package alfio.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Profile(Initializer.PROFILE_JDBC_SESSION)
@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 4 * 60 * 60) //4h
@AllArgsConstructor
public class JdbcSessionConfiguration {
}
