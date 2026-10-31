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
import org.mozilla.javascript.lc.type.TypeInfo;

import java.util.List;
import java.util.Map;

// source: https://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
public class SandboxWrapFactory extends WrapFactory {

    // Since Rhino 1.9, Rhino calls only this variant: the one with "Class<?> staticType" just delegates to it.
    // Overriding the Class<?> variant instead would silently disable the sandbox.
    @Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, TypeInfo staticType) {
        if (List.class.isAssignableFrom(javaObject.getClass())) {
            return new SandboxNativeJavaList(scope, javaObject, staticType);
        }
        if (Map.class.isAssignableFrom(javaObject.getClass())) {
            return new SandboxNativeJavaMap(scope, javaObject, staticType);
        }
        return new SandboxNativeJavaObject(scope, javaObject, staticType);
    }
}
