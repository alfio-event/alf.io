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

import alfio.config.support.HeaderPublisherFilter;
import alfio.manager.system.ConfigurationManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.AbstractUrlBasedView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static alfio.config.Initializer.API_V2_PUBLIC_PATH;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;


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

        registry.addResourceHandler("/resources/font/*", alfioVersion + "/resources/font/*")
            .addResourceLocations("classpath:/font/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler( "/resources/images/**", alfioVersion +"/resources/images/**")
            .addResourceLocations("classpath:/images/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler(alfioVersion + "/resources/**")
            .addResourceLocations("classpath:/alfio-admin-v1/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/frontend-public/**")
            .addResourceLocations("classpath:/resources/alfio-public-frontend/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(60)));

        registry.addResourceHandler(alfioVersion + "/frontend-admin/**")
            .addResourceLocations("classpath:/resources/alfio-admin-frontend/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(60)));
    }

    // see https://github.com/spring-projects/spring-session/issues/244#issuecomment-296605144
    @Order(SessionRepositoryFilter.DEFAULT_ORDER - 1)
    public static class ExcludeSessionRepositoryFilter extends OncePerRequestFilter {


        private final RequestMatcher staticContentToIgnore;

        ExcludeSessionRepositoryFilter(String alfioVersion) {
            var methodMatcher = RequestMatchers.anyOf(antMatcher(HttpMethod.GET),
                antMatcher(HttpMethod.HEAD),
                antMatcher(HttpMethod.TRACE),
                antMatcher(HttpMethod.OPTIONS)
            );
            var urlMatcher = RequestMatchers.anyOf(
                antMatcher("/favicon.*"),
                antMatcher("/resources/**"),
                antMatcher(alfioVersion + "/resources/**"),
                antMatcher("/frontend-public/**"),
                antMatcher(alfioVersion + "/frontend-admin/**"),
                antMatcher("/file/**")
            );
            this.staticContentToIgnore = RequestMatchers.allOf(methodMatcher, urlMatcher);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (staticContentToIgnore.matches(httpRequest)) {
                httpRequest.setAttribute("org.springframework.session.web.http.SessionRepositoryFilter.FILTERED", Boolean.TRUE);
            }
            filterChain.doFilter(httpRequest, httpResponse);
        }
    }

    @Bean
    public ExcludeSessionRepositoryFilter excludeSessionRepositoryFilter() {
        return new ExcludeSessionRepositoryFilter(alfioVersion);
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
    public HeaderPublisherFilter headerPublisherFilter(ConfigurationManager configurationManager) {
        return new HeaderPublisherFilter(configurationManager);
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
    public HttpSessionIdResolver httpSessionIdResolver(CookieSerializer cookieSerializer) {
        var publicRequestMatcher = new AntPathRequestMatcher(API_V2_PUBLIC_PATH + "**");
        var headerSessionIdResolver = HeaderHttpSessionIdResolver.xAuthToken();
        var cookieSessionIdResolver = new CookieHttpSessionIdResolver();
        cookieSessionIdResolver.setCookieSerializer(cookieSerializer);
        return new HttpSessionIdResolver() {
            @Override
            public List<String> resolveSessionIds(HttpServletRequest request) {
                if (isPublic(request)) {
                    var resolvedIds = headerSessionIdResolver.resolveSessionIds(request).stream()
                        .filter(StringUtils::isNotBlank).toList();
                    if (!resolvedIds.isEmpty()) {
                        return resolvedIds;
                    }
                }
                return cookieSessionIdResolver.resolveSessionIds(request);
            }

            @Override
            public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
                if (isPublic(request)) {
                    headerSessionIdResolver.setSessionId(request, response, sessionId);
                } else {
                    cookieSessionIdResolver.setSessionId(request, response, sessionId);
                }
            }

            @Override
            public void expireSession(HttpServletRequest request, HttpServletResponse response) {
                if (isPublic(request)) {
                    headerSessionIdResolver.expireSession(request, response);
                } else {
                    cookieSessionIdResolver.expireSession(request, response);
                }
            }

            private boolean isPublic(HttpServletRequest request) {
                return publicRequestMatcher.matches(request);
            }
        };
    }
}
