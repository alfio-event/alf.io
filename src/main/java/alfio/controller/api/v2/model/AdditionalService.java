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
package alfio.controller.api.v2.model;

import alfio.model.AdditionalService.AdditionalServiceType;
import alfio.model.AdditionalService.SupplementPolicy;
import java.util.Map;

/**
 * @param expired TODO: check, could be useless :)
 */
public record AdditionalService(int id,
                                AdditionalServiceType type,
                                SupplementPolicy supplementPolicy,
                                boolean fixPrice,
                                int availableQuantity,
                                int maxQtyPerOrder,
                                boolean free,
                                String formattedFinalPrice,
                                boolean hasDiscount,
                                String formattedDiscountedPrice,
                                boolean vatApplies,
                                boolean vatIncluded,
                                String vatPercentage,
                                boolean expired,
                                boolean saleInFuture,
                                Map<String, String> formattedInception,
                                Map<String, String> formattedExpiration,
                                Map<String, String> title, Map<String, String> description) {
}
