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

import alfio.extension.ExtensionMetadata;
import alfio.model.ExtensionCapabilitySummary;
import alfio.model.ExtensionSupport;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@QueryRepository
public interface ExtensionRepository {

    @Query("insert into extension_support(path, name, display_name, hash, enabled, async, script, metadata) values " +
        " (:path, :name, :displayName, :hash, :enabled, :async, :script, :metadata::jsonb)")
    int insert(@Bind("path") String path,
               @Bind("name") String name,
               @Bind("displayName") String displayName,
               @Bind("hash") String hash,
               @Bind("enabled") boolean enabled,
               @Bind("async") boolean async,
               @Bind("script") String script,
               @Bind("metadata") @JSONData ExtensionMetadata extensionMetadata);

    @Query("update extension_support set display_name = :displayName," +
        " hash = :hash, enabled = :enabled, async = :async, script = :script, metadata = :metadata::jsonb" +
        " where path = :path and name = :name")
    int update(@Bind("path") String path,
               @Bind("name") String name,
               @Bind("displayName") String displayName,
               @Bind("hash") String hash,
               @Bind("enabled") boolean enabled,
               @Bind("async") boolean async,
               @Bind("script") String script,
               @Bind("metadata") @JSONData ExtensionMetadata extensionMetadata);

    @Query("update extension_support set enabled = :enabled where path = :path and name = :name")
    int toggle(@Bind("path") String path, @Bind("name") String name, @Bind("enabled") boolean enabled);

    @Query("insert into extension_event(es_id_fk, event) values " +
        " (:extensionId, :event)")
    int insertEvent(@Bind("extensionId") int extensionId, @Bind("event") String event);

    @Query("select es_id from extension_support where path = :path and name = :name")
    int getExtensionIdFor(@Bind("path") String path, @Bind("name") String name);

    @Query("select script from extension_support where path = :path and name = :name")
    String getScript(@Bind("path") String path, @Bind("name") String name);

    @Query("select * from extension_support where path = :path and name = :name")
    Optional<ExtensionSupport> getSingle(@Bind("path") String path, @Bind("name") String name);

    @Query("select * from extension_support where path in (:paths) and name = :name limit 1")
    Optional<ExtensionSupport> getSingle(@Bind("paths") Collection<String> paths, @Bind("name") String name);

    @Query("delete from extension_event where es_id_fk = (select es_id from extension_support where path = :path and name = :name)")
    int deleteEventsForPath(@Bind("path") String path, @Bind("name") String name);

    @Query("delete from extension_support where path = :path and name = :name")
    int deleteScriptForPath(@Bind("path") String path, @Bind("name") String name);

    @Query("select * from extension_support order by path, name")
    List<ExtensionSupport> listAll();

    @Query("select a3.es_id, a3.path, a3.name, a3.hash from " +
        " (select a1.* from " +
        " (select es_id, path, name, hash from extension_support where enabled = true and async = :async and (path in (:possiblePaths))) a1 " +
        " left outer join (select es_id, path, name from extension_support where enabled = true and async = :async and (path in (:possiblePaths))) a2 on " +
        " (a1.es_id = a2.es_id) and length(a1.path) < length(a2.path) where a2.path is null) a3 " +
        " inner join extension_event on es_id_fk = a3.es_id where event = :event order by a3.name, a3.path")
    List<ExtensionSupport.ScriptPathNameHash> findActive(@Bind("possiblePaths") Set<String> possiblePaths,
                                                         @Bind("async") boolean async,
                                                         @Bind("event") String event);

    @Query("delete from extension_configuration_metadata where  ecm_es_id_fk = :extensionId")
    int deleteExtensionParameter(@Bind("extensionId") int extensionId);

    @Query("select ecm_name, ecm_configuration_level, conf_path, conf_value from extension_configuration_metadata " +
        " inner join extension_configuration_metadata_value on fk_ecm_id = ecm_id " +
        " where  ecm_es_id_fk =  :extensionId ")
    List<ExtensionSupport.ExtensionParameterKeyValue> findExtensionParameterKeyValue(@Bind("extensionId") int extensionId);

    @Query("insert into extension_configuration_metadata(ecm_es_id_fk, ecm_name, ecm_description, ecm_type, ecm_configuration_level, ecm_mandatory) " +
        " values (:extensionId, :name, :description, :type, :configurationLevel, :mandatory)")
    @AutoGeneratedKey("ecm_id")
    AffectedRowCountAndKey<Integer> registerExtensionConfigurationMetadata(@Bind("extensionId") int extensionId,
                                               @Bind("name") String name,
                                               @Bind("description") String description,
                                               @Bind("type") String type,
                                               @Bind("configurationLevel") String configurationLevel,
                                               @Bind("mandatory") boolean mandatory);


    @Query("select ecm_id, ecm_name, ecm_configuration_level, ecm_description, ecm_type, ecm_mandatory, path, es_id, name, display_name, conf_path, conf_value"+
        " from extension_configuration_metadata " +
        " inner join extension_support on es_id = ecm_es_id_fk " +
        " left outer join extension_configuration_metadata_value on ecm_id = fk_ecm_id and (conf_path is null or (conf_path in (:possiblePaths))) " +
        " where ecm_configuration_level = :configurationLevel and (path in (:possiblePaths) or path like :pathPattern) order by es_id, name, ecm_id, ecm_name")
    List<ExtensionSupport.ExtensionParameterMetadataAndValue> getParametersForLevelAndPath(
        @Bind("configurationLevel") String configurationLevel,
        @Bind("possiblePaths") Set<String> possiblePaths,
        @Bind("pathPattern") String pathPattern);


