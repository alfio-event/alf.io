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
package alfio.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
@Log4j2
public class ControllerExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String exception(Exception ex) {
        log.error("unexpected exception", ex);
        return "/event/500-internal-server-error";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String requestNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Request method {} not allowed for request {}", request.getMethod(), request.getRequestURI());
        return "/event/500-internal-server-error";
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String badRequest(HttpMessageConversionException e) {
        return "bad request";
    }

}
