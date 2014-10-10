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
package io.bagarino.util;

import static org.apache.commons.lang3.StringUtils.substring;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.samskivert.mustache.Mustache;

/**
 * For formatting date in a mustache template.
 * 
 * <p>Syntax is: {{#format-date}}{{yourDate}} FORMAT timezone:YOUR_TIMEZONE locale:YOUR_LOCALE{{/format-date}}.</p>
 * <p>Where </p>
 * <ul>
 * 	<li>yourDate has been formatted following the ISO8601 format for date-time with time zone (yyyy-MM-dd'T'HH:mm:ssZZ).</li>
 *  <li>FORMAT is a format understood by {@link SimpleDateFormat}</li>
 *  <li>optional: timezone:YOUR_TIMEZONE you can define the timezone</li>
 *  <li>optional: locale:YOUR_LOCALE you can define the locale</li>
 * </ul>
 */
//TODO: not happy about that, but meh, it's too late
public class DateFormatterInterceptor extends HandlerInterceptorAdapter {
	
	private static final String TZ_LABEL = "timezone:";
	private static final String LOCALE_LABEL = "locale:";
	
	private static Triple<String, TimeZone, Locale> parseParams(String r) {
		
		int indexTZ = r.indexOf(TZ_LABEL), indexLocale = r.indexOf(LOCALE_LABEL), end = Math.min(indexTZ != -1 ? indexTZ : r.length(), indexLocale != - 1 ? indexLocale : r.length());
		String format = substring(r, r.indexOf(" "), end);
		
		//
		String[] res = r.split("\\s+");
		Optional<TimeZone> tz = Arrays.asList(res).stream().filter((s) -> s.startsWith(TZ_LABEL)).findFirst().map((t) -> {return TimeZone.getTimeZone(substring(t, TZ_LABEL.length()));});
		Optional<Locale> locale = Arrays.asList(res).stream().filter((s) -> s.startsWith(LOCALE_LABEL)).findFirst().map((l) -> {return Locale.forLanguageTag(substring(l, LOCALE_LABEL.length()));});
		//
		
		return Triple.of(format, tz.orElse(null), locale.orElse(null));
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

		if (modelAndView != null) {
			Mustache.Lambda formatDate = (frag, out) -> {
				try {
					String execution = frag.execute().trim();
					Date d = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.parse(substring(execution, 0, execution.indexOf(" ")));
					Triple<String, TimeZone, Locale> p = parseParams(execution);
					out.write(DateFormatUtils.format(d, p.getLeft(), p.getMiddle(), p.getRight()));
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
			};
			modelAndView.addObject("format-date", formatDate);
		}
		
		super.postHandle(request, response, handler, modelAndView);
	}
}
