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
package alfio.model.result;

import lombok.Getter;
import lombok.ToString;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
public final class ValidationResult {

    private static final ValidationResult SUCCESS = new ValidationResult(Collections.emptyList());

    private final List<ErrorDescriptor> errorDescriptors;
    private final int errorCount;

    private ValidationResult(List<ErrorDescriptor> errorDescriptors) {
        this.errorDescriptors = errorDescriptors;
        this.errorCount = errorDescriptors.size();
    }

    public List<ErrorDescriptor> getValidationErrors() {
        return getErrorDescriptors();
    }

    public static ValidationResult success() {
        return SUCCESS;
    }

    public static ValidationResult failed(List<ErrorDescriptor> errors) {
        return new ValidationResult(errors);
    }

    public static ValidationResult failed(ErrorDescriptor... errors) {
        return failed(Arrays.asList(errors));
    }

    public static ValidationResult of(List<ErrorDescriptor> errors) {
        if(errors.size() > 0) {
            return failed(errors);
        }
        return success();
    }

    public ValidationResult ifSuccess(Operation operation) {
        if(errorCount == 0) {
            operation.doIt();
        }
        return this;
    }

    public ValidationResult or(ValidationResult second) {
        if(!isSuccess()) {
            List<ErrorDescriptor> joined = new ArrayList<>();
            joined.addAll(errorDescriptors);
            joined.addAll(second.getErrorDescriptors());
            return new ValidationResult(joined);
        }
        return second;
    }

    public boolean isSuccess() {
        return errorCount == 0;
    }

    @Getter
    @ToString
    public static final class ErrorDescriptor implements ErrorCode {
        private final String fieldName;
        private final String message;
        private final String code;

        public ErrorDescriptor(String fieldName, String message) {
            this(fieldName, message, null);
        }

        public ErrorDescriptor(String fieldName, String message, String code) {
            this.fieldName = fieldName;
            this.message = message;
            this.code = code;
        }

        @Override
        public String getLocation() {
            return fieldName;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getDescription() {
            return message;
        }

        public static ErrorDescriptor fromFieldError(FieldError fieldError) {
            return new ErrorDescriptor(fieldError.getField(), "", fieldError.getCode());
        }

        public static ErrorDescriptor fromObjectError(ObjectError objectError) {
            return new ErrorDescriptor("", objectError.getObjectName());
        }
    }

    @FunctionalInterface
    public interface Operation {
        void doIt();
    }
}

