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
package alfio.util;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@UtilityClass
@Log4j2
public class Wrappers {

    public static <I> void voidTransactionWrapper(Consumer<I> consumer, I input) {
        try {
            consumer.accept(input);
        } catch(Exception ex) {
            log.error("Unexpected exception", ex);
        }
    }

    public static <T> Optional<T> safeSupplier(Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> optionally(Supplier<T> s) {
        try {
            return Optional.ofNullable(s.get());
        } catch (EmptyResultDataAccessException | IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }
}
