--
-- This file is part of alf.io.
--
-- alf.io is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- alf.io is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
--

-- this function propagates organization id change to event's descendant resources
create or replace function propagate_event_organization_change() returns trigger
as $$
DECLARE
    subscription_links_count numeric;
    group_links_count numeric;
BEGIN
    if new.org_id <> old.org_id then
        -- organizationId has changed.
        -- We need to migrate all descendant resources to the new organization

        -- first we check if there are any incompatible links in place
        subscription_links_count := (select count(*) from subscription_event where event_id_fk = old.id);
        if subscription_links_count > 0 then
            raise 'CANNOT_TRANSFER_SUBSCRIPTION_LINK' USING DETAIL = ('{count:' || subscription_links_count || '}');
        end if;

        group_links_count := (select count(*) from group_link where event_id_fk = old.id);
        if group_links_count > 0 then
            raise 'CANNOT_TRANSFER_GROUP_LINK' USING DETAIL = ('{count:' || group_links_count || '}');
        end if;

        -- ticket categories / tickets
        update ticket_category set organization_id_fk = new.org_id where event_id = old.id;
        update ticket set organization_id_fk = new.org_id where event_id = old.id;

        -- additional services
        update additional_service set organization_id_fk = new.org_id where event_id_fk = old.id;
        update additional_service_description set organization_id_fk = new.org_id
            from additional_service ase
            where additional_service_id_fk = ase.id and ase.event_id_fk = old.id;
        update additional_service_item set organization_id_fk = new.org_id
            from additional_service ase
            where additional_service_id_fk = ase.id and ase.event_id_fk = old.id;

        -- ticket reservations
        update tickets_reservation set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- admin reservation request
        update admin_reservation_request set organization_id_fk = new.org_id where event_id = old.id;

        -- auditing
        update auditing set organization_id_fk = new.org_id where event_id = old.id;

        -- billing_document
        update billing_document set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- configuration
        update configuration_event set organization_id_fk = new.org_id where event_id_fk = old.id;
        update configuration_ticket_category set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- messages
        update email_message set organization_id_fk = new.org_id where event_id = old.id;

        -- event descriptions
        update event_description_text set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- polls
        update poll set organization_id_fk = new.org_id where event_id_fk = old.id;
        update poll_answer set organization_id_fk = new.org_id
            from poll p
            where poll_id_fk = p.id and p.event_id_fk = old.id;
        update poll_option set organization_id_fk = new.org_id
            from poll p
            where poll_id_fk = p.id and p.event_id_fk = old.id;

        -- promo code
        update promo_code set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- transaction
        update b_transaction set organization_id_fk = new.org_id
            from tickets_reservation tr
            where reservation_id = tr.id and tr.event_id_fk = old.id;

        -- event resources
        update resource_event set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- scan audit
        update scan_audit set organization_id_fk = new.org_id where event_id_fk = old.id;

        -- special price
        update special_price set organization_id_fk = new.org_id
            from ticket_category tc
            where ticket_category_id = tc.id and tc.event_id = old.id;

        -- sponsor scan
        update sponsor_scan set organization_id_fk = new.org_id where event_id = old.id;

        -- ticket_category_text
        update ticket_category_text set organization_id_fk = new.org_id
            from ticket_category tc
            where ticket_category_id_fk = tc.id and tc.event_id = old.id;

        -- ticket field
        update ticket_field_configuration set organization_id_fk = new.org_id where event_id_fk = old.id;
        update ticket_field_value set organization_id_fk = new.org_id
            from ticket_field_configuration tfc
            where ticket_field_configuration_id_fk = tfc.id and tfc.event_id_fk = old.id;
        update ticket_field_description set organization_id_fk = new.org_id
            from ticket_field_configuration tfc
            where ticket_field_configuration_id_fk = tfc.id and tfc.event_id_fk = old.id;
        update ticket_field_value set  organization_id_fk = new.org_id
            from ticket_field_configuration tfc
            where ticket_field_configuration_id_fk = tfc.id and tfc.event_id_fk = old.id;

        update waiting_queue set organization_id_fk = new.org_id where event_id = old.id;

    end if;
    return new;

END
$$ language plpgsql;

create trigger event_update_org_id_fk_trigger
    after update on event
    for each row execute procedure propagate_event_organization_change();

