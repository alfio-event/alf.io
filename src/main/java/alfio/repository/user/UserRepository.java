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
package alfio.repository.user;

import alfio.model.TicketReservationInvoicingAdditionalInfo;
import alfio.model.support.JSONData;
import alfio.model.user.AdditionalInfoWithLabel;
import alfio.model.user.PublicUserProfile;
import alfio.model.user.User;
import ch.digitalfondue.npjt.*;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.util.*;

@QueryRepository
public interface UserRepository {

    @Query("SELECT * FROM ba_user WHERE id = :userId")
    User findById(@Bind("userId") int userId);

    @Query("SELECT * FROM ba_user WHERE id = :userId")
    Optional<User> findOptionalById(@Bind("userId") int userId);

    @Query("select * from ba_user where id in (:userIds)")
    List<User> findByIds(@Bind("userIds") Collection<Integer> ids);

    @Query("select * from ba_user left join j_user_organization on ba_user.id = j_user_organization.user_id where j_user_organization.org_id = :orgId and ba_user.user_type = 'API_KEY'")
    List<User> findAllApiKeysForOrganization(@Bind("orgId") int organizationId);

    @Query("select id from ba_user join j_user_organization on ba_user.id = j_user_organization.user_id where j_user_organization.org_id = :orgId and ba_user.username = :apiKey and ba_user.user_type = 'API_KEY'")
    Optional<Integer> findUserIdForApiKey(@Bind("orgId") int organizationId, @Bind("apiKey") String apiKey);

    @Query("select * from ba_user where username = :username")
    User getByUsername(@Bind("username") String username);

    @Query("select * from ba_user where username = :username")
    Optional<User> findByUsername(@Bind("username") String username);

    @Query("select id from ba_user where username = :username")
    Optional<Integer> findIdByUserName(@Bind("username") String username);

    default Optional<Integer> nullSafeFindIdByUserName(String username) {
        if(StringUtils.isNotBlank(username)) {
            return findIdByUserName(username);
        }
        return Optional.empty();
    }

    @Query("select * from ba_user where username = :username and enabled = true")
    Optional<User> findEnabledByUsername(@Bind("username") String username);

    @Query("select id from ba_user where username = :username and user_type = 'PUBLIC' and enabled = true")
    Optional<Integer> findPublicUserIdByUsername(@Bind("username") String username);

    @Query("select password from ba_user where username = :username and enabled = true")
    Optional<String> findPasswordByUsername(@Bind("username") String username);

    @Query("""
            INSERT INTO ba_user(username, password, first_name, last_name, email_address, enabled, user_type, valid_to, description) VALUES\
             (:username, :password, :first_name, :last_name, :email_address, :enabled, :userType, :validTo, :description)\
            """)
    @AutoGeneratedKey("id")
    AffectedRowCountAndKey<Integer> create(@Bind("username") String username, @Bind("password") String password,
                                           @Bind("first_name") String firstname, @Bind("last_name") String lastname,
                                           @Bind("email_address") String emailAddress, @Bind("enabled") boolean enabled, @Bind("userType") User.Type userType,
                                           @Bind("validTo") ZonedDateTime validTo, @Bind("description") String description);

    @Query("""
        INSERT INTO ba_user(username, password, first_name, last_name, email_address, enabled, user_type) VALUES\
         (:username, :password, :first_name, :last_name, :email_address, :enabled, 'PUBLIC') on conflict(username) do nothing\
        """)
    int createPublicUserIfNotExists(@Bind("username") String username,
                                                                @Bind("password") String password,
                                                                @Bind("first_name") String firstname,
                                                                @Bind("last_name") String lastname,
                                                                @Bind("email_address") String emailAddress,
                                                                @Bind("enabled") boolean enabled);

    @Query("update ba_user set username = :username, first_name = :firstName, last_name = :lastName, email_address = :emailAddress, description = :description where id = :id")
    int update(@Bind("id") int id, @Bind("username") String username, @Bind("firstName") String firstName, @Bind("lastName") String lastName,
               @Bind("emailAddress") String emailAddress, @Bind("description") String description);

