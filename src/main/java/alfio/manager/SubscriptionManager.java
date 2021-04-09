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
package alfio.manager;

import alfio.controller.form.SearchOptions;
import alfio.model.AllocationStatus;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.subscription.EventSubscriptionLink;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptorWithStatistics;
import alfio.repository.SubscriptionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;

@Component
@Transactional
@AllArgsConstructor
@Log4j2
public class SubscriptionManager {

    private final SubscriptionRepository subscriptionRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<SubscriptionDescriptor> findAll(int organizationId) {
        return subscriptionRepository.findAllByOrganizationIds(organizationId);
    }

    public Optional<UUID> createSubscriptionDescriptor(SubscriptionDescriptorModification subscriptionDescriptor) {
        var id = UUID.randomUUID();
        int maxAvailable = requireNonNullElse(subscriptionDescriptor.getMaxAvailable(), -1);
        int result = subscriptionRepository.createSubscriptionDescriptor(
            id,
            subscriptionDescriptor.getTitle(),
            subscriptionDescriptor.getDescription(),
            maxAvailable,
            subscriptionDescriptor.getOnSaleFrom(),
            subscriptionDescriptor.getOnSaleTo(),
            subscriptionDescriptor.getPriceCts(),
            subscriptionDescriptor.getVat(),
            subscriptionDescriptor.getVatStatus(),
            subscriptionDescriptor.getCurrency(),
            Boolean.TRUE.equals(subscriptionDescriptor.getIsPublic()),
            subscriptionDescriptor.getOrganizationId(),

            requireNonNullElse(subscriptionDescriptor.getMaxEntries(), -1),
            subscriptionDescriptor.getValidityType(),
            subscriptionDescriptor.getValidityTimeUnit(),
            subscriptionDescriptor.getValidityUnits(),
            subscriptionDescriptor.getValidityFrom(),
            subscriptionDescriptor.getValidityTo(),
            subscriptionDescriptor.getUsageType(),

            subscriptionDescriptor.getTermsAndConditionsUrl(),
            subscriptionDescriptor.getPrivacyPolicyUrl(),
            subscriptionDescriptor.getFileBlobId(),
            subscriptionDescriptor.getPaymentProxies(),
            UUID.randomUUID().toString(),
            subscriptionDescriptor.getTimeZone().toString());

        if(result != 1) {
            return Optional.empty();
        }

        // pre-generate subscriptions if descriptor has a limited quantity
        if(maxAvailable > 0) {
            preGenerateSubscriptions(subscriptionDescriptor, id, maxAvailable);
        }

        return Optional.of(id);
    }

    private void preGenerateSubscriptions(SubscriptionDescriptorModification subscriptionDescriptor, UUID subscriptionDescriptorId, int quantity) {
        var results = jdbcTemplate.batchUpdate(subscriptionRepository.batchCreateSubscription(), Stream.generate(UUID::randomUUID)
            .limit(quantity)
            .map(subscriptionId -> new MapSqlParameterSource("id", subscriptionId)
                .addValue("subscriptionDescriptorId", subscriptionDescriptorId)
                .addValue("maxUsage", subscriptionDescriptor.getMaxEntries())
                .addValue("validFrom", toOffsetDateTime(subscriptionDescriptor.getValidityFrom()))
                .addValue("validTo", toOffsetDateTime(subscriptionDescriptor.getValidityTo()))
                .addValue("srcPriceCts", subscriptionDescriptor.getPriceCts())
                .addValue("currency", subscriptionDescriptor.getCurrency())
                .addValue("organizationId", subscriptionDescriptor.getOrganizationId())
                .addValue("status", AllocationStatus.FREE.name())
                .addValue("maxEntries", subscriptionDescriptor.getMaxEntries())
                .addValue("reservationId", null)
                .addValue("timeZone", subscriptionDescriptor.getTimeZone().toString())
            ).toArray(MapSqlParameterSource[]::new));
        var added = Arrays.stream(results).sum();
        if(added != quantity) {
            log.warn("wanted to generate {} subscriptions, got {} instead", quantity, added);
            throw new IllegalStateException("Cannot set max availability");
        }
    }

    private static OffsetDateTime toOffsetDateTime(ZonedDateTime in) {
        if(in == null) {
            return null;
        }
        return in.withZoneSameInstant(ZoneId.of("UTC")).toOffsetDateTime();
    }

