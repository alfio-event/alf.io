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
package alfio.model.transaction;

/**
 * Token used for transactions initiated on the backend side and finalized by the frontend side.
 * A typical example of this is Stripe SCA
 */
public interface TransactionInitializationToken extends PaymentToken {
    String getClientSecret();

    default String getErrorMessage() {
        return null;
    }

    /**
     * Override this method if you want to trigger a reload of the reservation page.
     * @return true if the reservation should be reloaded, false otherwise
     */
    default boolean isReservationStatusChanged() {
        return false;
    }
}
