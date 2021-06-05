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
package alfio.manager.support.response;

import alfio.controller.support.CustomBindingResult;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult;
import alfio.model.result.WarningMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class ValidatedResponse<T> {
    private final ValidationResult validationResult;
    private final T value;


    public static <T> ValidatedResponse<T> toResponse(BindingResult bindingResult, T value) {

        var transformed = bindingResult.getAllErrors().stream().map(objectError -> {
            if (objectError instanceof FieldError) {
                var fe = (FieldError) objectError;
                return new ValidationResult.ErrorDescriptor(fe.getField(), "", fe.getCode(), fe.getArguments());
            } else {
                return new ValidationResult.ErrorDescriptor(objectError.getObjectName(), "", objectError.getCode(), objectError.getArguments());
            }
        }).collect(Collectors.toList());

        List<WarningMessage> warnings = bindingResult instanceof CustomBindingResult ? ((CustomBindingResult)bindingResult).getWarnings() : List.of();
        return new ValidatedResponse<>(ValidationResult.failed(transformed, warnings), value);
    }

    public static <T> ValidatedResponse<T> fromResult(Result<T> result, String objectName) {
        if(result.isSuccess()) {
            return new ValidatedResponse<>(ValidationResult.success(), result.getData());
        }
        var transformed = result.getErrors().stream()
            .map(ec -> new ValidationResult.ErrorDescriptor(objectName, "", ec.getCode()))
            .collect(Collectors.toList());

        return new ValidatedResponse<>(ValidationResult.failed(transformed), null);
    }

    public<R> ValidatedResponse<R> withValue(R value) {
        return new ValidatedResponse<>(validationResult, value);
    }

    public boolean isSuccess() {
        return validationResult.isSuccess();
    }

    public List<ErrorDescriptor> getValidationErrors() {
        return validationResult.getValidationErrors().stream()
            .map(ed -> new ErrorDescriptor(ed.getFieldName(), ed.getCode(), fromArray(ed.getArguments())))
            .collect(Collectors.toList());
    }

    public int getErrorCount() {
        return validationResult.getErrorCount();
    }

    public T getValue() {
        return value;
    }

    public List<WarningMessage> getWarnings() {
        return validationResult.getWarnings();
    }

    private static Map<String, Object> fromArray(Object[] arguments) {

        if(arguments == null || arguments.length == 0) {
            return null;
        } else {
            var res = new HashMap<String, Object>();
            for (int i = 0; i < arguments.length; i++) {
                res.put(Integer.toString(i), arguments[i]);
            }
            return res;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class ErrorDescriptor {
        private final String fieldName;
        private final String code;
        private final Map<String, Object> arguments;
    }
}
