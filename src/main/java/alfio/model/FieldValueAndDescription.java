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
package alfio.model;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.experimental.Delegate;

import java.util.UUID;

public class FieldValueAndDescription {
    @Delegate
    private final PurchaseContextFieldValue purchaseContextFieldValue;
    private final String description;


    public FieldValueAndDescription(@Column("ticket_id_fk") Integer ticketId,
                                    @Column("subscription_id_fk") UUID subscriptionId,
                                    @Column("additional_service_item_id_fk") Integer additionalServiceItemId,
                                    @Column("field_configuration_id_fk") long fieldConfigurationId,
                                    @Column("field_name") String name,
                                    @Column("field_value") String value,
                                    @Column("context") PurchaseContextFieldConfiguration.Context context,
                                    @Column("description") String description) {
        purchaseContextFieldValue = new PurchaseContextFieldValue(ticketId, subscriptionId, additionalServiceItemId, fieldConfigurationId, name, value, context);
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
