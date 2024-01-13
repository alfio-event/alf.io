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
package alfio.repository;

import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@QueryRepository
public interface OrganizationDeleterRepository {

    Logger LOGGER = LoggerFactory.getLogger(OrganizationDeleterRepository.class);
    String SELECT_EMPTY_ORGANIZATIONS = "select distinct(org_id) from j_user_organization where org_id in(:organizationIds)";

    @Query("delete from auditing where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteAuditingForEmptyOrganizations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from invoice_sequences where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteInvoiceSequencesForEmptyOrganizations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from a_group where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteGroupsForEmptyOrganizations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from group_member where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteGroupMembersForEmptyOrganizations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from configuration_organization where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteConfigurationForEmptyOrganizations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from configuration_purchase_context where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteConfigurationForPurchaseContexts(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from resource_organizer where organization_id_fk in(:organizationIds)" +
        " and organization_id_fk not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteResourcesForEmptyOrganizations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from subscription where organization_id_fk in (:organizationIds)")
    int deleteSubscriptions(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from subscription_descriptor where organization_id_fk in (:organizationIds)")
    int deleteSubscriptionDescriptors(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from promo_code where organization_id_fk in (:organizationIds)")
    int deletePromoCodes(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from organization where id in(:organizationIds)" +
        " and id not in (" + SELECT_EMPTY_ORGANIZATIONS + ")")
    int deleteOrganizationsIfEmpty(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from tickets_reservation where organization_id_fk in (:organizationIds)")
    int deleteReservations(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from admin_reservation_request where organization_id_fk in (:organizationIds)")
    int deleteAdminReservationRequests(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from email_message where organization_id_fk in (:organizationIds)")
    int deleteEmailMessages(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from b_transaction where organization_id_fk in (:organizationIds)")
    int deleteAllTransactions(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from purchase_context_field_value where organization_id_fk in (:organizationIds)")
    int deleteFieldValues(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from purchase_context_field_description where organization_id_fk in (:organizationIds)")
    int deleteFieldDescription(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("delete from purchase_context_field_configuration where organization_id_fk in (:organizationIds)")
    int deleteFieldConfiguration(@Bind("organizationIds") List<Integer> organizationIds);

    default void deleteEmptyOrganizations(List<Integer> organizationIds) {
        // delete invoice sequences
        int deletedSequences = deleteInvoiceSequencesForEmptyOrganizations(organizationIds);
        LOGGER.info("deleted {} invoice sequences", deletedSequences);
        // delete auditing
        int deletedAuditing = deleteAuditingForEmptyOrganizations(organizationIds);
        LOGGER.info("deleted {} auditing rows", deletedAuditing);
        // delete groups
        int deletedGroupMembers = deleteGroupMembersForEmptyOrganizations(organizationIds);
        int deletedGroups = deleteGroupsForEmptyOrganizations(organizationIds);
        LOGGER.info("deleted {} groups and {} members", deletedGroups, deletedGroupMembers);

        // delete configuration
        int deletedConfigurations = deleteConfigurationForEmptyOrganizations(organizationIds);
        LOGGER.info("deleted {} configurations", deletedConfigurations);

        deletedConfigurations = deleteConfigurationForPurchaseContexts(organizationIds);
        LOGGER.info("deleted {} configurations for purchase_contexts", deletedConfigurations);

        // delete resources
        int deletedResources = deleteResourcesForEmptyOrganizations(organizationIds);
        LOGGER.info("deleted {} resources", deletedResources);

        int deletedEmails = deleteEmailMessages(organizationIds);
        LOGGER.info("deleted {} email messages", deletedEmails);

        // delete subscriptions
        int deletedSubscriptions = deleteSubscriptions(organizationIds);
        int deletedDescriptors = deleteSubscriptionDescriptors(organizationIds);
        LOGGER.info("deleted {} subscription descriptors and {} subscriptions", deletedDescriptors, deletedSubscriptions);

        int deletedTransactions = deleteAllTransactions(organizationIds);
        LOGGER.info("deleted {} transactions", deletedTransactions);

        // delete all reservations
        int deletedReservations = deleteReservations(organizationIds);
        LOGGER.info("deleted {} reservations", deletedReservations);

        // delete admin reservation request
        int deletedAdminReservationRequests = deleteAdminReservationRequests(organizationIds);
        LOGGER.info("deleted {} adminReservationRequests", deletedAdminReservationRequests);

        // delete promo codes
        int deletedPromoCodes = deletePromoCodes(organizationIds);
        LOGGER.info("deleted {} promo codes", deletedPromoCodes);

        int deletedOrganizations = deleteOrganizationsIfEmpty(organizationIds);
        LOGGER.info("deleted {} empty organizations", deletedOrganizations);

    }
}
