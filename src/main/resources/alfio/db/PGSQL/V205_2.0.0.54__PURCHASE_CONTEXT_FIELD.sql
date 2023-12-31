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

create type ADDITIONAL_FIELD_CONTEXT as enum ('ATTENDEE', 'ADDITIONAL_SERVICE', 'SUBSCRIPTION');

create table purchase_context_field_configuration (
   id bigserial primary key not null,
   event_id_fk integer references event(id),
   subscription_descriptor_id_fk uuid references subscription_descriptor(id),
   field_name character varying(64) not null,
   field_order integer not null,
   field_type character varying(64) not null,
   field_restricted_values text,
   field_maxlength integer,
   field_minlength integer,
   field_required boolean not null,
   context ADDITIONAL_FIELD_CONTEXT not null default 'ATTENDEE',
   additional_service_id integer not null default '-1'::integer,
   ticket_category_ids text,
   field_disabled_values text,
   organization_id_fk integer not null references organization(id),
   field_editable boolean not null default true,
   old_id integer -- migration helper
);

create unique index unique_pc_field_configuration_event
    on purchase_context_field_configuration(event_id_fk, field_name, additional_service_id, context)
    where event_id_fk is not null;

create unique index unique_pc_field_configuration_subscription
    on purchase_context_field_configuration(subscription_descriptor_id_fk, field_name, context)
    where event_id_fk is not null;

-- add check constraint
alter table purchase_context_field_configuration
    add constraint "purchase_context_id_check" check (
        -- event_id must be present if the context is "event-related"
        (context in ('ATTENDEE', 'ADDITIONAL_SERVICE') and event_id_fk is not null)
        OR
        -- subscription id must be present if the context is SUBSCRIPTION
        (context = 'SUBSCRIPTION' and subscription_descriptor_id_fk is not null)
    );

-- copy existing data
insert into purchase_context_field_configuration(old_id, event_id_fk, field_name, field_order, field_type, field_restricted_values, field_maxlength, field_minlength, field_required, context, additional_service_id, ticket_category_ids, field_disabled_values, organization_id_fk, field_editable)
select id, event_id_fk, field_name, field_order, field_type, field_restricted_values, field_maxlength, field_minlength, field_required, context::ADDITIONAL_FIELD_CONTEXT, additional_service_id, ticket_category_ids, field_disabled_values, organization_id_fk, field_editable from ticket_field_configuration;

alter table purchase_context_field_configuration enable row level security;
alter table purchase_context_field_configuration force row level security;
create policy purchase_context_field_configuration_access_policy on purchase_context_field_configuration to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

create table purchase_context_field_description (
     field_configuration_id_fk bigint not null references purchase_context_field_configuration(id),
     field_locale text not null,
     description text not null,
     organization_id_fk integer not null references organization(id)
);

-- copy existing descriptions
insert into purchase_context_field_description (field_configuration_id_fk, field_locale, description, organization_id_fk)
    select pfc.id, tfd.field_locale, tfd.description, tfd.organization_id_fk from ticket_field_description tfd
        join purchase_context_field_configuration pfc on tfd.ticket_field_configuration_id_fk = pfc.old_id;

alter table purchase_context_field_description enable row level security;
alter table purchase_context_field_description force row level security;
create policy purchase_context_field_description_access_policy on purchase_context_field_description to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

-- field value
create table purchase_context_field_value(
    context ADDITIONAL_FIELD_CONTEXT not null,
    subscription_id_fk uuid references subscription(id)
        check ( context <> 'SUBSCRIPTION' or subscription_id_fk is not null ),
    ticket_id_fk integer references ticket(id)
        check ( context <> 'ATTENDEE' or ticket_id_fk is not null),
    additional_service_item_id_fk integer references additional_service_item (id)
        check ( context <> 'ADDITIONAL_SERVICE' or additional_service_item_id_fk is not null),
    field_configuration_id_fk bigint not null
        references purchase_context_field_configuration(id),
    field_value text,
    organization_id_fk integer not null references organization(id)
);

create unique index unique_pc_field_value_ticket
    on purchase_context_field_value(context, ticket_id_fk, field_configuration_id_fk)
    where ticket_id_fk is not null;

create unique index unique_pc_field_value_subscription
    on purchase_context_field_value(context, subscription_id_fk, field_configuration_id_fk)
    where subscription_id_fk is not null;

create unique index unique_pc_field_value_additional_service
    on purchase_context_field_value(context, additional_service_item_id_fk, field_configuration_id_fk)
    where additional_service_item_id_fk is not null;

-- import ticket fields
insert into purchase_context_field_value(context, ticket_id_fk, field_configuration_id_fk, field_value, organization_id_fk)
    select 'ATTENDEE'::ADDITIONAL_FIELD_CONTEXT, tfv.ticket_id_fk, pcfc.id, tfv.field_value, pcfc.organization_id_fk
        from ticket_field_value tfv
        join purchase_context_field_configuration pcfc on pcfc.old_id = tfv.ticket_field_configuration_id_fk;

-- import additional service fields
insert into purchase_context_field_value(context, additional_service_item_id_fk, field_configuration_id_fk, field_value, organization_id_fk)
    select 'ADDITIONAL_SERVICE'::ADDITIONAL_FIELD_CONTEXT, afv.additional_service_item_id_fk, pcfc.id, afv.field_value, pcfc.organization_id_fk
        from additional_service_field_value afv
        join purchase_context_field_configuration pcfc on pcfc.old_id = afv.ticket_field_configuration_id_fk;

alter table purchase_context_field_value enable row level security;
alter table purchase_context_field_value force row level security;
create policy purchase_context_field_value_access_policy on purchase_context_field_value to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

-- table additional_service_field_value was added in the same release so it can be safely dropped
drop view if exists all_ticket_field_values;
drop view if exists ticket_field_value_w_additional;
drop view if exists additional_item_field_value_with_ticket_id;
drop table if exists additional_service_field_value;

-- replace function propagate_event_organization_change() to take into account new field tables
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
        update configuration_purchase_context set organization_id_fk = new.org_id where event_id_fk = old.id;
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
        update purchase_context_field_configuration set organization_id_fk = new.org_id where event_id_fk = old.id;
        update purchase_context_field_value set organization_id_fk = new.org_id
        from purchase_context_field_configuration tfc
        where field_configuration_id_fk = tfc.id and tfc.event_id_fk = old.id;
        update purchase_context_field_description set organization_id_fk = new.org_id
        from purchase_context_field_configuration tfc
        where field_configuration_id_fk = tfc.id and tfc.event_id_fk = old.id;
        update purchase_context_field_value set  organization_id_fk = new.org_id
        from purchase_context_field_configuration tfc
        where field_configuration_id_fk = tfc.id and tfc.event_id_fk = old.id;

        update waiting_queue set organization_id_fk = new.org_id where event_id = old.id;

    end if;
    return new;

END
$$ language plpgsql;