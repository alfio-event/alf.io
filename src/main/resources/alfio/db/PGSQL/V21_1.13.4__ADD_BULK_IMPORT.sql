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

create table admin_reservation_request (
    id bigserial primary key not null,
    request_id varchar(512) not null,
    user_id bigint not null,
    event_id bigint not null,
    reservation_id varchar(512),
    request_type varchar(64),
    status varchar(64),
    body text
);

create index "admin_reservation_request_req_idx" on admin_reservation_request(request_id);
create index "admin_reservation_request_res_idx" on admin_reservation_request(reservation_id);

create view admin_reservation_request_stats as (
    select request_id,
        user_id,
        event_id,
        sum(case (status = 'PENDING') when true then 1 else 0 end) as count_pending,
        sum(case (status = 'SUCCESS') when true then 1 else 0 end) as count_success,
        sum(case (status = 'ERROR') when true then 1 else 0 end) as count_error
    from admin_reservation_request
    group by request_id, event_id, user_id
);