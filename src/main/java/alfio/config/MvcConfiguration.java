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
package alfio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.AbstractUrlBasedView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static alfio.config.Initializer.API_V2_PUBLIC_PATH;


@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"alfio.controller", "alfio.config"})
@EnableWebMvc
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 4 * 60 * 60, tableName = "ALFIO_SPRING_SESSION") //4h
public class MvcConfiguration implements WebMvcConfigurer {

    private final Environment environment;
    private final String alfioVersion;
    private final ObjectMapper objectMapper;

    public MvcConfiguration(Environment environment,
                            @Value("${alfio.version}") String alfioVersion,
                            ObjectMapper objectMapper) {
        this.environment = environment;
        this.alfioVersion = alfioVersion;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        boolean isLive = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE));
        int cacheMinutes = isLive ? 15 : 0;

        var defaultCacheControl = CacheControl.maxAge(Duration.ofDays(isLive ? 10 : 0)).mustRevalidate();

        registry.addResourceHandler(alfioVersion + "/resources/**")
            .addResourceLocations("/resources/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/resources/font/*")
            .addResourceLocations("/resources/font/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/resources/images/**")
            .addResourceLocations("/resources/images/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/frontend-public/**")
            .addResourceLocations("/resources/alfio-public-frontend/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(60)));

        registry.addResourceHandler("/frontend-admin/**")
            .addResourceLocations("/resources/alfio-admin-frontend/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(60)));

    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(jacksonMessageConverter(objectMapper));
        StringHttpMessageConverter converter = new StringHttpMessageConverter();
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.ALL));
        converters.add(converter);
    }

    private MappingJackson2HttpMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    @Bean
    public CommonsMultipartResolver multipartResolver() {
        var multipartResolver = new CommonsMultipartResolver();
        multipartResolver.setMaxUploadSize(500_000); // 500kB
        return multipartResolver;
    }

    @Bean
    public ViewResolver viewResolver() {
        var resolver = new UrlBasedViewResolver();
        resolver.setViewClass(AbstractUrlBasedView.class);
        return resolver;
    }

    @Bean
    public SpringSessionBackedSessionRegistry<?> sessionRegistry(FindByIndexNameSessionRepository<?> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        var publicSessionIdResolver = HeaderHttpSessionIdResolver.xAuthToken();
        var adminSessionIdResolver = new CookieHttpSessionIdResolver();
        return new HttpSessionIdResolver() {
            @Override
            public List<String> resolveSessionIds(HttpServletRequest request) {
                return isPublic(request) ? publicSessionIdResolver.resolveSessionIds(request)
                    : adminSessionIdResolver.resolveSessionIds(request);
            }

            @Override
            public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
                if (isPublic(request)) {
                    publicSessionIdResolver.setSessionId(request, response, sessionId);
                } else {
                    adminSessionIdResolver.setSessionId(request, response, sessionId);
                }
            }

            @Override
            public void expireSession(HttpServletRequest request, HttpServletResponse response) {
                if (isPublic(request)) {
                    publicSessionIdResolver.expireSession(request, response);
                } else {
                    adminSessionIdResolver.expireSession(request, response);
                }
            }

            private static boolean isPublic(HttpServletRequest request) {
                var requestURI = request.getRequestURI();
                return requestURI.startsWith(API_V2_PUBLIC_PATH);
            }
        };
    }
}
