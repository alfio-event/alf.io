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

import alfio.model.result.ValidationResult.ErrorDescriptor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.validation.ObjectError;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public class Result<T> {

    private final ResultStatus status;
    private final T data;
    private final List<ErrorCode> errors;

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultStatus.OK, data, Collections.emptyList());
    }

    public static <T> Result<T> validationError(List<ObjectError> errors) {
        return new Result<>(ResultStatus.VALIDATION_ERROR, null, errors.stream().map(ErrorDescriptor::fromObjectError).collect(Collectors.toList()));
    }

    public static <T> Result<T> error(List<ErrorCode> errorDescriptors) {
        return new Result<>(ResultStatus.ERROR, null, errorDescriptors);
    }

    public static <T> Result<T> error(ErrorCode errorDescriptor) {
        return error(Collections.singletonList(errorDescriptor));
    }

    public boolean isSuccess() {
        return status == ResultStatus.OK;
    }

    public void ifSuccess(Consumer<T> consumer) {
        if(isSuccess()) {
            consumer.accept(data);
        }
    }

    public <K> Result<K> map(Function<T, K> mapper) {
        if(isSuccess()) {
            return Result.success(mapper.apply(data));
        }
        return Result.error(this.errors);
    }

    public <K> Result<K> flatMap(Function<T, Result<K>> mapper) {
        if(isSuccess()) {
            return Objects.requireNonNull(mapper.apply(data), "this method does not allow null values");
        }
        return Result.error(this.errors);
    }

    public ErrorCode getFirstErrorOrNull() {
        if(isSuccess() || CollectionUtils.size(errors) == 0) {
            return null;
        }
        return errors.get(0);
    }

    public String getFormattedErrors() {
        if(isSuccess()) {
            return null;
        }
        return getErrors().stream().map(ErrorCode::getDescription)
            .collect(Collectors.joining("\n"));
    }

    public enum ResultStatus {
        OK, VALIDATION_ERROR, ERROR
    }

    public static final class Builder<T> {
        private final List<Pair<ConditionValidator, ErrorCode>> validators = new ArrayList<>();

        public Builder<T> checkPrecondition(ConditionValidator validator, ErrorCode error) {
            this.validators.add(Pair.of(validator, error));
            return this;
        }

        public Result<T> build(Supplier<T> valueSupplier) {
            Optional<Pair<ConditionValidator, ErrorCode>> validationError = performValidation();
            return validationError.map(p -> Result.<T>error(Collections.singletonList(p.getRight())))
                .orElseGet(() -> Result.success(valueSupplier.get()));
        }

        public Result<T> buildAndEvaluate(Supplier<Result<T>> resultSupplier) {
            return performValidation().map(p -> Result.<T>error(Collections.singletonList(p.getRight())))
                .orElseGet(resultSupplier);
        }

        private Optional<Pair<ConditionValidator, ErrorCode>> performValidation() {
            return this.validators.stream()
                .filter(p -> !p.getLeft().isValid())
                .findFirst();
        }

    }

    public static <T> Result<T> fromNullable(T nullable, ErrorCode justInCase) {
        if(nullable == null) {
            return Result.error(justInCase);
        }
        return Result.success(nullable);
    }

    @FunctionalInterface
    public interface ConditionValidator {
        boolean isValid();
    }
}
