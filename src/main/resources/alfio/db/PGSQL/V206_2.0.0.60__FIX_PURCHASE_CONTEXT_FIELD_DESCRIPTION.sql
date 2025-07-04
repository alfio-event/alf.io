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

-- delete duplicate rows, thanks to https://github.com/Tiddons for the statement
DELETE FROM purchase_context_field_description
WHERE ctid IN (
    SELECT ctid
    FROM (
             SELECT ctid,
                    ROW_NUMBER() OVER (PARTITION BY field_configuration_id_fk, field_locale ORDER BY ctid) AS rnum
             FROM purchase_context_field_description
         ) t
    WHERE t.rnum > 1
);

-- add missing unique constraint (+index)
alter table purchase_context_field_description add constraint "pc_field_description_unique_locale" unique(field_configuration_id_fk, field_locale);