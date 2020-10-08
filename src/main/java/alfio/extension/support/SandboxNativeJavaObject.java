package alfio.extension.support;

import alfio.extension.exception.OutOfBoundariesException;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

// source: https://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
public class SandboxNativeJavaObject extends NativeJavaObject {
    public SandboxNativeJavaObject(Scriptable scope, Object javaObject, Class staticType) {
        super(scope, javaObject, staticType);
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (name.equals("getClass")) {
            throw new OutOfBoundariesException("Out of boundaries class use.");
//            return NOT_FOUND;
        }

        return super.get(name, start);
    }
}
