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

import alfio.controller.decorator.EventDescriptor;
import alfio.manager.i18n.I18nManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.system.Configuration.ConfigurationPathKey;
import alfio.util.MustacheCustomTagInterceptor;
import alfio.util.TemplateManager;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.mustache.MustacheViewResolver;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateFactory;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;
import org.springframework.web.servlet.view.mustache.jmustache.LocalizationMessageInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;

@Configuration
@ComponentScan(basePackages = {"alfio.controller", "alfio.config"})
@EnableWebMvc
public class MvcConfiguration extends WebMvcConfigurerAdapter {

    private final MessageSource messageSource;
    private final JMustacheTemplateLoader templateLoader;
    private final I18nManager i18nManager;
    private final ConfigurationManager configurationManager;
    private final Environment environment;

    @Autowired
    public MvcConfiguration(MessageSource messageSource,
                            JMustacheTemplateLoader templateLoader,
                            I18nManager i18nManager,
                            ConfigurationManager configurationManager,
                            Environment environment) {
        this.messageSource = messageSource;
        this.templateLoader = templateLoader;
        this.i18nManager = i18nManager;
        this.configurationManager = configurationManager;
        this.environment = environment;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        ResourceHandlerRegistration reg = registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
        int cacheMinutes = environment.acceptsProfiles(Initializer.PROFILE_LIVE) ? 15 : 0;
        reg.setCachePeriod(cacheMinutes * 60);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin/partials/index.html").setViewName("/admin/partials/main");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getLocaleChangeInterceptor());
        registry.addInterceptor(getEventLocaleSetterInterceptor());
        registry.addInterceptor(getTemplateMessagesInterceptor());
        registry.addInterceptor(new MustacheCustomTagInterceptor());
        registry.addInterceptor(getCsrfInterceptor());
        registry.addInterceptor(getCSPInterceptor());
        registry.addInterceptor(getDefaultTemplateObjectsFiller());
    }

