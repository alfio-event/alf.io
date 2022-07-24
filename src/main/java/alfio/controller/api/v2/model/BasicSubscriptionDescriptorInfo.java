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

import alfio.controller.api.support.CurrencyDescriptor;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionTimeUnit;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionValidityType;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BasicSubscriptionDescriptorInfo(UUID id,
                                              String fileBlobId,
                                              Map<String, String> title,
                                              Map<String, String> description,
                                              DatesWithTimeZoneOffset salePeriod,
                                              SubscriptionValidityType validityType,
                                              SubscriptionUsageType usageType,
                                              ZoneId timeZone,
                                              SubscriptionTimeUnit validityTimeUnit,
                                              Integer validityUnits,
                                              Integer maxEntries,
                                              String organizationEmail,
                                              String organizationName,
                                              String formattedPrice,
                                              String currency,
                                              CurrencyDescriptor currencyDescriptor,
                                              BigDecimal vat,
                                              boolean vatIncluded,
                                              Map<String, String> formattedOnSaleFrom,
                                              Map<String, String> formattedOnSaleTo,
                                              Map<String, String> formattedValidFrom,
                                              Map<String, String> formattedValidTo,
                                              List<Language> contentLanguages) {
}
