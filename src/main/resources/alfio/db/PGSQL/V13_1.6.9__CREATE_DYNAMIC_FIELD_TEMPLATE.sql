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

create table dynamic_field_template (
    id serial PRIMARY KEY NOT NULL,
    field_name varchar(64) not null,
    field_type varchar(64) not null,
    field_restricted_values text,
    field_description text,
    field_maxlength int,
    field_minlength int
);

alter table dynamic_field_template add constraint "unique_dynamic_template_name" unique(field_name);

insert into dynamic_field_template (field_name, field_type, field_description, field_minlength, field_maxlength) values('jobTitle', 'input:text', '{"en":{"label": "Job title"},"it":{"label": "Ruolo"}, "de":{"label": "Berufsbezeichnung"}, "nl":{"label": "Functie"}}', 0, 255);
insert into dynamic_field_template (field_name, field_type, field_description, field_minlength, field_maxlength) values('phoneNumber', 'input:tel', '{"en":{"label": "Phone number"},"it":{"label": "Numero di telefono"}, "de":{"label": "Telefonnummer"}, "nl":{"label": "Telefoon nummer"}}', 0, 255);
insert into dynamic_field_template (field_name, field_type, field_description, field_minlength, field_maxlength) values('company', 'input:text', '{"en":{"label": "Company"},"it":{"label": "Societ\u00E0"}, "de":{"label": "Firma"}, "nl":{"label": "Bedrijf"}}', 0, 255);
insert into dynamic_field_template (field_name, field_type, field_description, field_minlength, field_maxlength) values('address', 'textarea', '{"en":{"label": "Address"},"it":{"label": "Indirizzo"}, "de":{"label": "Adresse"}, "nl":{"label": "Adres"}}', 0, 450);
insert into dynamic_field_template (field_name, field_type, field_description, field_minlength, field_maxlength) values('country', 'country', '{"en":{"label": "Country"},"it":{"label": "Nazione"}, "de":{"label": "Land"}, "nl":{"label": "Land"}}', 0, 255);
insert into dynamic_field_template (field_name, field_type, field_description, field_minlength, field_maxlength) values('notes', 'textarea', '{"en":{"label": "Notes", "placeholder" : "Should you have any special need (such as but not limited to diet or accessibility requirements) please let us know"},"it":{"label": "Note", "placeholder" : "Hai qualche esigenza particolare (ad esempio per quanto riguarda cibo o accessibilit\u00E0)? Per favore, faccelo sapere!"}, "de":{"label": "Bemerkungen", "placeholder" : "Gerne nehmen wir auf Ihre Bed\u00FCrfnisse R\u00FCcksicht, falls du aussergew\u00F6hnliche Essensgewohnheiten oder eingeschr\u00E4nkte Zutrittsm\u00F6glichkeiten hast, informiere uns bitte."}, "nl":{"label": "Notities", "placeholder" : "Heeft uw een speciale behoefte (bijvoorbeeld mindervalidetoegang) laat het ons weten"}}', 0, 1024);
insert into dynamic_field_template (field_name, field_type, field_description, field_restricted_values) values('gender', 'select', '{"en":{"label": "I am", "restrictedValues": {"F" : "a woman", "M" : "a man", "X" : "Other"}},"it":{"label": "Sono", "restrictedValues": {"F" : "una donna", "M" : "un uomo", "X" : "Altro"}}, "de":{"label": "Ich bin", "restrictedValues": {"F" : "Frau", "M" : "Herr", "X" : "Andere"}}, "nl":{"label": "Ik ben een", "restrictedValues": {"F" : "Vrouw", "M" : "Man", "X" : "Overig"}}}', '["F", "M", "X"]');
insert into dynamic_field_template (field_name, field_type, field_description, field_restricted_values) values('tShirtSize', 'select', '{"en":{"label": "T-Shirt size", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }},"it":{"label": "Misura T-Shirt", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}, "de":{"label": "T-Shirt-Gr\u00F6\u00DFe", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}, "nl":{"label": "T-Shirt maat", "restrictedValues": {"SMALL" : "Small", "MEDIUM" : "Medium", "LARGE" : "Large", "X-LARGE" : "X-Large", "2X-LARGE" : "2X-Large" }}}', '["SMALL", "MEDIUM", "LARGE", "X-LARGE", "2X-LARGE"]');