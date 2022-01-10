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
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.AbstractUrlBasedView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"alfio.controller", "alfio.config"})
@EnableWebMvc
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 4 * 60 * 60) //4h
public class MvcConfiguration implements WebMvcConfigurer {

    private final Environment environment;
    private final String frontendVersion;
    private final String alfioVersion;
    private final ObjectMapper objectMapper;

    public MvcConfiguration(Environment environment,
                            @Value("${alfio.frontend.version}") String frontendVersion,
                            @Value("${alfio.version}") String alfioVersion,
                            ObjectMapper objectMapper) {
        this.environment = environment;
        this.frontendVersion = frontendVersion;
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
        //
        registry
            .addResourceHandler("/webjars/**")
            .addResourceLocations("/webjars/")
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/assets/**")
            .addResourceLocations("/webjars/alfio-public-frontend/" + frontendVersion + "/alfio-public-frontend/assets/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/*.js")
            .addResourceLocations("/webjars/alfio-public-frontend/" + frontendVersion + "/alfio-public-frontend/")
            .setCachePeriod(cacheMinutes * 60)
            .setCacheControl(defaultCacheControl);

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
}
