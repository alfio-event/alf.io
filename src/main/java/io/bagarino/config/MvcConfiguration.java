/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import org.springframework.context.MessageSource;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.view.mustache.MustacheViewResolver;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateFactory;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;
import org.springframework.web.servlet.view.mustache.jmustache.LocalizationMessageInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Configuration
@ComponentScan(basePackages = "io.bagarino.controller")
@EnableWebMvc
public class MvcConfiguration extends WebMvcConfigurerAdapter implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin/").setViewName("/admin/index");
        registry.addViewController("/admin/partials/index.html").setViewName("/admin/partials/main");
        registry.addViewController("/admin/partials/main/organizations.html").setViewName("/admin/partials/organizations");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getTemplateMessagesInterceptor());
        registry.addInterceptor(getCsrfInterceptor());
    }

    @Bean
    public HandlerInterceptor getCsrfInterceptor() {
        return new HandlerInterceptorAdapter() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                Optional.ofNullable(modelAndView).ifPresent(mv -> {
                    HttpSession session = request.getSession();
                    mv.addObject("_csrf", session.getAttribute(WebSecurityConfig.CSRF_SESSION_ATTRIBUTE));
                });
            }
        };
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("io.bagarino.i18n.application", "io.bagarino.i18n.admin");
        return source;
    }

    @Bean
    public LocalizationMessageInterceptor getTemplateMessagesInterceptor() {
        LocalizationMessageInterceptor interceptor = new LocalizationMessageInterceptor();
        interceptor.setLocaleResolver(getLocaleResolver());
        interceptor.setMessageSource(messageSource());
        return interceptor;
    }

    @Bean
    public LocaleResolver getLocaleResolver() {
        SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        localeResolver.setDefaultLocale(Locale.ENGLISH);
        return localeResolver;
    }

    @Bean
    public ViewResolver getViewResolver() throws Exception {
        MustacheViewResolver viewResolver = new MustacheViewResolver();
        viewResolver.setSuffix("");
        viewResolver.setTemplateFactory(getTemplateFactory());
        viewResolver.setOrder(1);
        return viewResolver;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter converter = new StringHttpMessageConverter();
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.TEXT_HTML));
        converters.add(jacksonMessageConverter());
    }

    @Bean
    public JMustacheTemplateFactory getTemplateFactory() throws Exception {
        final JMustacheTemplateFactory templateFactory = new JMustacheTemplateFactory();
        templateFactory.setPrefix("/WEB-INF/templates");
        templateFactory.setSuffix(".ms");
        templateFactory.setEscapeHTML(true);
        templateFactory.setStandardsMode(false);
        templateFactory.setTemplateLoader(getTemplateLoader());
        templateFactory.afterPropertiesSet();
        return templateFactory;
    }

    @Bean
    public JMustacheTemplateLoader getTemplateLoader() {
        final JMustacheTemplateLoader templateLoader = new JMustacheTemplateLoader();
        templateLoader.setResourceLoader(resourceLoader);
        return templateLoader;
    }

    @Bean
    public MappingJackson2HttpMessageConverter jacksonMessageConverter() {
        final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JSR310Module());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
