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

DO
$$
BEGIN

-- revoke all grants
EXECUTE (
select 'revoke all on all tables in schema ' || (select string_agg(a.table_schema, ',') from (select table_schema from information_schema.tables where table_name = 'ticket') a)|| ' from application_user'
);

EXECUTE (
select 'revoke all on all sequences in schema ' || (select string_agg(a.table_schema, ',') from (select table_schema from information_schema.tables where table_name = 'ticket') a)|| ' from application_user'
);

EXECUTE (
select 'revoke all on all functions in schema ' || (select string_agg(a.table_schema, ',') from (select table_schema from information_schema.tables where table_name = 'ticket') a)|| ' from application_user'
);


-- we collect all the schema where a table 'ticket' is present
-- and give grant
EXECUTE (
select 'grant all on all tables in schema ' || (select string_agg(a.table_schema, ',') from (select table_schema from information_schema.tables where table_name = 'ticket') a)|| ' to application_user'
);

EXECUTE (
select 'grant all on all sequences in schema ' || (select string_agg(a.table_schema, ',') from (select table_schema from information_schema.tables where table_name = 'ticket') a)|| ' to application_user'
);

EXECUTE (
select 'grant all on all functions in schema ' || (select string_agg(a.table_schema, ',') from (select table_schema from information_schema.tables where table_name = 'ticket') a)|| ' to application_user'
);

END
$$;