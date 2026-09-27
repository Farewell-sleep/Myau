package myau.access;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class Access {
    private static final Set<String> REPORTED =
            Collections.synchronizedSet(new HashSet<String>());
    private Access() {
    }
    static void report(String owner, String member, Throwable cause) {
        String key = owner + "#" + member;
        if (!REPORTED.add(key)) {
            return;
        }
        String simple = owner.substring(owner.lastIndexOf('.') + 1);

        myau.inject.Log.line("cannot reach " + simple + "." + member
                + " (" + cause + ") -- returning a default from here on");
    }
}
