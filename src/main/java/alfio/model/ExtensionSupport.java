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
package alfio.model;

import alfio.extension.ExtensionMetadata;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class ExtensionSupport {

    private final Integer id;
    private final String path;
    private final String name;
    private final String hash;
    private final boolean enabled;
    private final boolean async;
    private final String script;
    private final ExtensionMetadata extensionMetadata;


    public ExtensionSupport(@Column("es_id") Integer id,
                            @Column("path") String path,
                            @Column("name") String name,
                            @Column("hash") String hash,
                            @Column("enabled") boolean enabled,
                            @Column("async") boolean async,
                            @Column("script") String script,
                            @Column("metadata") @JSONData ExtensionMetadata extensionMetadata) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.hash = hash;
        this.enabled = enabled;
        this.async = async;
        this.script = script;
        this.extensionMetadata = extensionMetadata;
    }


    @Getter
    public static class ScriptPathNameHash {
        private final String path;
        private final String name;
        private final String hash;

        public ScriptPathNameHash(@Column("path") String path,
                                  @Column("name") String name,
                                  @Column("hash") String hash) {
            this.path = path;
            this.name = name;
            this.hash = hash;
        }
    }

    @Getter
    public static class ExtensionParameterKeyValue {
        private final String name;
        private final String configurationLevel;
        private final String configurationPath;
        private final String configurationValue;

        public ExtensionParameterKeyValue(@Column("ecm_name") String name,
                                          @Column("ecm_configuration_level") String configurationLevel,
                                          @Column("conf_path") String configurationPath,
                                          @Column("conf_value") String configurationValue) {
            this.name = name;
            this.configurationLevel = configurationLevel;
            this.configurationPath = configurationPath;
            this.configurationValue = configurationValue;
        }
    }

    @Getter
    public static class ExtensionParameterMetadataAndValue {
        private final int id;
        private final String name;
        private final String extensionDisplayName;
        private final String configurationLevel;
        private final String description;
        private final String type;
        private final boolean mandatory;
        private final String path;
        private final int extensionId;
        private final String extensionName;
        private final String configurationPath;
        private final String configurationValue;


        public ExtensionParameterMetadataAndValue(@Column("ecm_id") int id,
                                                  @Column("ecm_name") String name,
                                                  @Column("ecm_configuration_level") String configurationLevel,
                                                  @Column("ecm_description") String description,
                                                  @Column("ecm_type") String type,
                                                  @Column("ecm_mandatory") boolean mandatory,
                                                  @Column("path") String path,
                                                  @Column("es_id") int extensionId,
                                                  @Column("name") String extensionName,
                                                  @Column("display_name") String extensionDisplayName,
                                                  @Column("conf_path") String configurationPath,
                                                  @Column("conf_value") String configurationValue) {
            this.id = id;
            this.name = name;
            this.configurationLevel = configurationLevel;
            this.description = description;
            this.type = type;
            this.mandatory = mandatory;
            this.path = path;
            this.extensionId = extensionId;
            this.extensionName = extensionName;
            this.extensionDisplayName = extensionDisplayName;
            this.configurationPath = configurationPath;
            this.configurationValue = configurationValue;
        }

        //for compatibility
        public String getComponentType() {
            return type;
        }

        public String getValue() {
            return configurationValue;
        }

        public String getConfigurationPathLevel() {
            return configurationLevel;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class ExtensionMetadataValue {
        private final int id;
        private final String value;
    }


    @Getter
    public static class NameAndValue {
        private final String name;
        private final String value;

        public NameAndValue(@Column("ecm_name") String name,
                            @Column("conf_value") String value) {
            this.name = name;
            this.value = value;
        }
    }

    @Getter
    public static class ExtensionMetadataIdAndName {
        private final int id;
        private final String name;

        public ExtensionMetadataIdAndName(@Column("ecm_id") int id,
                                          @Column("ecm_name") String name) {
            this.id = id;
            this.name = name;
        }
    }
}
