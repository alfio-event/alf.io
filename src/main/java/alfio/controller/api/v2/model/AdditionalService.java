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

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor
@Getter
public class AdditionalService {

    private final int id;
    private final alfio.model.AdditionalService.AdditionalServiceType type;
    private final alfio.model.AdditionalService.SupplementPolicy supplementPolicy;

    //
    private final boolean fixPrice;
    private final int availableQuantity;
    private final int maxQtyPerOrder;

    //
    private final boolean free;
    private final String formattedFinalPrice;
    private final boolean hasDiscount;
    private final String formattedDiscountedPrice;
    private final boolean vatApplies;
    private final boolean vatIncluded;
    private final String vatPercentage;
    //


    private final boolean expired; //TODO: check, could be useless :)
    private final boolean saleInFuture;
    private final Map<String, String> formattedInception;
    private final Map<String, String> formattedExpiration;

    private final Map<String, String> title;
    private final Map<String, String> description;
}
