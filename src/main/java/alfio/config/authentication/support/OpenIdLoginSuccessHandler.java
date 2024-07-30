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
package alfio.config.authentication.support;

import alfio.config.support.ContextAwareCookieSerializer;
import alfio.util.TemplateManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.session.web.http.CookieSerializer;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public class OpenIdLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final TemplateManager templateManager;
    private final ContextAwareCookieSerializer cookieSerializer;

    public OpenIdLoginSuccessHandler(TemplateManager templateManager, ContextAwareCookieSerializer cookieSerializer) {
        this.templateManager = templateManager;
        this.cookieSerializer = cookieSerializer;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String targetPath;
        var savedRequest = this.requestCache.getRequest(request, response);
        if (savedRequest == null || StringUtils.isBlank(savedRequest.getRedirectUrl())) {
            targetPath = "/";
        } else {
            targetPath = URI.create(savedRequest.getRedirectUrl()).getPath();
        }
        clearAuthenticationAttributes(request);
        cookieSerializer.writePreAuthCookieValue(new CookieSerializer.CookieValue(request, response, ""));
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        templateManager.renderHtml(
            new ClassPathResource("/alfio/templates/openid-redirect.ms"),
            Map.of("redirectPath", targetPath),
            response.getWriter()
        );
        response.flushBuffer();
    }
}
