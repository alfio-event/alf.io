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

import alfio.util.MustacheCustomTagInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.samskivert.mustache.Mustache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@ComponentScan(basePackages = {"alfio.controller", "alfio.config"})
@EnableWebMvc
public class MvcConfiguration extends WebMvcConfigurerAdapter {

    @Autowired
    private MessageSource messageSource;
    @Autowired
    private JMustacheTemplateLoader templateLoader;

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
    			response.addHeader("Content-Security-Policy", "default-src 'none'; "//block all by default
    					+ " script-src 'self' https://ajax.googleapis.com/ https://js.stripe.com/ https://api.stripe.com/ https://ssl.google-analytics.com/;"//
    					+ " style-src 'self';"//
    					+ " img-src 'self' https: data:;"//
    					+ " child-src 'self';"//webworker
    					+ " font-src 'self';"//
    					+ " media-src 'self';"//for loading camera api
    					+ " connect-src 'self' https://api.stripe.com;"); //<- currently stripe.js use jsonp but if they switch to xmlhttprequest+cors we will be ready
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
        viewResolver.setCache(env.acceptsProfiles(Initializer.PROFILE_LIVE));
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

        templateFactory.setPrefix("/WEB-INF/templates");
        templateFactory.setSuffix(".ms");
        templateFactory.setTemplateLoader(templateLoader);
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
        		.withLoader(templateLoader));
        
        templateFactory.afterPropertiesSet();
        return templateFactory;
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
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }


    
}