    @Bean
    public HandlerInterceptor getEventLocaleSetterInterceptor() {
        return new HandlerInterceptorAdapter() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

                if(handler instanceof HandlerMethod) {
                    HandlerMethod handlerMethod = ((HandlerMethod) handler);
                    RequestMapping reqMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);

                    //check if the request mapping value has the form "/event/{something}"
                    Pattern eventPattern = Pattern.compile("^/event/\\{(\\w+)}/{0,1}.*");
                    if (reqMapping != null && reqMapping.value().length == 1 && eventPattern.matcher(reqMapping.value()[0]).matches()) {

                        Matcher m = eventPattern.matcher(reqMapping.value()[0]);
                        m.matches();

                        String pathVariableName = m.group(1);

                        //extract the parameter name
                        Arrays.stream(handlerMethod.getMethodParameters())
                            .map(methodParameter -> methodParameter.getParameterAnnotation(PathVariable.class))
                            .filter(Objects::nonNull)
                            .map(PathVariable::value)
                            .filter(pathVariableName::equals)
                            .findFirst().ifPresent((val) -> {

                                //fetch the parameter value
                                @SuppressWarnings("unchecked")
                                String eventName = Optional.ofNullable(((Map<String, Object>)request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).get(val)).orElse("").toString();


                                LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request);
                                Locale locale = resolver.resolveLocale(request);
                                List<ContentLanguage> cl = i18nManager.getEventLanguages(eventName);

                                request.setAttribute("ALFIO_EVENT_NAME", eventName);

                                if(cl.stream().noneMatch(contentLanguage -> contentLanguage.getLanguage().equals(Optional.ofNullable(locale).orElse(Locale.ENGLISH).getLanguage()))) {
                                    //override the user locale if it's not in the one permitted by the event
                                    resolver.setLocale(request, response, cl.stream().findFirst().map(ContentLanguage::getLocale).orElse(Locale.ENGLISH));
                                } else {
                                    resolver.setLocale(request, response, locale);
                                }
                            });
                    }
                }
                return true;
            }
        };
    }

    @Bean
    public HandlerInterceptorAdapter getDefaultTemplateObjectsFiller() {
        return new HandlerInterceptorAdapter() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                Optional.ofNullable(modelAndView)
                    .filter(mv -> !StringUtils.startsWith(mv.getViewName(), "redirect:"))
                    .ifPresent(mv -> {
                        mv.addObject("request", request);
                        final ModelMap modelMap = mv.getModelMap();

                        boolean demoModeEnabled = environment.acceptsProfiles(Initializer.PROFILE_DEMO);

                        modelMap.put("demoModeEnabled", demoModeEnabled);

                        Optional.ofNullable(request.getAttribute("ALFIO_EVENT_NAME")).map(Object::toString).ifPresent(eventName -> {

                            List<?> availableLanguages = i18nManager.getEventLanguages(eventName);

                            modelMap.put("showAvailableLanguagesInPageTop", availableLanguages.size() > 1);
                            modelMap.put("availableLanguages", availableLanguages);
                        });

                        modelMap.putIfAbsent("event", null);
                        modelMap.putIfAbsent("pageTitle", "empty");
                        Event event = modelMap.get("event") == null ? null : modelMap.get("event") instanceof Event ? (Event) modelMap.get("event") : ((EventDescriptor) modelMap.get("event")).getEvent();
                        ConfigurationPathKey googleAnalyticsKey = Optional.ofNullable(event)
                            .map(e -> alfio.model.system.Configuration.from(e.getOrganizationId(), e.getId(), GOOGLE_ANALYTICS_KEY))
                            .orElseGet(() -> alfio.model.system.Configuration.getSystemConfiguration(GOOGLE_ANALYTICS_KEY));
                        modelMap.putIfAbsent("analyticsEnabled", StringUtils.isNotBlank(configurationManager.getStringConfigValue(googleAnalyticsKey, "")));


                        if(demoModeEnabled) {
                            modelMap.putIfAbsent("paypalTestUsername", configurationManager.getStringConfigValue(alfio.model.system.Configuration.getSystemConfiguration(PAYPAL_DEMO_MODE_USERNAME), "<missing>"));
                            modelMap.putIfAbsent("paypalTestPassword", configurationManager.getStringConfigValue(alfio.model.system.Configuration.getSystemConfiguration(PAYPAL_DEMO_MODE_PASSWORD), "<missing>"));
                        }

                        modelMap.putIfAbsent(TemplateManager.VAT_TRANSLATION_TEMPLATE_KEY, TemplateManager.getVATString(event, messageSource, RequestContextUtils.getLocaleResolver(request).resolveLocale(request), configurationManager));
                });
            }
        };
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
                        + " script-src 'self' https://js.stripe.com/ https://api.stripe.com/ https://ssl.google-analytics.com/ https://www.google.com/recaptcha/api.js https://www.gstatic.com/recaptcha/api2/ https://maps.googleapis.com/;"//
                        + " style-src 'self' 'unsafe-inline';" // unsafe-inline for style is acceptable...
                        + " img-src 'self' https: data:;"//
                        + " child-src 'self';"//webworker
                        + " frame-src 'self' https://js.stripe.com https://www.google.com;"
                        + " font-src 'self';"//
                        + " media-src blob: 'self';"//for loading camera api
                        + " connect-src 'self' https://api.stripe.com https://maps.googleapis.com/ https://geocoder.cit.api.here.com;" //<- currently stripe.js use jsonp but if they switch to xmlhttprequest+cors we will be ready
                        + (environment.acceptsProfiles(Initializer.PROFILE_DEBUG_CSP) ? " report-uri /report-csp-violation" : ""));
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
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.ALL));
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
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    @Bean
    public CommonsMultipartResolver multipartResolver() {
        return new CommonsMultipartResolver();
    }


    
}
