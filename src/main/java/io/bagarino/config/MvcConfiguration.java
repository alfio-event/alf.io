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

import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.view.mustache.MustacheViewResolver;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateFactory;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;

@ComponentScan(basePackages = "io.bagarino")
@EnableWebMvc
public class MvcConfiguration extends WebMvcConfigurerAdapter implements ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
	}

	@Bean
	public ViewResolver getViewResolver() throws Exception {
		MustacheViewResolver viewResolver = new MustacheViewResolver();
		viewResolver.setSuffix("");
		viewResolver.setTemplateFactory(getTemplateFactory());
		return viewResolver;
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

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}
}
