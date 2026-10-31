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
package alfio.extension;

import alfio.repository.system.AdminJobQueueRepository;
import alfio.util.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the extension scripts see the Java lists and maps they receive (payload, HTTP responses, JSON.parse, HashMap,
 * extension parameters) and how they return them, to detect backwards incompatible changes when upgrading Rhino
 * or changing the sandbox (alfio.extension.support).
 * <p>
 * The expected values were recorded with Rhino 1.7.15.1 and are unchanged in 1.9.2, except "for...of" (a syntax error
 * before). Note that the Java maps behave like JavaScript objects (FEATURE_ENABLE_JAVA_MAP_ACCESS): their keys are
 * properties, so their methods (get, put, size...) cannot be called. Lists support both indexes and methods.
 */
class ScriptJavaInteropTest {

    private static final Pattern JS_ERROR = Pattern.compile("\\b[A-Z][a-zA-Z]*Error\\b");
    private static ScriptingExecutionService scriptingExecutionService;

    @BeforeAll
    static void init() {
        Supplier<Executor> executorSupplier = () -> Runnable::run;
        scriptingExecutionService = new ScriptingExecutionService(Mockito.mock(HttpClient.class), Mockito.mock(AdminJobQueueRepository.class), executorSupplier);
    }

    public static class Item {
        private final int id;
        private final String name;
        private final List<String> tags;
        private final Map<String, Object> attributes;

