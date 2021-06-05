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

    private static final ValidationResult SUCCESS = new ValidationResult(Collections.emptyList(), Collections.emptyList());

    private final List<ErrorDescriptor> errorDescriptors;
    private final int errorCount;
    private final List<WarningMessage> warnings;

    private ValidationResult(List<ErrorDescriptor> errorDescriptors, List<WarningMessage> warnings) {
        this.errorDescriptors = errorDescriptors;
        this.errorCount = errorDescriptors.size();
        this.warnings = warnings;
    }

    public List<ErrorDescriptor> getValidationErrors() {
        return getErrorDescriptors();
    }

    public static ValidationResult success() {
        return SUCCESS;
    }

    public static ValidationResult failed(List<ErrorDescriptor> errors) {
        return new ValidationResult(errors, List.of());
    }

    public static ValidationResult failed(List<ErrorDescriptor> errors, List<WarningMessage> warnings) {
        return new ValidationResult(errors, warnings);
    }

    public static ValidationResult failed(ErrorDescriptor... errors) {
        return failed(Arrays.asList(errors));
    }

    public static ValidationResult of(List<ErrorDescriptor> errors) {
        return errors.isEmpty() ? success() : failed(errors);
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
            List<WarningMessage> allWarnings = new ArrayList<>(warnings);
            allWarnings.addAll(second.getWarnings());
            return new ValidationResult(joined, allWarnings);
        }
        return second;
    }

    public boolean isSuccess() {
        return errorCount == 0;
    }

    @ToString
    @Getter
    public static final class ErrorDescriptor implements ErrorCode {
        private final String fieldName;
        private final String message;
        private final String code;
        private final Object[] arguments;

        public ErrorDescriptor(String fieldName, String message) {
            this(fieldName, message, null, null);
        }

        public ErrorDescriptor(String fieldName, String message, String code) {
            this(fieldName, message, code, null);
        }

        public ErrorDescriptor(String fieldName, String message, String code, Object[] arguments) {
            this.fieldName = fieldName;
            this.message = message;
            this.code = code;
            this.arguments = arguments;
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

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        public static ErrorDescriptor fromFieldError(FieldError fieldError) {
            return new ErrorDescriptor(fieldError.getField(), "", fieldError.getCode(), fieldError.getArguments());
        }

        public static ErrorDescriptor fromObjectError(ObjectError objectError) {
            return new ErrorDescriptor("", objectError.getObjectName(), objectError.getCode(), objectError.getArguments());
        }
    }

    @FunctionalInterface
    public interface Operation {
        void doIt();
    }
}

