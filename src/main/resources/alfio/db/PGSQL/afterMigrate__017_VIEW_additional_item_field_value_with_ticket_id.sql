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

create or replace view additional_item_field_value_with_ticket_id as (
    select asv.additional_service_item_id_fk as additional_service_item_id_fk, asv.ticket_field_configuration_id_fk as ticket_field_configuration_id_fk, tfc.field_name as field_name, asv.field_value as field_value, item.ticket_id_fk as ticket_id_fk
    from additional_service_field_value asv
        join additional_service_item item on asv.additional_service_item_id_fk = item.id
        join ticket_field_configuration tfc on tfc.id = asv.ticket_field_configuration_id_fk
);
