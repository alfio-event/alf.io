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
package alfio.extension.support;

import org.mozilla.javascript.*;

import java.util.List;
import java.util.Map;

// source: https://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
public class SandboxWrapFactory extends WrapFactory {

    @Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class<?> staticType) {
        if (List.class.isAssignableFrom(javaObject.getClass())) {
            return new SandboxNativeJavaList(scope, javaObject);
        }
        if (Map.class.isAssignableFrom(javaObject.getClass())) {
            return new SandboxNativeJavaMap(scope, javaObject);
        }
        return new SandboxNativeJavaObject(scope, javaObject, staticType);
    }
}
