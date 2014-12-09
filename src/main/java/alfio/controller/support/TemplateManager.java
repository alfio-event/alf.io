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
package alfio.controller.support;

import alfio.config.WebSecurityConfig;
import alfio.util.MustacheCustomTagInterceptor;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.AbstractFileResolvingResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;
import org.springframework.web.servlet.view.mustache.jmustache.LocalizationMessageInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * For hiding the uglyness :)
 * */
public class TemplateManager {

	private final LocalizationMessageInterceptor localizationMessageInterceptor;
	private final boolean cache;
	private Map<String, Template> templateCache = new ConcurrentHashMap<>(5); // 1 pdf, 2 email confirmation, 2 email
																				// ticket

	private final Compiler templateCompiler;

	@Autowired
	public TemplateManager(LocalizationMessageInterceptor localizationMessageInterceptor,
						   Environment environment,
						   JMustacheTemplateLoader templateLoader) {
		this.localizationMessageInterceptor = localizationMessageInterceptor;
		this.cache = environment.acceptsProfiles("!dev");
		this.templateCompiler = Mustache.compiler()
				.escapeHTML(false)
				.standardsMode(false)
				.defaultValue("")
				.nullValue("")
				.withFormatter(
						(o) -> {
							return (o instanceof ZonedDateTime) ? DateTimeFormatter.ISO_ZONED_DATE_TIME
									.format((ZonedDateTime) o) : String.valueOf(o);
						})
				.withLoader(templateLoader);
	}

	public String render(String classPathResource, Map<String, Object> model, HttpServletRequest request) {
		return render(new ClassPathResource(classPathResource), classPathResource, model, request);
	}

	public String renderServletContextResource(String servletContextResource, Map<String, Object> model, HttpServletRequest request) {
		model.put("request", request);
		model.put(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
		return render(new ServletContextResource(request.getServletContext(), servletContextResource), servletContextResource, model, request);
	}

	private String render(AbstractFileResolvingResource resource, String key, Map<String, Object> model, HttpServletRequest request) {
		try {
			ModelAndView mv = new ModelAndView((String) null, model);
			mv.addObject("format-date", MustacheCustomTagInterceptor.FORMAT_DATE);
			localizationMessageInterceptor.postHandle(request, null, null, mv);
			Template tmpl = cache ? templateCache.computeIfAbsent(key, k -> compile(resource))
					: compile(resource);
			return tmpl.execute(mv.getModel());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Template compile(AbstractFileResolvingResource resource) {
		try {
			InputStreamReader tmpl = new InputStreamReader(resource.getInputStream(),
					StandardCharsets.UTF_8);
			return templateCompiler.compile(tmpl);
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}
	}
}
