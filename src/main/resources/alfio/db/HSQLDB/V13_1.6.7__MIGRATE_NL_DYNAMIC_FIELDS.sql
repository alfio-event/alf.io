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

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Functie"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Bedrijf"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Telefoon nummer"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Adres"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Land"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Ik ben een", "restrictedValues": {"F" : "Vrouw", "M" : "Man", "X" : "Overig"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "T-Shirt maat", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'nl', '{"label": "Notities", "placeholder" : "Heeft uw een speciale behoefte (bijvoorbeeld mindervalidetoegang) laat het ons weten"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes';
