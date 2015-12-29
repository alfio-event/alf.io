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


-- change field type
alter table ticket_field_description alter column description SET DATA TYPE text;
alter table ticket_field_configuration alter column field_restricted_values SET DATA TYPE text;

-- port the currently existing events to the new structure, first the field definitions
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'jobTitle', 0, 'input:text', false, 255  FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'company', 1, 'input:text', false, 255 FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'phoneNumber', 2, 'input:tel', false, 255 FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'address', 3, 'textarea', false, 450 FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required)
    (SELECT event.id, 'country', 4, 'country', false FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_restricted_values)
    (SELECT event.id, 'gender', 5, 'select', false, '["F", "M", "X"]' FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_restricted_values)
    (SELECT event.id, 'tShirtSize', 6, 'select', false, '["SMALL", "MEDIUM", "LARGE", "X-LARGE", "2X-LARGE"]' FROM event);
insert into ticket_field_configuration (event_id_fk, field_name, field_order, field_type, field_required, field_maxlength)
    (SELECT event.id, 'notes', 7, 'textarea', false, 1024 FROM event);

-- create translations for each fields
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "Job title"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Ruolo"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Berufsbezeichnung"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'jobTitle';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "Company"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Societ\u00E0"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Firma"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'company';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "Phone number"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Numero di telefono"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Telefonnummer"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'phoneNumber';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "Address"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Indirizzo"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Adresse"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'address';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "Country"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Nazione"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Land"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'country';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "I am", "restrictedValues": {"F" : "a woman", "M" : "a man", "X" : "Other"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Sono", "restrictedValues": {"F" : "una donna", "M" : "un uomo", "X" : "Altro"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Ich bin", "restrictedValues": {"F" : "Frau", "M" : "Herr", "X" : "Andere"}}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'gender';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "T-Shirt size", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Misura T-Shirt", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "T-Shirt-Gr\u00F6\u00DFe", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'tShirtSize';

insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'en', '{"label": "Notes", "placeholder" : "Should you have any special need (such as but not limited to diet or accessibility requirements) please let us know"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'it', '{"label": "Note", "placeholder" : "Hai qualche esigenza particolare (ad esempio per quanto riguarda cibo o accessibilit\u00E0)? Per favore, faccelo sapere!"}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes';
insert into ticket_field_description(ticket_field_configuration_id_fk, field_locale, description)
    select ticket_field_configuration.id, 'de', '{"label": "Bemerkungen", "placeholder" : "Gerne nehmen wir auf Ihre Bed\u00FCrfnisse R\u00FCcksicht, falls du aussergew\u00F6hnliche Essensgewohnheiten oder eingeschr\u00E4nkte Zutrittsm\u00F6glichkeiten hast, informiere uns bitte."}' from ticket_field_configuration inner join event on event_id_fk = event.id where field_name = 'notes';

-- copy values from ticket to new fields

insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, JOB_TITLE from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'jobTitle';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, COMPANY from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'company';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, PHONE_NUMBER from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'phoneNumber';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, ADDRESS from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'address';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, COUNTRY from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'country';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, GENDER from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'gender';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, TSHIRT_SIZE from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'tShirtSize';
insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) select ticket.id, ticket_field_configuration.id, NOTES from ticket inner join ticket_field_configuration on event_id = event_id_fk where field_name = 'notes';

ALTER TABLE ticket drop column JOB_TITLE;
ALTER TABLE ticket drop column COMPANY;
ALTER TABLE ticket drop column PHONE_NUMBER;
ALTER TABLE ticket drop column ADDRESS;
ALTER TABLE ticket drop column COUNTRY;
ALTER TABLE ticket drop column GENDER;
ALTER TABLE ticket drop column TSHIRT_SIZE;
ALTER TABLE ticket drop column NOTES;
