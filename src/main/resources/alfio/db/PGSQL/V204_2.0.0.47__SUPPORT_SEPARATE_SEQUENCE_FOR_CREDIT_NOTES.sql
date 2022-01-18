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

alter table invoice_sequences drop constraint invoice_sequences_pkey;
create type BILLING_DOCUMENT_TYPE as enum ('INVOICE', 'CREDIT_NOTE');
alter table invoice_sequences add column document_type BILLING_DOCUMENT_TYPE not null default 'INVOICE';
alter table invoice_sequences add primary key (organization_id_fk, document_type);
-- prefill data for existing organizations
insert into invoice_sequences (organization_id_fk, invoice_sequence, document_type)
 select o.id, 1, 'CREDIT_NOTE' from organization o;