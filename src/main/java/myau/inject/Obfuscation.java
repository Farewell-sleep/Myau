package myau.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

final class Obfuscation {
    enum Naming {
        SRG,
        NOTCH,
        MCP
    }
    private static final Map<String, String> CLASSES = new HashMap<String, String>();
    private static final Map<String, String> METHODS_EXACT = new HashMap<String, String>();
    private static final Map<String, String> FIELDS_EXACT = new HashMap<String, String>();
    private static final Map<String, String> METHODS_FLAT = new HashMap<String, String>();
    private static final Map<String, String> FIELDS_FLAT = new HashMap<String, String>();
    private static volatile Naming naming;
    private static volatile Remapper remapper;
    private static volatile boolean prepared;
    private Obfuscation() {
    }
    static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        resolveNaming();
        if (naming != Naming.SRG) {
            buildRemapper();
        }
    }
    static Naming naming() {
        Naming resolved = naming;
        if (resolved != null) {
            return resolved;
        }
        prepare();
        return naming;
    }
    private static void resolveNaming() {
        ClassLoader loader = Obfuscation.class.getClassLoader();
        Class<?> minecraft;
        try {
            minecraft = Class.forName("net.minecraft.client.Minecraft", false, loader);
        } catch (Throwable absent) {
            naming = Naming.NOTCH;
            Log.line("the game is obfuscated -- client classes will be remapped as they load");
            return;
        }
        naming = has(minecraft, "func_71410_x") ? Naming.SRG : Naming.MCP;
        Log.line(naming == Naming.SRG
                ? "the game uses SRG members -- no remapping needed"
                : "the game is fully named -- client classes will be remapped as they load");
    }
    private static boolean has(Class<?> owner, String method) {
        try {
            owner.getDeclaredMethod(method);
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }
    static boolean needsRemapping() {
        return naming() != Naming.SRG;
    }
    static byte[] remap(String internalName, byte[] classfile) {
        try {
            Remapper active = remapper();
            if (active == null) {
                return classfile;
            }
            ClassReader reader = new ClassReader(classfile);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, active), 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            StackTraceElement[] frames = t.getStackTrace();
            Log.line("remap failed for " + internalName + ": " + t
                    + (frames.length > 0 ? " at " + frames[0] : ""));
            return classfile;
        }
    }
    private static Remapper remapper() {
        return remapper;
    }

    private static void buildRemapper() {
        String table = naming == Naming.NOTCH ? "/myau_obf.tsv" : "/myau_mcp.tsv";
        if (!load(table)) {
            return;
        }
        remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                String mapped = CLASSES.get(internalName);
                return mapped == null ? internalName : mapped;
            }
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {

                if (isSrgName(name)) {
                    String override = METHODS_FLAT.get(name + "\t" + descriptor);
                    if (override != null) {
                        return override;
                    }
                }
                if (!isGameClass(owner)) {
                    return name;
                }
                String exact = METHODS_EXACT.get(owner + "\t" + name + "\t" + descriptor);
                if (exact != null) {
                    return exact;
                }
                String flat = METHODS_FLAT.get(name + "\t" + descriptor);
                return flat == null ? name : flat;
            }
            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                if (isSrgName(name)) {
                    String inherited = FIELDS_FLAT.get(name);
                    if (inherited != null) {
                        return inherited;
                    }
                }
                if (!isGameClass(owner)) {
                    return name;
                }
                String exact = FIELDS_EXACT.get(owner + "\t" + name);
                if (exact != null) {
                    return exact;
                }
                String flat = FIELDS_FLAT.get(name);
                return flat == null ? name : flat;
            }
        };
        Log.line("mapping table " + table + ": " + CLASSES.size() + " classes, "
                + METHODS_EXACT.size() + " methods, " + FIELDS_EXACT.size() + " fields");
    }
    static String mapDescriptor(String mcpDescriptor) {
        if (mcpDescriptor == null || CLASSES.isEmpty()) {
            return mcpDescriptor;
        }
        StringBuilder out = new StringBuilder(mcpDescriptor.length());
        int i = 0;
        while (i < mcpDescriptor.length()) {
            char c = mcpDescriptor.charAt(i);
            if (c != 'L') {
                out.append(c);
                i++;
                continue;
            }
            int end = mcpDescriptor.indexOf(';', i);
            if (end < 0) {
                out.append(mcpDescriptor.substring(i));
                break;
            }
            String internal = mcpDescriptor.substring(i + 1, end);
            String mapped = CLASSES.get(internal);
            out.append('L').append(mapped == null ? internal : mapped).append(';');
            i = end + 1;
        }
        return out.toString();
    }
    private static boolean isSrgName(String name) {
        return name.startsWith("func_") || name.startsWith("field_");
    }
    private static boolean isGameClass(String internalName) {
        return internalName.startsWith("net/minecraft/") || CLASSES.containsKey(internalName);
    }
    private static boolean load(String resource) {
        InputStream in = Obfuscation.class.getResourceAsStream(resource);
        if (in == null) {
            Log.line(resource + " is missing from the jar -- cannot remap");
            return false;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 1 << 16);
            Map<String, String> methodSeen = new HashMap<String, String>();
            Map<String, String> fieldSeen = new HashMap<String, String>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    continue;
                }
                if ("C".equals(parts[0])) {
                    CLASSES.put(parts[1], parts[2]);
                } else if ("M".equals(parts[0]) && parts.length == 5) {
                    METHODS_EXACT.put(parts[1] + "\t" + parts[2] + "\t" + parts[3], parts[4]);
                    remember(methodSeen, METHODS_FLAT, parts[2] + "\t" + parts[3], parts[4]);
                } else if ("F".equals(parts[0]) && parts.length == 4) {
                    FIELDS_EXACT.put(parts[1] + "\t" + parts[2], parts[3]);
                    remember(fieldSeen, FIELDS_FLAT, parts[2], parts[3]);
                }
            }
            return true;
        } catch (Throwable t) {
            Log.line("could not read " + resource + ": " + t);
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
                }
            }
        }
    }
    private static void remember(Map<String, String> seen, Map<String, String> flat,
                                 String key, String value) {
        String previous = seen.put(key, value);
        if (previous == null) {
            flat.put(key, value);
        } else if (!previous.equals(value)) {
            flat.remove(key);
        }
    }
}