    public Optional<UUID> updateSubscriptionDescriptor(SubscriptionDescriptorModification subscriptionDescriptor) {

        return subscriptionRepository.findOne(subscriptionDescriptor.getId(), subscriptionDescriptor.getOrganizationId())
            .flatMap(original -> {
                int maxAvailable = requireNonNullElse(subscriptionDescriptor.getMaxAvailable(), -1);
                int result = subscriptionRepository.updateSubscriptionDescriptor(
                    subscriptionDescriptor.getTitle(),
                    subscriptionDescriptor.getDescription(),
                    requireNonNullElse(maxAvailable, -1),
                    subscriptionDescriptor.getOnSaleFrom(),
                    subscriptionDescriptor.getOnSaleTo(),
                    subscriptionDescriptor.getPriceCts(),
                    subscriptionDescriptor.getVat(),
                    subscriptionDescriptor.getVatStatus(),
                    subscriptionDescriptor.getCurrency(),
                    Boolean.TRUE.equals(subscriptionDescriptor.getIsPublic()),

                    requireNonNullElse(subscriptionDescriptor.getMaxEntries(), -1),
                    subscriptionDescriptor.getValidityType(),
                    subscriptionDescriptor.getValidityTimeUnit(),
                    subscriptionDescriptor.getValidityUnits(),
                    subscriptionDescriptor.getValidityFrom(),
                    subscriptionDescriptor.getValidityTo(),
                    subscriptionDescriptor.getUsageType(),

                    subscriptionDescriptor.getTermsAndConditionsUrl(),
                    subscriptionDescriptor.getPrivacyPolicyUrl(),
                    subscriptionDescriptor.getFileBlobId(),
                    subscriptionDescriptor.getPaymentProxies(),

                    subscriptionDescriptor.getId(),
                    original.getOrganizationId(),
                    subscriptionDescriptor.getTimeZone().toString()
                );

                if(maxAvailable > 0 && maxAvailable > original.getMaxAvailable()) {
                    int existing = Math.max(0, original.getMaxAvailable());
                    preGenerateSubscriptions(subscriptionDescriptor, subscriptionDescriptor.getId(), maxAvailable - existing);
                } else if(maxAvailable > -1 && maxAvailable < original.getMaxAvailable()) {
                    int amount = original.getMaxAvailable() - maxAvailable;
                    int invalidated = subscriptionRepository.invalidateSubscriptions(subscriptionDescriptor.getId(), amount);
                    Validate.isTrue(amount == invalidated, "Cannot invalidate existing subscriptions. (wanted: %d got: %d)", amount, invalidated);
                }
                return result == 1 ? Optional.of(subscriptionDescriptor.getId()) : Optional.empty();
            });
    }

    public Optional<SubscriptionDescriptor> findOne(UUID id, int organizationId) {
        return subscriptionRepository.findOne(id, organizationId);
    }

    public boolean setPublicStatus(UUID id, int organizationId, boolean isPublic) {
        return subscriptionRepository.setPublicStatus(id, organizationId, isPublic) == 1;
    }

    public List<SubscriptionDescriptor> getActivePublicSubscriptionsDescriptor(ZonedDateTime from, SearchOptions searchOptions) {
        return subscriptionRepository.findAllActiveAndPublic(from, searchOptions.getOrganizerSlug());
    }

    public Optional<SubscriptionDescriptor> getSubscriptionById(UUID id) {
        return subscriptionRepository.findOne(id);
    }

    public List<SubscriptionDescriptorWithStatistics> loadSubscriptionsWithStatistics(int organizationId) {
        return subscriptionRepository.findAllWithStatistics(organizationId);
    }

    public int linkSubscriptionToEvent(UUID subscriptionId, int eventId, int organizationId, int pricePerTicket) {
        return subscriptionRepository.linkSubscriptionAndEvent(subscriptionId, eventId, pricePerTicket, organizationId);
    }

    public List<EventSubscriptionLink> getLinkedEvents(int organizationId, UUID id) {
        return subscriptionRepository.findLinkedEvents(organizationId, id);
    }

    public int countFree(UUID subscriptionDescriptorId) {
        return subscriptionRepository.countFreeSubscriptionForDescriptor(subscriptionDescriptorId);
    }
}
