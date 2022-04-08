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

import lombok.Getter;

import java.util.Map;

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

    public AdditionalService(int id,
                             alfio.model.AdditionalService.AdditionalServiceType type,
                             alfio.model.AdditionalService.SupplementPolicy supplementPolicy,
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
                             Map<String, String> title,
                             Map<String, String> description) {
        this.id = id;
        this.type = type;
        this.supplementPolicy = supplementPolicy;
        this.fixPrice = fixPrice;
        this.availableQuantity = availableQuantity;
        this.maxQtyPerOrder = maxQtyPerOrder;
        this.free = free;
        this.formattedFinalPrice = formattedFinalPrice;
        this.hasDiscount = hasDiscount;
        this.formattedDiscountedPrice = formattedDiscountedPrice;
        this.vatApplies = vatApplies;
        this.vatIncluded = vatIncluded;
        this.vatPercentage = vatPercentage;
        this.expired = expired;
        this.saleInFuture = saleInFuture;
        this.formattedInception = formattedInception;
        this.formattedExpiration = formattedExpiration;
        this.title = title;
        this.description = description;
    }
}
