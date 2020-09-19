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

-- add tags on ticket, in order to check who can participate to the poll
alter table ticket add column tags text array not null default array[]::text[];

create type POLL_STATUS as enum ('DRAFT', 'OPEN', 'CLOSED');

create table poll (
    id bigserial primary key not null,
    status POLL_STATUS not null default 'DRAFT',
    title jsonb not null,
    description jsonb,
    allowed_tags text array not null default array[]::text[],
    poll_order integer not null default 0,

    event_id_fk integer not null constraint "poll_event_id_fk" references event(id),
    organization_id_fk integer not null constraint "poll_org_id_fk" references organization(id)
);

alter table poll enable row level security;
alter table poll force row level security;
create policy poll_access_policy on poll to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

create table poll_option (
    id bigserial primary key not null,
    poll_id_fk bigint not null constraint "poll_option_poll_id" references poll(id) on delete cascade,
    title jsonb not null,
    description jsonb,
    organization_id_fk integer not null constraint "poll_option_org_id_fk" references organization(id)
);

alter table poll_option enable row level security;
alter table poll_option force row level security;
create policy poll_option_access_policy on poll_option to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));

create table poll_answer (
    id bigserial primary key not null,
    poll_id_fk bigint not null constraint "poll_answer_poll_id" references poll(id) on delete cascade,
    poll_option_id_fk bigint not null constraint "poll_answer_option_id_fk" references poll_option(id) on delete cascade,
    ticket_id_fk integer constraint "poll_answer_ticket_id_fk" references ticket(id),
    organization_id_fk integer not null constraint "poll_answer_org_id_fk" references organization(id)
);

alter table poll_answer add constraint "unique_answer_poll_ticket" unique(poll_id_fk, ticket_id_fk);

alter table poll_answer enable row level security;
alter table poll_answer force row level security;
create policy poll_answer_access_policy on poll_answer to public
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));
