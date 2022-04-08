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
package alfio.model.subscription;

import lombok.Getter;

@Getter
public class UsageDetails {
    private final Integer total;
    private final Integer used;
    private final Integer available;

    public UsageDetails(Integer total, Integer used, Integer available) {
        this.total = total;
        this.used = used;
        this.available = available;
    }

    public static UsageDetails fromSubscription(Subscription subscription, int usageCount) {
        int maxEntries = Math.max(subscription.getMaxEntries(), 0);
        return new UsageDetails(maxEntries == 0 ? null : maxEntries,
                                usageCount,
                                maxEntries == 0 ? null : maxEntries - usageCount);
    }
}
