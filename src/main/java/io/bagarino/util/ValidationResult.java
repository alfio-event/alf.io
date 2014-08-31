package io.bagarino.util;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
@Getter
public final class ValidationResult {

    private static final ValidationResult SUCCESS = new ValidationResult(Collections.<ValidationError>emptyList());

    private final List<ValidationError> validationErrors;
    private final int errorCount;

    private ValidationResult(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
        this.errorCount = validationErrors.size();
    }

    public static ValidationResult success() {
        return SUCCESS;
    }

    public static ValidationResult failed(ValidationError... errors) {
        return new ValidationResult(Arrays.asList(errors));
    }
    @Getter
    public static final class ValidationError {
        private final String fieldName;
        private final String message;

        public ValidationError(String fieldName, String message) {
            this.fieldName = fieldName;
            this.message = message;
        }
    }
}

