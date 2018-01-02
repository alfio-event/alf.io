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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class ScriptSupport {

    private final String path;
    private final String name;
    private final String hash;
    private final boolean enabled;
    private final boolean async;
    private final String script;
    private final String configuration;


    public ScriptSupport(@Column("path") String path,
                         @Column("name") String name,
                         @Column("hash") String hash,
                         @Column("enabled") boolean enabled,
                         @Column("async") boolean async,
                         @Column("script") String script,
                         @Column("configuration") String configuration) {
        this.path = path;
        this.name = name;
        this.hash = hash;
        this.enabled = enabled;
        this.async = async;
        this.script = script;
        this.configuration = configuration;
    }
}
