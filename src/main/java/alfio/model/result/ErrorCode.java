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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@JsonSerialize(using=ErrorCodeSerializer.class)
public interface ErrorCode {

    String DUPLICATE = "duplicate";

    default String getLocation() {
        return "";
    }

    String getCode();
    String getDescription();
    Object[] getArguments();

    @RequiredArgsConstructor
    @Getter
    enum CategoryError implements ErrorCode {
        NOT_FOUND("not_found", "Category not found"),
        NOT_ENOUGH_SEATS("not_enough_tickets", "Not enough seats"),
        ALL_TICKETS_ASSIGNED("all_tickets_assigned", "All the tickets have already been assigned to a category. Try increasing the total seats number."),
        EXPIRATION_AFTER_EVENT_END("expiration_after_event_end", "expiration must be before the end of the event"),
        NOT_ENOUGH_FREE_TOKEN_FOR_SHRINK("not_enough_free_token_for_shrink", "Cannot downsize this category: not enough free token can be removed");

        private final String code;
        private final String description;


        @Override
        public String toString() {
            return description;
        }

        @Override
        public Object[] getArguments() {
            return null;
        }
    }

    @RequiredArgsConstructor
    @Getter
    enum EventError implements ErrorCode {
        NOT_FOUND("not_found", "No event has been found"),
        ACCESS_DENIED("access_denied", "Access is denied");

        private final String code;
        private final String description;


        @Override
        public String toString() {
            return description;
        }

        @Override
        public Object[] getArguments() {
            return null;
        }
    }

    @RequiredArgsConstructor
    @Getter
    enum ReservationError implements ErrorCode {
        NOT_FOUND("not_found", "No reservation has been found"),
        UPDATE_FAILED("update_failed", "Update failed"),
        ACCESS_DENIED("access_denied", "Access is denied");

        private final String code;
        private final String description;


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
            public Object[] getArguments() {
                return null;
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
