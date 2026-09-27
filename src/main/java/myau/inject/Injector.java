package myau.inject;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public final class Injector {
    private Injector() {
    }
    public static void main(String[] args) {
        File self;
        try {
            self = new File(Injector.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Throwable t) {
            System.out.println("cannot work out where this jar is: " + t);
            return;
        }
        if (!self.getName().endsWith(".jar")) {
            System.out.println("run this from the built jar, not from loose classes: " + self);
            return;
        }
        Class<?> vmClass = attachClass();
        if (vmClass == null) {
            System.out.println("no attach API available.");
            System.out.println("run this with a JDK, not a JRE -- a JRE has no attach support.");
            return;
        }
        try {
            String pid = args.length > 0 ? args[0] : choose(vmClass);
            if (pid == null) {
                return;
            }
            System.out.println("attaching to " + pid);
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            try {
                vmClass.getMethod("loadAgent", String.class).invoke(vm, self.getAbsolutePath());
                System.out.println("agent loaded -- everything else appears in the game's own log");
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }
        } catch (Throwable t) {
            Throwable cause = t.getCause() == null ? t : t.getCause();
            System.out.println("attach failed: " + cause);
            System.out.println("Either the target runs with -XX:+DisableAttachMechanism, or it");
            System.out.println("belongs to another user, or its Java version is too far from this");
            System.out.println("one -- attaching from a modern JDK to a Java 8 game fails with");
            System.out.println("\"Failed to load agent library\". Run this on the same Java the");
            System.out.println("game uses.");
        }
    }
    private static String choose(Class<?> vmClass) throws Exception {
        List<Object> all = (List<Object>) vmClass.getMethod("list").invoke(null);
        List<Object> likely = new ArrayList<Object>();
        for (Object vm : all) {
            if (looksLikeMinecraft(displayName(vm))) {
                likely.add(vm);
            }
        }
        if (likely.size() == 1) {
            String id = id(likely.get(0));
            System.out.println("one candidate, using it: " + id);
            return id;
        }
        System.out.println("pass a pid. Java processes the attach API can see:");
        if (all.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Object vm : all) {
            String name = displayName(vm);
            System.out.println("  " + id(vm) + "  " + shorten(name)
                    + (looksLikeMinecraft(name) ? "   <-- looks like Minecraft" : ""));
        }
        if (likely.isEmpty()) {
            listProcessesTheAttachApiMissed(all);
        }
        return null;
    }
    private static void listProcessesTheAttachApiMissed(List<Object> listed) {
        java.util.Set<String> known = new java.util.HashSet<String>();
        for (Object vm : listed) {
            try {
                known.add(id(vm));
            } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            }
        }
        List<String[]> found = new ArrayList<String[]>();
        for (String image : new String[]{"java.exe", "javaw.exe"}) {
            found.addAll(tasklist(image, known));
        }
        if (found.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println("java processes the OS sees but the attach API does not:");
        for (String[] row : found) {
            System.out.println("  " + row[0] + "  " + row[1]);
        }
        System.out.println();
        System.out.println("A JVM started with -XX:-UsePerfData is missing from the list above");
        System.out.println("but can usually still be attached to. Try the pid directly:");
        System.out.println("  java -jar <this jar> <pid>");
    }
    private static List<String[]> tasklist(String image, java.util.Set<String> exclude) {
        List<String[]> rows = new ArrayList<String[]>();
        java.io.BufferedReader reader = null;
        try {
            Process process = new ProcessBuilder(
                    "tasklist", "/FI", "IMAGENAME eq " + image, "/FO", "CSV", "/NH")
                    .redirectErrorStream(true).start();
            reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split("\",\"");
                if (cells.length < 2) {
                    continue;
                }
                String pid = cells[1].replace("\"", "").trim();
                if (pid.length() == 0 || exclude.contains(pid)) {
                    continue;
                }
                rows.add(new String[]{pid, image});
            }
            process.waitFor();
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (java.io.IOException ignored) {
                }
            }
        }
        return rows;
    }

    private static String id(Object descriptor) throws Exception {
        return (String) descriptor.getClass().getMethod("id").invoke(descriptor);
    }
    private static String displayName(Object descriptor) throws Exception {
        return (String) descriptor.getClass().getMethod("displayName").invoke(descriptor);
    }
    private static Class<?> attachClass() {
        try {
            return Class.forName("com.sun.tools.attach.VirtualMachine");
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
        }
        File tools = new File(System.getProperty("java.home"), "../lib/tools.jar");
        if (!tools.isFile()) {
            tools = new File(System.getProperty("java.home"), "lib/tools.jar");
        }
        if (!tools.isFile()) {
            return null;
        }
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{tools.toURI().toURL()},
                    Injector.class.getClassLoader());
            return Class.forName("com.sun.tools.attach.VirtualMachine", true, loader);
        } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
            return null;
        }
    }
    private static boolean looksLikeMinecraft(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return false;
        }
        String lower = displayName.toLowerCase();
        if (lower.contains("gradle") || lower.contains("myau") || lower.contains("injector")) {
            return false;
        }
        return lower.contains("net.minecraft.client.main.main")
                || lower.contains("net.minecraft.launchwrapper.launch")
                || lower.contains("lunar")
                || lower.contains("badlion");
    }
    private static String shorten(String s) {
        if (s == null || s.isEmpty()) {
            return "(no name)";
        }
        return s.length() > 110 ? s.substring(0, 110) + "..." : s;
    }
}
