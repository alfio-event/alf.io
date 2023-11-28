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
    select asv.additional_service_item_id_fk as additional_service_item_id_fk,
           asv.ticket_field_configuration_id_fk as ticket_field_configuration_id_fk,
           tfc.field_name as field_name,
           coalesce(asv.field_value, '') as field_value,
           item.ticket_id_fk as ticket_id_fk,
           tfc.field_type as field_type,
           tfc.context as context,
           item.additional_service_id_fk as additional_service_id_fk
    from additional_service_field_value asv
        join additional_service_item item on asv.additional_service_item_id_fk = item.id
        join ticket_field_configuration tfc on tfc.id = asv.ticket_field_configuration_id_fk
);

create or replace view ticket_field_value_w_additional as (
    select a.ticket_id_fk as ticket_id_fk,
           a.ticket_field_configuration_id_fk as ticket_field_configuration_id_fk,
           b.field_name as field_name,
           a.field_value as field_value,
           b.field_type as field_type,
           b.context as context
        from ticket_field_value a
            join ticket_field_configuration b on a.ticket_field_configuration_id_fk = b.id
    union all
    select a.ticket_id_fk as ticket_id_fk,
           a.ticket_field_configuration_id_fk as ticket_field_configuration_id_fk,
           a.field_name as field_name,
           a.field_value as field_value,
           a.field_type as field_type,
           a.context as context
        from additional_item_field_value_with_ticket_id a
);

create or replace view all_ticket_field_values as (
      select a.ticket_id_fk, a.ticket_field_configuration_id_fk, a.field_name, a.field_value, null as description, a.context as context
          from ticket_field_value_w_additional a
          where a.field_type <> 'select'
      union all
      select a.ticket_id_fk, a.ticket_field_configuration_id_fk, a.field_name, a.field_value, c.description, a.context as context
          from ticket_field_value_w_additional a
               inner join ticket on a.ticket_id_fk = ticket.id
               left join ticket_field_description c on c.ticket_field_configuration_id_fk = a.ticket_field_configuration_id_fk
          where c.field_locale = ticket.user_language
            and a.field_type = 'select'
);