    @Query("delete from extension_configuration_metadata_value where fk_ecm_id in (select ecm_id from extension_configuration_metadata where ECM_CONFIGURATION_LEVEL = :confLevel) and conf_path = :confPath")
    int deleteSettingValue(@Bind("confLevel") String level, @Bind("confPath") String confPath);

    @Query("delete from extension_configuration_metadata_value where fk_ecm_id = :id and conf_path = :path")
    int deleteSettingValue(@Bind("id") int id, @Bind("path") String path);

    @Query("insert into extension_configuration_metadata_value(fk_ecm_id, conf_path, conf_value) values (:ecmId, :confPath, :value)")
    int insertSettingValue(@Bind("ecmId") int ecmId, @Bind("confPath") String confPath, @Bind("value") String value);

    @Query(type = QueryType.TEMPLATE, value = "insert into extension_configuration_metadata_value(fk_ecm_id, conf_path, conf_value) values (:ecmId, :confPath, :value)")
    String bulkInsertSettingValue();

    @Query("select ecm_name, conf_value from " +
        "" +
        "(select ecm_name, conf_value, ecm_configuration_level, conf_path, " +
        "(case when ecm_configuration_level = 'EVENT' then 0 when ecm_configuration_level = 'ORGANIZATION' then 1 else 2 end) as priority from extension_configuration_metadata inner join " +
        "extension_configuration_metadata_value on ecm_id = fk_ecm_id " +
        "where ecm_es_id_fk = (SELECT es_id from extension_support where path = :path and name = :name) and conf_path in (:allPaths)) a1 " +
        "" +
        "where (ecm_name, priority) in " +
        "" +
        "(select ecm_name, min(priority) selected_priority from ( " +
        "select ecm_name, conf_value, ecm_configuration_level, conf_path, " +
        "(case when ecm_configuration_level = 'EVENT' then 0 when ecm_configuration_level = 'ORGANIZATION' then 1 else 2 end) as priority from extension_configuration_metadata inner join " +
        "extension_configuration_metadata_value on ecm_id = fk_ecm_id " +
        "where ecm_es_id_fk = (SELECT es_id from extension_support where path = :path and name = :name) and conf_path in (:allPaths)) a2 group by ecm_name)")
    List<ExtensionSupport.NameAndValue> findParametersForScript(@Bind("name") String name, @Bind("path") String path, @Bind("allPaths") Set<String> allPaths);

    @Query("select distinct ecm_name from extension_configuration_metadata where ecm_es_id_fk = (SELECT es_id from extension_support where path = :path and name = :name) and ecm_mandatory is true")
    List<String> findMandatoryParametersForScript(@Bind("name") String name, @Bind("path") String path);

    @Query("select ecm_id, ecm_name from extension_configuration_metadata where ecm_es_id_fk = :extensionId")
    List<ExtensionSupport.ExtensionMetadataIdAndName> findAllParametersForExtension(@Bind("extensionId") int extensionId);

    @Query("select count(a3.*) from " +
        " (select a1.es_id from " +
        " (select es_id, path from extension_capabilities where (path in (:possiblePaths)) and capability in(:capabilities)) a1 " +
        " left outer join (select es_id, path from extension_capabilities where (path in (:possiblePaths)) and capability in(:capabilities)) a2 on " +
        " (a1.es_id = a2.es_id) and length(a1.path) < length(a2.path) where a2.path is null) a3 ")
    int countScriptsSupportingCapability(@Bind("possiblePaths") Set<String> paths,
                                         @Bind("capabilities") List<String> capabilities);

    @Query("select distinct a3.capability, a3.capability_detail from " +
        " (select a1.es_id, a1.capability, a1.capability_detail from " +
        " (select es_id, path, capability, capability_detail from extension_capabilities where (path in (:possiblePaths)) and capability in(:capabilities)) a1 " +
        " left outer join (select es_id, path, capability, capability_detail from extension_capabilities where (path in (:possiblePaths)) and capability in(:capabilities)) a2 on " +
        " (a1.es_id = a2.es_id) and length(a1.path) < length(a2.path) where a2.path is null) a3 ")
    List<ExtensionCapabilitySummary> getSupportedCapabilities(@Bind("possiblePaths") Set<String> paths,
                                                              @Bind("capabilities") Collection<String> capabilities);

    @Query("select a3.es_id, a3.path, a3.name, a3.hash from " +
        " (select a1.es_id, a1.path, a1.name, a1.hash from " +
        " (select es_id, path, name, hash from extension_capabilities where (path in (:possiblePaths)) and capability = :capability) a1 " +
        " left outer join (select es_id, path, name, hash from extension_capabilities where (path in (:possiblePaths)) and capability = :capability) a2 on " +
        " (a1.es_id = a2.es_id) and length(a1.path) < length(a2.path) where a2.path is null) a3 limit 1")
    Optional<ExtensionSupport.ScriptPathNameHash> getFirstScriptForCapability(@Bind("possiblePaths") Set<String> paths,
                                                                              @Bind("capability") String capability);
}
