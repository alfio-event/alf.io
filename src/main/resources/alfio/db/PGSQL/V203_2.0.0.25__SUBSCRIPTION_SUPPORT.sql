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

create type SUBSCRIPTION_TIME_UNIT as enum ('DAYS', 'MONTHS', 'YEARS');
create type SUBSCRIPTION_VALIDITY_TYPE as enum ('STANDARD', 'CUSTOM', 'NOT_SET');
create type SUBSCRIPTION_USAGE_TYPE as enum ('ONCE_PER_EVENT', 'UNLIMITED');
create type ALLOCATION_STATUS as enum ('FREE', 'PRE_RESERVED', 'PENDING', 'TO_BE_PAID', 'ACQUIRED', 'CANCELLED',
                                        'CHECKED_IN', 'EXPIRED',
                                        'INVALIDATED', 'RELEASED');

create type VAT_STATUS as enum(
    'NONE', 'INCLUDED', 'NOT_INCLUDED',
    'INCLUDED_EXEMPT', 'NOT_INCLUDED_EXEMPT');

create table subscription_descriptor (
    id uuid primary key not null,
    title jsonb not null,
    description jsonb,
    max_available integer not null default -1,
    creation_ts timestamp with time zone not null default now(),
    on_sale_from timestamp with time zone not null,
    on_sale_to timestamp with time zone,
    price_cts integer not null,
    vat decimal(5,2) not null,
    vat_status VAT_STATUS not null check (vat_status in('NONE', 'INCLUDED', 'NOT_INCLUDED')),
    currency text,
    is_public boolean not null default false,
    organization_id_fk integer not null constraint subscription_descriptor_organization_id_fk references organization(id),

    -- subscription template
    max_entries integer not null default -1,
    validity_type SUBSCRIPTION_VALIDITY_TYPE not null,
    validity_time_unit SUBSCRIPTION_TIME_UNIT check (validity_type <> 'STANDARD' OR validity_time_unit is not null),
    validity_units integer check (validity_type <> 'STANDARD' OR (validity_units is not null AND validity_units > 0)),
    validity_from timestamp with time zone check (validity_type <> 'CUSTOM' OR validity_from is not null),
    validity_to timestamp with time zone,
    usage_type SUBSCRIPTION_USAGE_TYPE not null default 'ONCE_PER_EVENT',

    terms_conditions_url text not null,
    privacy_policy_url text,
    file_blob_id_fk char(64) not null constraint subscription_descriptor_file_blob_id references file_blob(id),
    allowed_payment_proxies text array not null,
    private_key text not null,
    time_zone text not null

);

alter table subscription_descriptor enable row level security;
alter table subscription_descriptor force row level security;
create policy subscription_descriptor_access_policy on subscription_descriptor to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

create table subscription (
    id uuid primary key not null,
    first_name text,
    last_name text,
    email_address text,
    code text not null constraint subscription_code_unique unique,
    subscription_descriptor_fk uuid not null constraint subscription_subscription_descriptor_fk references subscription_descriptor(id),
    reservation_id_fk character(36) not null constraint subscription_reservation_id_fk references tickets_reservation(id),
    usage_count integer not null,
    max_usage integer,
    valid_from timestamp with time zone default now(),
    valid_to timestamp with time zone,
    src_price_cts integer not null default 0,
    final_price_cts integer not null default 0,
    vat_cts integer not null default 0,
    discount_cts integer not null default 0,
    currency text,
    organization_id_fk integer not null constraint subscription_organization_id_fk references organization(id),
    creation_ts timestamp with time zone not null default now(),
    update_ts timestamp with time zone,
    status ALLOCATION_STATUS not null default 'FREE'
);

alter table subscription enable row level security;
alter table subscription force row level security;
create policy subscription_access_policy on subscription to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((organization_id_fk)));

create table subscription_event (
    id bigserial primary key not null,
    event_id_fk integer not null references event(id),
    subscription_descriptor_id_fk uuid not null constraint subscription_event_subscription_descriptor_id_fk references subscription_descriptor(id),
    price_per_ticket integer not null default 0,
    organization_id_fk integer not null constraint subscription_event_organization_id_fk references organization(id)
);

alter table subscription_event add constraint "unique_subscription_event" unique(subscription_descriptor_id_fk, event_id_fk);

alter table subscription_event enable row level security;
alter table subscription_event force row level security;
create policy subscription_event_access_policy on subscription_event to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access((organization_id_fk)));

alter table event add column tags text array not null default array[]::text[];
alter table tickets_reservation add column subscription_id_fk uuid constraint ticket_subscription_id_fk references subscription(id);


alter table tickets_reservation alter column event_id_fk drop not null;