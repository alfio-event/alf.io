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

delete from configuration where c_key = 'EU_COUNTRIES_LIST';
insert into configuration(c_key, c_value, description) values('EU_COUNTRIES_LIST', 'BE,EL,LT,PT,BG,ES,LU,RO,CZ,FR,HU,SI,DK,HR,MT,SK,DE,IT,NL,FI,EE,CY,AT,SE,IE,LV,PL,GB', '');