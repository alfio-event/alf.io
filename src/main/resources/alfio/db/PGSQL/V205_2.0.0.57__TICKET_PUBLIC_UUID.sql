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

alter table ticket add column public_uuid uuid;
update ticket set public_uuid = uuid::uuid;
alter table ticket alter column public_uuid set not null;

CREATE OR REPLACE FUNCTION fill_public_uuid()
    RETURNS TRIGGER AS
$body$
BEGIN
    IF (NEW.public_uuid is null) THEN
        NEW.public_uuid := NEW.uuid::uuid;
    END IF;
    RETURN NEW;
END
$body$
    LANGUAGE plpgsql;

CREATE TRIGGER t_check_public_uuid
    BEFORE INSERT ON ticket
    FOR EACH ROW
    when ( new.public_uuid is null )
    EXECUTE PROCEDURE fill_public_uuid();

alter table ticket add constraint "t_unique_public_uuid" unique(public_uuid);

