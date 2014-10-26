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

import io.bagarino.util.DateFormatterInterceptor;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.mustache.jmustache.LocalizationMessageInterceptor;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;

/**
 * For hiding the uglyness :)
 * */
public class TemplateManager {

	private final LocalizationMessageInterceptor localizationMessageInterceptor;

	private final Compiler templateCompiler = Mustache
			.compiler()
			.escapeHTML(false)
			.withFormatter(
					(o) -> {
						return (o instanceof ZonedDateTime) ? DateTimeFormatter.ISO_ZONED_DATE_TIME
								.format((ZonedDateTime) o) : String.valueOf(o);
					});

	@Autowired
	public TemplateManager(LocalizationMessageInterceptor localizationMessageInterceptor) {
		this.localizationMessageInterceptor = localizationMessageInterceptor;
	}

	public String render(String classPathResource, Map<String, Object> model, HttpServletRequest request)
			throws Exception {

		ModelAndView mv = new ModelAndView((String) null, model);

		//
		mv.addObject("format-date", DateFormatterInterceptor.FORMAT_DATE);
		localizationMessageInterceptor.postHandle(request, null, null, mv);
		//

		InputStreamReader tmpl = new InputStreamReader(new ClassPathResource(classPathResource).getInputStream(),
				StandardCharsets.UTF_8);
		return templateCompiler.compile(tmpl).execute(mv.getModel());
	}
}
