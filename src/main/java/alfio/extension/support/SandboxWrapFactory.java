package alfio.extension.support;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

// source: https://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
public class SandboxWrapFactory extends WrapFactory {
    @Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
        return new SandboxNativeJavaObject(scope, javaObject, staticType);
    }
}