    @Query("update ba_user set first_name = :firstName, last_name = :lastName, email_address = :emailAddress where id = :id")
    int updateContactInfo(@Bind("id") int id, @Bind("firstName") String firstName, @Bind("lastName") String lastName, @Bind("emailAddress") String emailAddress);

    @Query("update ba_user set enabled = :enabled where id = :id")
    int toggleEnabled(@Bind("id") int id, @Bind("enabled") boolean enabled);

    @Query("update ba_user set password = :password where id = :id")
    int resetPassword(@Bind("id") int id, @Bind("password") String newPassword);

    @Query("delete from sponsor_scan where user_id = :id")
    int deleteUserFromSponsorScan(@Bind("id") int id);

    @Query("delete from j_user_organization where user_id = :id")
    int deleteUserFromOrganization(@Bind("id") int id);

    @Query("delete from ba_user where id = :id")
    int deleteUser(@Bind("id") int id);

    @Query("select id from ba_user where user_type in (:types) and enabled = true and user_creation_time < :date")
    List<Integer> findUsersToDeleteOlderThan(@Bind("date") Date date, @Bind("types") Collection<String> types);

    @Query("delete from authority where username = (select username from ba_user where id = :id)")
    int deleteUserFromAuthority(@Bind("id") int id);

    @Query("""
        insert into user_profile (user_id_fk, billing_address_company, billing_address_line1, billing_address_line2, billing_address_zip,\
        billing_address_city, billing_address_state, vat_country, vat_nr, invoicing_additional_information, additional_fields)\
         values (:userId, :company, :line1, :line2, :zip, :city, :state, :country, :taxId, :invoiceInfo::jsonb, :addFields::jsonb)\
         on conflict (user_id_fk) do update set \
         billing_address_company = EXCLUDED.billing_address_company,\
         billing_address_line1 = EXCLUDED.billing_address_line1,\
         billing_address_line2 = EXCLUDED.billing_address_line2,\
         billing_address_zip = EXCLUDED.billing_address_zip,\
         billing_address_city = EXCLUDED.billing_address_city,\
         billing_address_state = EXCLUDED.billing_address_state,\
         vat_country = EXCLUDED.vat_country,\
         vat_nr = EXCLUDED.vat_nr,\
         invoicing_additional_information = EXCLUDED.invoicing_additional_information::jsonb,\
         additional_fields = EXCLUDED.additional_fields::jsonb\
        """)
    int persistUserProfile(@Bind("userId") int userId,
                           @Bind("company") String companyName,
                           @Bind("line1") String addressLine1,
                           @Bind("line2") String addressLine2,
                           @Bind("zip") String zip,
                           @Bind("city") String city,
                           @Bind("state") String state,
                           @Bind("country") String country,
                           @Bind("taxId") String taxId,
                           @Bind("invoiceInfo") @JSONData TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo,
                           @Bind("addFields") @JSONData Map<String, AdditionalInfoWithLabel> additionalFields);

    @Query("select * from user_profile where user_id_fk = :userId")
    Optional<PublicUserProfile> loadUserProfile(@Bind("userId") int userId);

    @Query("delete from user_profile where user_id_fk = :userId")
    int deleteUserProfile(@Bind("userId") int userId);

    default void deleteUserAndReferences(int userId) {
        deleteUserFromSponsorScan(userId);
        deleteUserFromOrganization(userId);
        deleteUserFromAuthority(userId);
        deleteUserProfile(userId);
        deleteUser(userId);
    }

    @Query("update ba_user set enabled = false, email_address = :newEmail, username = :newEmail, first_name = 'Deleted', last_name = 'Deleted' where id = :userId")
    int invalidatePublicUser(@Bind("userId") int userId, @Bind("newEmail") String newEmail);
}
