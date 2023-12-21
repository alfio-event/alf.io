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

import java.util.*;

@QueryRepository
public interface AdditionalServiceTextRepository {

    @Query("select id, additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk = :additionalServiceId")
    List<AdditionalServiceText> findAllByAdditionalServiceId(@Bind("additionalServiceId") int additionalServiceId);

    @Query("delete from additional_service_description where additional_service_id_fk = :additionalServiceId")
    int deleteAdditionalServiceTexts(@Bind("additionalServiceId") int additionalServiceId);

    @Query("select id, additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk = :additionalServiceId and type = :type")
    List<AdditionalServiceText> findAllByAdditionalServiceIdAndType(@Bind("additionalServiceId") int additionalServiceId, @Bind("type") AdditionalServiceText.TextType type);

    @Query("select id, additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk = :additionalServiceId and locale = :locale and type = :type")
    Optional<AdditionalServiceText> findByLocaleAndType(@Bind("additionalServiceId") int additionalServiceId, @Bind("locale") String locale, @Bind("type") AdditionalServiceText.TextType type);

    @Query("insert into additional_service_description(additional_service_id_fk, locale, type, value) values(:additionalServiceId, :locale, :type, :value)")
    int insert(@Bind("additionalServiceId") int additionalServiceId, @Bind("locale") String locale, @Bind("type") AdditionalServiceText.TextType type, @Bind("value") String value);

    @Query("update additional_service_description set locale = :locale, type = :type, value = :value where id = :id and additional_service_id_fk = :additionalServiceId")
    int update(@Bind("id") int id, @Bind("locale") String locale, @Bind("type") AdditionalServiceText.TextType type, @Bind("value") String value, @Bind("additionalServiceId") int additionalServiceId);


    @Query("select id, additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk in (:additionalServiceIds)")
    List<AdditionalServiceText> findAllByAdditionalServiceIds(@Bind("additionalServiceIds") Collection<Integer> additionalServiceIds);


    default Map<Integer, Map<AdditionalServiceText.TextType, Map<String, String>>> getDescriptionsByAdditionalServiceIds(Collection<Integer> additionalServiceIds) {
        if (additionalServiceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, Map<AdditionalServiceText.TextType, Map<String, String>>> res = new HashMap<>();
        findAllByAdditionalServiceIds(additionalServiceIds).forEach(t -> {
            var id = t.getAdditionalServiceId();

            if (!res.containsKey(id)) {
                res.put(id, new EnumMap<>(AdditionalServiceText.TextType.class));
            }

            if(!res.get(id).containsKey(t.getType())) {
                res.get(id).put(t.getType(), new HashMap<>());
            }

            res.get(id).get(t.getType()).put(t.getLocale(), t.getValue());
        });

        return res;

    }

    default AdditionalServiceText findBestMatchByLocaleAndType(int additionalServiceId, String locale, AdditionalServiceText.TextType type) {
        return findByLocaleAndType(additionalServiceId, locale, type)
            .orElseGet(() -> {
                List<AdditionalServiceText> texts = findAllByAdditionalServiceIdAndType(additionalServiceId, type);
                return !texts.isEmpty() ? texts.get(0) : new AdditionalServiceText(-1, additionalServiceId, locale, type, "N/A");
            });
    }


}
