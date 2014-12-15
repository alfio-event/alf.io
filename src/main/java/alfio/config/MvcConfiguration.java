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

import alfio.controller.support.TemplateManager;
import alfio.util.MustacheCustomTagInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.samskivert.mustache.Mustache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.view.mustache.MustacheViewResolver;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateFactory;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;
import org.springframework.web.servlet.view.mustache.jmustache.LocalizationMessageInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@ComponentScan(basePackages = {"alfio.controller", "alfio.config"})
@EnableWebMvc
public class MvcConfiguration extends WebMvcConfigurerAdapter implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Autowired
    private MessageSource messageSource;

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
        registry.addInterceptor(new MustacheCustomTagInterceptor());
        registry.addInterceptor(getCsrfInterceptor());
        registry.addInterceptor(getCSPInterceptor());
        registry.addInterceptor(getLocaleChangeInterceptor());
        registry.addInterceptor(new HandlerInterceptorAdapter() {
    		@Override
    		public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
    				ModelAndView modelAndView) throws Exception {
    			Optional.ofNullable(modelAndView).ifPresent(mv -> {
                    mv.addObject("request", request);
                   	final ModelMap modelMap = mv.getModelMap();
                   	modelMap.putIfAbsent("event", null);
                   	if(!StringUtils.startsWith(mv.getViewName(), "redirect:")) {
                    	modelMap.putIfAbsent("pageTitle", "empty");
                    }
                });
    		}
		});
    }

    @Bean
    public HandlerInterceptor getLocaleChangeInterceptor(){
        LocaleChangeInterceptor localeChangeInterceptor= new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("lang");
        return localeChangeInterceptor;
    }

    private HandlerInterceptor getCSPInterceptor() {
    	return new HandlerInterceptorAdapter() {
    		@Override
    		public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
    				ModelAndView modelAndView) throws Exception {
    			// http://www.html5rocks.com/en/tutorials/security/content-security-policy/
    			// lockdown policy
    			response.addHeader("Content-Security-Policy", "default-src 'none'; "
    					+ " script-src 'self' https://ajax.googleapis.com/ https://js.stripe.com/ https://api.stripe.com/ https://ssl.google-analytics.com/;"//
    					+ " style-src 'self';"//
    					+ " img-src 'self' https://maps.googleapis.com http://tyler-demo.herokuapp.com/;" //http://tyler-demo.herokuapp.com/ = maps for local dev mode
    					+ " font-src 'self';"//
    					+ " connect-src 'self' https://api.stripe.com;"); //<- currently stripe.js but we never know with future updates...
    		}
    	};
	}
    

	@Bean
    public HandlerInterceptor getCsrfInterceptor() {
        return new HandlerInterceptorAdapter() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                Optional.ofNullable(modelAndView).ifPresent(mv -> mv.addObject(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName())));
            }
        };
    }

    @Bean
    public LocalizationMessageInterceptor getTemplateMessagesInterceptor() {
        LocalizationMessageInterceptor interceptor = new LocalizationMessageInterceptor();
        interceptor.setLocaleResolver(getLocaleResolver());
        interceptor.setMessageSource(messageSource);
        return interceptor;
    }

    @Bean(name = "localeResolver")
    public LocaleResolver getLocaleResolver() {
        return new SessionLocaleResolver();
    }

    @Bean
    public ViewResolver getViewResolver(Environment env) throws Exception {
        MustacheViewResolver viewResolver = new MustacheViewResolver();
        viewResolver.setSuffix("");
        viewResolver.setTemplateFactory(getTemplateFactory());
        viewResolver.setOrder(1);
        //disable caching if we are in dev mode
        viewResolver.setCache(env.acceptsProfiles("!dev"));
        viewResolver.setContentType("text/html;charset=UTF-8");
        return viewResolver;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(jacksonMessageConverter());
        StringHttpMessageConverter converter = new StringHttpMessageConverter();
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.ALL));
        converters.add(converter);
    }

    @Bean
    public JMustacheTemplateFactory getTemplateFactory() throws Exception {
        final JMustacheTemplateFactory templateFactory = new JMustacheTemplateFactory();

        JMustacheTemplateLoader loader = getTemplateLoader();
        
        templateFactory.setPrefix("/WEB-INF/templates");
        templateFactory.setSuffix(".ms");
        templateFactory.setTemplateLoader(loader);
        templateFactory.setCompiler(Mustache.compiler()
        		.escapeHTML(true)
        		.standardsMode(false)
        		.defaultValue("")
        		.nullValue("")
				.withFormatter(
						(o) -> {
							if(o instanceof ZonedDateTime) {
								return DateTimeFormatter.ISO_ZONED_DATE_TIME.format((ZonedDateTime) o);
							} else if(o instanceof DefaultMessageSourceResolvable) {
								DefaultMessageSourceResolvable m = ((DefaultMessageSourceResolvable) o);
								return m.getCode()+ " " + Arrays.stream(Optional.ofNullable(m.getArguments()).orElse(new Object[]{})).map(x -> "["+x.toString()+"]").collect(Collectors.joining(" "));
							} else {
								return String.valueOf(o);
							}
						})
        		.withLoader(loader));
        
        templateFactory.afterPropertiesSet();
        return templateFactory;
    }

    @Bean
    public JMustacheTemplateLoader getTemplateLoader() {
        JMustacheTemplateLoader loader = new JMustacheTemplateLoader();
        loader.setResourceLoader(resourceLoader);
        return loader;
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
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
    
    @Bean
    public TemplateManager getTemplateManager(LocalizationMessageInterceptor localizationMessageInterceptor, Environment environment) {
    	return new TemplateManager(localizationMessageInterceptor, environment, getTemplateLoader());
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public static class JavascriptMessageConverter extends AbstractHttpMessageConverter<String> {

        protected JavascriptMessageConverter() {
            super(new MediaType("application", "javascript", Charset.forName("UTF-8")));
        }

        @Override
        protected boolean supports(Class<?> clazz) {
            return String.class.isAssignableFrom(clazz);
        }

        @Override
        protected String readInternal(Class<? extends String> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
            return null;
        }

        @Override
        protected void writeInternal(String s, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
            outputMessage.getBody().write(s.getBytes(Charset.forName("UTF-8")));
        }
    }
}
