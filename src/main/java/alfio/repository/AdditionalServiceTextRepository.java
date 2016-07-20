/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.repository;

import alfio.model.AdditionalServiceText;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface AdditionalServiceTextRepository {

    @Query("select id, additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk = :additionalServiceId")
    List<AdditionalServiceText> findAllByAdditionalServiceId(@Bind("additionalServiceId") int additionalServiceId);

    @Query("delete from additional_service_description where additional_service_id_fk = :additionalServiceId")
    int deleteAdditionalServiceTexts(@Bind("additionalServiceId") int additionalServiceId);

    @Query("select id, additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk = :additionalServiceId and locale = :locale and type = :type")
    AdditionalServiceText findByLocaleAndType(@Bind("additionalServiceId") int additionalServiceId, @Bind("locale") String locale, @Bind("type") AdditionalServiceText.TextType type);

    @Query("insert into additional_service_description(additional_service_id_fk, locale, type, value) values(:additionalServiceId, :locale, :type, :value)")
    int insert(@Bind("additionalServiceId") int additionalServiceId, @Bind("locale") String locale, @Bind("type") AdditionalServiceText.TextType type, @Bind("value") String value);

    @Query("update additional_service_description set locale = :locale, type = :type, value = :value where id = :id")
    int update(@Bind("id") int id, @Bind("locale") String locale, @Bind("type") AdditionalServiceText.TextType type, @Bind("value") String value);


}
