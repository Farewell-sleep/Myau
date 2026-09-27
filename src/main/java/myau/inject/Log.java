package myau.inject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public final class Log {
    private static final Object LOCK = new Object();
    private static File file;
    private static boolean resolved;
    private Log() {
    }
    public static void line(String message) {
        String text = "[myau-inject] " + message;
        System.out.println(text);
        append(text);
    }
    static void lines(String prefix, Iterable<String> messages) {
        for (String message : messages) {
            line(prefix + message);
        }
    }
    private static final java.util.Set<String> SWALLOWED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public static void swallowed(Throwable cause) {
        try {
            StackTraceElement[] frames = cause.getStackTrace();
            String where = frames.length > 0
                    ? frames[0].getClassName() + "." + frames[0].getMethodName()
                            + ":" + frames[0].getLineNumber()
                    : "unknown";
            String key = where + " " + cause.getClass().getName() + " " + cause.getMessage();
            if (!SWALLOWED.add(key)) {
                return;
            }
            line("swallowed at " + where + ": " + cause);
        } catch (Throwable ignored) {
        }
    }

    static void throwable(String message, Throwable cause) {
        line(message + ": " + cause);
        for (StackTraceElement frame : cause.getStackTrace()) {
            append("    at " + frame);
        }
        cause.printStackTrace();
    }
    private static void append(String text) {
        synchronized (LOCK) {
            if (!resolved) {
                resolved = true;
                String temp = System.getProperty("java.io.tmpdir");
                if (temp != null) {
                    file = new File(temp, "myau-native.log");
                }
            }
            if (file == null) {
                return;
            }
            Writer writer = null;
            try {
                writer = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
                writer.write(text);
                writer.write(System.getProperty("line.separator", "\n"));
            } catch (Throwable ignored) {
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
