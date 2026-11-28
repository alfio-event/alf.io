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
package alfio.controller.api.admin;

import alfio.manager.i18n.MessageSourceManager;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Locale;

/**
 * Translates the descriptions of the errors returned by the admin API in the language of the admin UI
 * (see MvcConfiguration#localeResolver), using the translation key of each error ({@link ErrorCode#getMessageKey()}).
 * An error without a key, or whose key has no translation, keeps its description.
 */
@RestControllerAdvice(basePackageClasses = AdminErrorTranslationAdvice.class)
public class AdminErrorTranslationAdvice implements ResponseBodyAdvice<Object> {

    private final MessageSource messageSource;

    public AdminErrorTranslationAdvice(MessageSourceManager messageSourceManager) {
        this.messageSource = messageSourceManager.getRootMessageSource(false);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Result<?> result && result.getErrors() != null && !result.getErrors().isEmpty()) {
            return translate(result, LocaleContextHolder.getLocale());
        }
        return body;
    }

    <T> Result<T> translate(Result<T> result, Locale locale) {
        List<ErrorCode> errors = result.getErrors().stream()
            .map(error -> translate(error, locale))
            .toList();
        return new Result<>(result.getStatus(), result.getData(), errors);
    }

    private ErrorCode translate(ErrorCode error, Locale locale) {
        var messageKey = error.getMessageKey();
        if (messageKey == null) {
            return error;
        }
        var description = messageSource.getMessage(messageKey, error.getArguments(), null, locale);
        if (description == null) {
            return error;
        }
        return ErrorCode.withDescription(error, description);
    }
}
