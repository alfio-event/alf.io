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
package alfio.controller.api.v2.user.model;

import alfio.model.result.ValidationResult;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class ValidatedResponse<T> {
    private final ValidationResult validationResult;
    private final T value;


    public static <T> ValidatedResponse<T> toResponse(BindingResult bindingResult, T value) {

        var transformed = bindingResult.getAllErrors().stream().map(objectError -> {
            if (objectError instanceof FieldError) {
                var fe = (FieldError) objectError;
                return new ValidationResult.ErrorDescriptor(fe.getField(), "", fe.getCode());
            } else {
                return new ValidationResult.ErrorDescriptor(objectError.getObjectName(), "", objectError.getCode());
            }
        }).collect(Collectors.toList());

        return new ValidatedResponse<>(ValidationResult.failed(transformed), value);
    }

    public boolean isSuccess() {
        return validationResult.isSuccess();
    }

    public List<ValidationResult.ErrorDescriptor> getValidationErrors() {
        return validationResult.getValidationErrors();
    }

    public int getErrorCount() {
        return validationResult.getErrorCount();
    }

    public T getValue() {
        return value;
    }
}
