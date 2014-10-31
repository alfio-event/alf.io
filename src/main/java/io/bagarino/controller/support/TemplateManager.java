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
package io.bagarino.controller.support;

import io.bagarino.util.MustacheCustomTagInterceptor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.mustache.jmustache.LocalizationMessageInterceptor;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;

/**
 * For hiding the uglyness :)
 * */
public class TemplateManager {

	private final LocalizationMessageInterceptor localizationMessageInterceptor;
	private final boolean cache;
	private Map<String, Template> templateCache = new ConcurrentHashMap<>(5); // 1 pdf, 2 email confirmation, 2 email
																				// ticket

	private final Compiler templateCompiler = Mustache
			.compiler()
			.escapeHTML(false)
			.withFormatter(
					(o) -> {
						return (o instanceof ZonedDateTime) ? DateTimeFormatter.ISO_ZONED_DATE_TIME
								.format((ZonedDateTime) o) : String.valueOf(o);
					});

	@Autowired
	public TemplateManager(LocalizationMessageInterceptor localizationMessageInterceptor, Environment environment) {
		this.localizationMessageInterceptor = localizationMessageInterceptor;
		this.cache = environment.acceptsProfiles("!dev");
	}

	public String render(String classPathResource, Map<String, Object> model, HttpServletRequest request) {
		try {
			ModelAndView mv = new ModelAndView((String) null, model);

			//
			mv.addObject("format-date", MustacheCustomTagInterceptor.FORMAT_DATE);
			localizationMessageInterceptor.postHandle(request, null, null, mv);
			//

			Template tmpl = cache ? templateCache.computeIfAbsent(classPathResource, this::compile)
					: compile(classPathResource);

			return tmpl.execute(mv.getModel());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Template compile(String classPathResource) {
		try {
			InputStreamReader tmpl = new InputStreamReader(new ClassPathResource(classPathResource).getInputStream(),
					StandardCharsets.UTF_8);
			return templateCompiler.compile(tmpl);
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}
	}
}