        public Item(int id, String name, List<String> tags, Map<String, Object> attributes) {
            this.id = id;
            this.name = name;
            this.tags = tags;
            this.attributes = attributes;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<String> getTags() {
            return tags;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
            // java.util.List, e.g. "reservations", "ticketUUIDs"
            c("list index", "return list[0] + list[2];", "ac"),
            c("list length", "return list.length;", "3"),
            c("list size/get", "return list.size() + list.get(1);", "3b"),
            c("list contains/indexOf/isEmpty", "return [list.contains('b'), list.indexOf('c'), list.isEmpty()].join();", "true,2,false"),
            c("list index out of range", "return typeof list[10];", "undefined"),
            c("list classic for", "var s = ''; for (var i = 0; i < list.length; i++) { s += list[i]; } return s;", "abc"),
            c("list for in", "var s = []; for (var k in list) { s.push(k); } return s.join();", "0,1,2"),
            // syntax error in Rhino 1.7.15.1
            c("list for of", "var s = ''; for (var v of list) { s += v; } return s;", "abc"),
            c("list forEach", "var s = ''; list.forEach(function(v) { s += v; }); return s;", "abc"),
            c("list Array.prototype.map", "return Array.prototype.map.call(list, function(v) { return v.toUpperCase(); }).join();", "A,B,C"),
            c("list typeof/isArray", "return typeof list + ',' + Array.isArray(list);", "object,false"),
            c("list string element", "return typeof list[0] + ',' + (list[0] === 'a') + ',' + (list[0] == 'a') + ',' + list[0].toUpperCase();", "object,false,true,A"),
            c("list number element", "return (numbers[0] + 1) + ',' + typeof numbers[0] + ',' + (numbers[1] === 2) + ',' + (numbers[1] == 2);", "2,object,false,true"),
            c("list of beans", "return items[0].name + items[1].id + items[0].getName();", "first2first"),
            c("bean list getter", "return items[0].tags[1] + items[0].tags.length + items[0].tags.size();", "y22"),
            c("bean map getter dot", "return items[0].attributes.k + items[0].attributes['k'];", "vv"),
            c("bean map getter get()", "return items[0].attributes.get('k');", "ERROR InvalidScriptException TypeError"),
            // java.util.Collection, e.g. "reservationIds"
            c("collection size/contains", "return ids.size() + ',' + ids.contains('r1');", "2,true"),
            c("collection iterator", "var s = ''; for (var it = ids.iterator(); it.hasNext();) { s += it.next(); } return s;", "r1r2"),
            // syntax error in Rhino 1.7.15.1
            c("collection for of", "var s = ''; for (var v of ids) { s += v; } return s;", "r1r2"),
            c("collection index", "var x = ids[0]; return typeof x;", "ERROR EvaluatorException"),
            // java.util.Map, e.g. "additionalInfo", "categoriesById"
            c("map dot", "return map.a + map.b;", "1x"),
            c("map bracket", "return map['a'] + map['b'];", "1x"),
            c("map get()", "return map.get('b');", "ERROR InvalidScriptException TypeError"),
            c("map containsKey()", "return map.containsKey('a');", "ERROR InvalidScriptException TypeError"),
            c("map size()", "return map.size();", "ERROR InvalidScriptException TypeError"),
            c("map typeof get", "return typeof map.get;", "object"),
            c("map null value", "return String(map.n) + ',' + (map.n === null) + ',' + (map['n'] === null);", "null,true,true"),
            c("map missing key", "return typeof map.missing + ',' + typeof map['missing'];", "object,object"),
            c("map in operator", "return ('a' in map) + ',' + ('missing' in map);", "true,false"),
            c("map Object.keys", "return Object.keys(map).join();", "a,b,n,nested,items"),
            c("map for in", "var k = []; for (var key in map) { k.push(key); } return k.join();", "a,b,n,nested,items"),
            c("map nested map", "return map.nested.x + 1;", "11"),
            c("map nested list", "return map.items[1] + map.items.length;", "i12"),
            c("map number value", "return (map.a + 1) + ',' + typeof map.a;", "2,object"),
            c("map key named like a method", "return typeof collision.size + ',' + collision['size'] + ',' + collision.get;", "object,s,g"),
            c("map integer keys", "return intMap[1] + intMap[2];", "onetwo"),
            c("immutable empty map", "return typeof immutable.foo + ',' + typeof immutable['foo'];", "object,object"),
            c("immutable empty map size()", "return immutable.size();", "ERROR InvalidScriptException TypeError"),
            c("map typeof", "return typeof map + ',' + Array.isArray(map);", "object,false"),
            // values created by the scripts
            c("HashMap property write/read", "var h = new HashMap(); h.x = 1; h['y'] = 'two'; return h.x + ',' + h['y'] + ',' + JSON.stringify(h);", "1,two,{\"x\":1,\"y\":\"two\"}"),
            c("HashMap put()", "var h = new HashMap(); h.put('x', 1); return h.x;", "ERROR InvalidScriptException TypeError"),
            c("JSON.parse", "var p = JSON.parse('{\"a\": 1, \"l\": [1, 2], \"o\": {\"k\": \"v\"}}'); return p.a + ',' + p.l[1] + ',' + p.l.length + ',' + p.o.k;", "1,2,2,v"),
            c("getJsonBody", "return jsonBody[0].random + ',' + jsonBody.length + ',' + jsonBody[0].nested.k[1] + ',' + typeof jsonBody[0].random;", "42,1,2,object"),
            c("extensionParameters", "return extensionParameters.apiKey + ',' + extensionParameters.count + ',' + typeof extensionParameters.count;", "secret,3,object"),
            // results: JSON.stringify, then GSON.fromJson (see ExtensionService.EVALUATE_RESULT)
            c("JSON.stringify list", "return JSON.stringify(list) + JSON.stringify(numbers);", "[\"a\",\"b\",\"c\"][1,2,3]"),
            c("JSON.stringify map", "return JSON.stringify(map);", "{\"a\":1,\"b\":\"x\",\"nested\":{\"x\":10},\"items\":[\"i0\",\"i1\"]}"),
            c("JSON.stringify object with java values", "return JSON.stringify({l: list, m: map.nested, i: items[0].tags, n: numbers});", "{\"l\":[\"a\",\"b\",\"c\"],\"m\":{\"x\":10},\"i\":[\"x\",\"y\"],\"n\":[1,2,3]}"),
            c("result through GSON", "var res = {l: list, m: map.nested, n: numbers}; res = GSON.fromJson(JSON.stringify(res), returnClass); return GSON.toJson(res);", "{\"l\":[\"a\",\"b\",\"c\"],\"m\":{\"x\":10.0},\"n\":[1.0,2.0,3.0]}"),
            // changes made by the scripts are visible to Java
            c("mutation list", "list.add('d'); list[0] = 'z'; return list.size();", "4 | list=[z, b, c, d] | map={a=1(Integer), b=x(String), n=null(null), nested={x=10}(Map1), items=[i0, i1](List12)}"),
            c("mutation map put()", "map.put('c', 3); return 'done';", "ERROR InvalidScriptException TypeError | list=[a, b, c] | map={a=1(Integer), b=x(String), n=null(null), nested={x=10}(Map1), items=[i0, i1](List12)}"),
            c("mutation map property", "map.c = 3; map['e'] = 'dot'; delete map.a; return 'done';", "done | list=[a, b, c] | map={a=1(Integer), b=x(String), n=null(null), nested={x=10}(Map1), items=[i0, i1](List12), c=3.0(Double), e=dot(String)}")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void interop(String name, String body, String expected) {
        var params = params();
        var errors = new ArrayList<String>();
        ExtensionLogger logger = new ExtensionLogger() {
            @Override
            public void logError(String msg) {
                errors.add(msg);
            }
        };
        String actual;
        try {
            var script = "var __result = (function() { " + body + " })(); String(__result);";
            actual = String.valueOf(scriptingExecutionService.executeScript("interop", script, params, Object.class, logger));
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            // the script error (e.g. a TypeError) is logged, the exception only wraps it.
            // Only the kind of error is compared: the messages change between Rhino versions
            actual = "ERROR " + root.getClass().getSimpleName();
            var jsError = JS_ERROR.matcher(String.join(" ", errors));
            if (jsError.find()) {
                actual += " " + jsError.group();
            }
        }
        if (name.startsWith("mutation")) {
            actual += " | list=" + params.get("list") + " | map=" + describe((Map<?, ?>) params.get("map"));
        }
        System.out.println("INTEROP\t" + name + "\t" + actual);
        assertEquals(expected, actual);
    }

    private static Arguments c(String name, String body, String expected) {
        return Arguments.of(name, body, expected);
    }

    private static String describe(Map<?, ?> map) {
        return map.entrySet().stream()
            .map(e -> {
                var value = e.getValue();
                String type = "null";
                if (value != null) {
                    type = value.getClass().getSimpleName();
                }
                return e.getKey() + "=" + value + "(" + type + ")";
            })
            .collect(Collectors.joining(", ", "{", "}"));
    }

    private static Map<String, Object> params() {
        var map = new LinkedHashMap<String, Object>();
        map.put("a", 1);
        map.put("b", "x");
        map.put("n", null);
        map.put("nested", Map.of("x", 10));
        map.put("items", List.of("i0", "i1"));
        var params = new HashMap<String, Object>();
        params.put("list", new ArrayList<>(List.of("a", "b", "c")));
        params.put("numbers", List.of(1, 2, 3));
        params.put("items", List.of(
            new Item(1, "first", List.of("x", "y"), Map.of("k", "v")),
            new Item(2, "second", List.of(), Map.of())));
        params.put("ids", new LinkedHashSet<>(List.of("r1", "r2")));
        params.put("map", map);
        params.put("collision", Map.of("size", "s", "get", "g"));
        params.put("intMap", Map.of(1, "one", 2, "two"));
        params.put("immutable", Map.of());
        params.put("jsonBody", Json.GSON.fromJson("[{\"random\": 42, \"nested\": {\"k\": [1, 2]}}]", Object.class));
        params.put(ScriptingExecutionService.EXTENSION_CONFIGURATION_PARAMETERS, Map.of("apiKey", "secret", "count", 3));
        return params;
    }
}
