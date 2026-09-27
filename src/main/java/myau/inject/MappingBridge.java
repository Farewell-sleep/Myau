package myau.inject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MappingBridge {
    private static final Map<String, String> NOTCH_CLASSES = new HashMap<String, String>();
    private static final Map<String, Map<String, String[]>> FIELDS = new HashMap<String, Map<String, String[]>>();
    private static final Map<String, Map<String, String[]>> METHODS = new HashMap<String, Map<String, String[]>>();
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<String, Class<?>>();
    private static final Map<String, Field> FIELD_CACHE = new HashMap<String, Field>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<String, Method>();
    private static final java.util.Set<String> FAILURES = new java.util.LinkedHashSet<String>();
    private MappingBridge() {
    }
    static {
        load();
    }
    public static List<String> failures() {
        return new ArrayList<String>(FAILURES);
    }
    public static String notchClass(String mcpName) {
        return NOTCH_CLASSES.get(mcpName);
    }

    public static Class<?> findClass(String mcpName) {
        Class<?> cached = CLASS_CACHE.get(mcpName);
        if (cached != null) {
            return cached;
        }
        ClassLoader loader = MappingBridge.class.getClassLoader();
        Class<?> found = tryClass(mcpName, loader);
        if (found == null) {
            String notch = NOTCH_CLASSES.get(mcpName);
            if (notch != null) {
                found = tryClass(notch, loader);
            }
        }
        if (found == null) {
            if (FAILURES.add("class " + mcpName)) {
                Log.line("unresolved class " + mcpName);
            }
            return null;
        }
        CLASS_CACHE.put(mcpName, found);
        return found;
    }
    private static Class<?> tryClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return null;
        }
    }

    public static Field field(String owner, String mcp, Class<?> type) {
        String key = owner + "#" + mcp;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> cls = findClass(owner);
        if (cls == null) {
            return null;
        }
        Field found = null;
        Map<String, String[]> names = FIELDS.get(owner);
        String[] alt = names == null ? null : names.get(mcp);
        for (String candidate : candidates(mcp, alt)) {
            if (candidate == null) {
                continue;
            }
            found = declaredField(cls, candidate);
            if (found != null) {
                break;
            }
        }
        if (found == null && type != null) {
            found = fieldByType(cls, type);
        }
        if (found == null) {

            if (FAILURES.add("field " + owner + "." + mcp)) {
                Log.line("unresolved field " + owner + "." + mcp);
            }
            return null;
        }
        found.setAccessible(true);
        FIELD_CACHE.put(key, found);
        return found;
    }
    private static Field declaredField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
    private static Field fieldByType(Class<?> cls, Class<?> type) {
        Field match = null;
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != type || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (match != null) {
                    return null;
                }
                match = f;
            }
            if (match != null) {
                return match;
            }
        }
        return match;
    }
    public static Method method(String owner, String mcp, Class<?>... params) {
        StringBuilder key = new StringBuilder(owner).append('#').append(mcp);
        for (Class<?> p : params) {
            key.append(';').append(p.getName());
        }
        Method cached = METHOD_CACHE.get(key.toString());
        if (cached != null) {
            return cached;
        }
        Class<?> cls = findClass(owner);
        if (cls == null) {
            return null;
        }
        Method found = null;
        Map<String, String[]> names = METHODS.get(owner);
        String[] alt = names == null ? null : names.get(mcp);
        for (String candidate : candidates(mcp, alt)) {
            if (candidate == null) {
                continue;
            }
            found = declaredMethod(cls, candidate, params);
            if (found != null) {
                break;
            }
        }
        if (found == null) {
            found = methodByParams(cls, params);
        }
        if (found == null) {
            if (FAILURES.add("method " + owner + "." + mcp)) {
                Log.line("unresolved method " + owner + "." + mcp);
            }
            return null;
        }
        found.setAccessible(true);
        METHOD_CACHE.put(key.toString(), found);
        return found;
    }
    private static Method declaredMethod(Class<?> cls, String name, Class<?>[] params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
    private static Method methodByParams(Class<?> cls, Class<?>[] params) {
        Method match = null;
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                Class<?>[] actual = m.getParameterTypes();
                if (actual.length != params.length) {
                    continue;
                }
                boolean same = true;
                for (int i = 0; i < actual.length; i++) {
                    if (actual[i] != params[i]) {
                        same = false;
                        break;
                    }
                }
                if (!same) {
                    continue;
                }
                if (match != null) {
                    return null;
                }
                match = m;
            }
            if (match != null) {
                return match;
            }
        }
        return match;
    }
    private static String[] candidates(String mcp, String[] alt) {
        if (alt == null) {
            return new String[]{mcp};
        }
        List<String> out = new ArrayList<String>();
        out.add(mcp);
        for (String group : alt) {
            if (group == null) {
                continue;
            }
            for (String name : group.split("\\|")) {
                if (name.length() > 0 && !out.contains(name)) {
                    out.add(name);
                }
            }
        }
        return out.toArray(new String[out.size()]);
    }
    public static java.util.Set<String> descriptors(String mcpDescriptor) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        if (mcpDescriptor == null) {
            return out;
        }
        out.add(mcpDescriptor);
        StringBuilder obfuscated = new StringBuilder();
        int i = 0;
        while (i < mcpDescriptor.length()) {
            char c = mcpDescriptor.charAt(i);
            if (c != 'L') {
                obfuscated.append(c);
                i++;
                continue;
            }
            int end = mcpDescriptor.indexOf(';', i);
            if (end < 0) {
                obfuscated.append(mcpDescriptor.substring(i));
                break;
            }
            String internal = mcpDescriptor.substring(i + 1, end);
            String notch = NOTCH_CLASSES.get(internal.replace('/', '.'));
            obfuscated.append('L')
                    .append(notch == null ? internal : notch.replace('.', '/'))
                    .append(';');
            i = end + 1;
        }
        out.add(obfuscated.toString());
        return out;
    }
    public static String[] methodNames(String owner, String mcp) {
        Map<String, String[]> names = METHODS.get(owner);
        return candidates(mcp, names == null ? null : names.get(mcp));
    }
    public static String[] fieldNames(String owner, String mcp) {
        Map<String, String[]> names = FIELDS.get(owner);
        return candidates(mcp, names == null ? null : names.get(mcp));
    }
    private static void load() {
        InputStream in = MappingBridge.class.getResourceAsStream("/myau_mappings.json");
        if (in == null) {
            FAILURES.add("myau_mappings.json missing from the jar");
            return;
        }
        try {
            StringBuilder text = new StringBuilder();
            InputStreamReader reader = new InputStreamReader(in, "UTF-8");
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) > 0) {
                text.append(buf, 0, read);
            }
            reader.close();
            parse(text.toString());
        } catch (Throwable t) {
            FAILURES.add("myau_mappings.json unreadable: " + t);
        }
    }
    private static void parse(String json) {
        String classes = section(json, "\"classes\"");
        for (String[] pair : pairs(classes)) {
            NOTCH_CLASSES.put(pair[0], pair[1]);
        }
        parseMembers(section(json, "\"fields\""), FIELDS);
        parseMembers(section(json, "\"methods\""), METHODS);
    }
    private static void parseMembers(String block, Map<String, Map<String, String[]>> into) {
        if (block == null) {
            return;
        }
        int i = 0;
        while (true) {
            int keyStart = block.indexOf('"', i);
            if (keyStart < 0) {
                return;
            }
            int keyEnd = block.indexOf('"', keyStart + 1);
            String owner = block.substring(keyStart + 1, keyEnd);
            int open = block.indexOf('{', keyEnd);
            if (open < 0) {
                return;
            }
            int close = matching(block, open);
            String body = block.substring(open + 1, close);
            Map<String, String[]> members = new HashMap<String, String[]>();
            int j = 0;
            while (true) {
                int nameStart = body.indexOf('"', j);
                if (nameStart < 0) {
                    break;
                }
                int nameEnd = body.indexOf('"', nameStart + 1);
                String member = body.substring(nameStart + 1, nameEnd);
                int mOpen = body.indexOf('{', nameEnd);
                if (mOpen < 0) {
                    break;
                }
                int mClose = matching(body, mOpen);
                String entry = body.substring(mOpen + 1, mClose);
                members.put(stripDescriptor(member),
                        new String[]{value(entry, "srg"), value(entry, "notch")});
                j = mClose + 1;
            }
            into.put(owner, members);
            i = close + 1;
        }
    }
    private static String stripDescriptor(String key) {
        int paren = key.indexOf('(');
        return paren < 0 ? key : key.substring(0, paren);
    }
    private static String section(String json, String key) {
        int at = json.indexOf(key);
        if (at < 0) {
            return null;
        }
        int open = json.indexOf('{', at);
        return json.substring(open + 1, matching(json, open));
    }
    private static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return s.length() - 1;
    }
    private static String value(String entry, String key) {
        int at = entry.indexOf('"' + key + '"');
        if (at < 0) {
            return null;
        }
        int colon = entry.indexOf(':', at);
        if (colon < 0) {
            return null;
        }
        int end = colon + 1;
        while (end < entry.length() && entry.charAt(end) != ',' && entry.charAt(end) != '}') {
            end++;
        }
        String raw = entry.substring(colon + 1, end).trim();
        if (raw.length() < 2 || raw.charAt(0) != '"') {
            return null;
        }
        return raw.substring(1, raw.length() - 1);
    }
    private static List<String[]> pairs(String block) {
        List<String[]> out = new ArrayList<String[]>();
        if (block == null) {
            return out;
        }
        int i = 0;
        while (true) {
            int a = block.indexOf('"', i);
            if (a < 0) {
                return out;
            }
            int b = block.indexOf('"', a + 1);
            int c = block.indexOf('"', b + 1);
            if (c < 0) {
                return out;
            }
            int d = block.indexOf('"', c + 1);
            out.add(new String[]{block.substring(a + 1, b), block.substring(c + 1, d)});
            i = d + 1;
        }
    }
}
