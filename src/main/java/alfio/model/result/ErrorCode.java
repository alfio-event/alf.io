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

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

import java.util.function.Supplier;

/**
 * An error, described for the user.
 * <p>
 * An error can name the translation key of its description ({@link #getMessageKey()}), with {@link #getArguments()}
 * as the values of its placeholders. The admin API translates such descriptions in the language of the user (see
 * alfio.controller.api.admin.AdminErrorTranslationAdvice); elsewhere, the English description is used as it is.
 */
@JsonSerialize(using=ErrorCodeSerializer.class)
public interface ErrorCode {

    String DUPLICATE = "duplicate";
    String CODE_NOT_FOUND = "not_found";

    default String getLocation() {
        return "";
    }

    String getCode();
    String getDescription();
    Object[] getArguments();

    /**
     * The translation key of the description, or null if the description cannot be translated
     */
    @JsonIgnore
    default String getMessageKey() {
        return null;
    }


    @Getter
    enum CategoryError implements ErrorCode {
        NOT_FOUND(CODE_NOT_FOUND, "admin.error.category.not-found", "Category not found"),
        NOT_ENOUGH_SEATS("not_enough_tickets", "admin.error.category.not-enough-seats", "Not enough seats"),
        ALL_TICKETS_ASSIGNED("all_tickets_assigned", "admin.error.category.all-tickets-assigned", "All the tickets have already been assigned to a category. Try increasing the total seats number."),
        EXPIRATION_AFTER_EVENT_END("expiration_after_event_end", "admin.error.category.expiration-after-event-end", "expiration must be before the end of the event"),
        NOT_ENOUGH_FREE_TOKEN_FOR_SHRINK("not_enough_free_token_for_shrink", "admin.error.category.not-enough-free-tokens", "Cannot downsize this category: not enough free token can be removed");

        private final String code;
        private final String messageKey;
        private final String description;

        CategoryError(String code, String messageKey, String description) {
            this.code = code;
            this.messageKey = messageKey;
            this.description = description;
        }


        @Override
        public String toString() {
            return description;
        }

        @Override
        public Object[] getArguments() {
            return null;
        }
    }


    @Getter
    enum EventError implements ErrorCode {
        NOT_FOUND(CODE_NOT_FOUND, "admin.error.event.not-found", "No event has been found"),
        ACCESS_DENIED("access_denied", "admin.error.access-denied", "Access is denied");

        private final String code;
        private final String messageKey;
        private final String description;

        EventError(String code, String messageKey, String description) {
            this.code = code;
            this.messageKey = messageKey;
            this.description = description;
        }


        @Override
        public String toString() {
            return description;
        }

        @Override
        public Object[] getArguments() {
            return null;
        }
    }


    @Getter
    enum ReservationError implements ErrorCode {
        NOT_FOUND(CODE_NOT_FOUND, "admin.error.reservation.not-found", "No reservation has been found"),
        UPDATE_FAILED("update_failed", "admin.error.reservation.update-failed", "Update failed"),
        ACCESS_DENIED("access_denied", "admin.error.access-denied", "Access is denied");

        private final String code;
        private final String messageKey;
        private final String description;

        ReservationError(String code, String messageKey, String description) {
            this.code = code;
            this.messageKey = messageKey;
            this.description = description;
        }


        @Override
        public String toString() {
            return description;
        }

        @Override
        public Object[] getArguments() {
            return null;
        }
    }

    static ErrorCode custom(String code, String description) {
        return new ErrorCode() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String toString() {
                return description;
            }

            @Override
            public Object[] getArguments() {
                return null;
            }
        };
    }

    /**
     * An error whose description can be translated
     *
     * @param code the code, which the clients can check
     * @param messageKey the translation key of the description
     * @param description the English description, used where descriptions are not translated
     * @param arguments the values of the placeholders of the description
     */
    static ErrorCode translatable(String code, String messageKey, String description, Object... arguments) {
        return new ErrorCode() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getMessageKey() {
                return messageKey;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String toString() {
                return description;
            }

            @Override
            public Object[] getArguments() {
                return arguments;
            }
        };
    }

    /**
     * The same error, with a different description
     */
    static ErrorCode withDescription(ErrorCode error, String description) {
        return new ErrorCode() {
            @Override
            public String getLocation() {
                return error.getLocation();
            }

            @Override
            public String getCode() {
                return error.getCode();
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String toString() {
                return description;
            }

            @Override
            public Object[] getArguments() {
                return error.getArguments();
            }
        };
    }

    static ErrorCode lazy(Supplier<ErrorCode> supplier) {
        return new ErrorCode() {

            private transient ErrorCode delegate = null;

            @Override
            public String getCode() {
                return get().getCode();
            }

            @Override
            public String getDescription() {
                return get().getDescription();
            }

            @Override
            public String getMessageKey() {
                return get().getMessageKey();
            }

            @Override
            public Object[] getArguments() {
                return get().getArguments();
            }

            private synchronized ErrorCode get() {
                if(delegate != null) {
                    return delegate;
                }
                delegate = supplier.get();
                return delegate;
            }
        };
    }
}
