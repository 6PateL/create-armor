package com.createarmor;

import java.util.HashMap;
import java.util.Map;

/** Клієнтський стан гармат для анімацій (без клієнтських класів, щоб його можна було читати зі спільного коду). */
public final class CannonState {
    public static final Map<Integer, Long> FIRED = new HashMap<>();
    public static final Map<Integer, Long> RELOAD_UNTIL = new HashMap<>();
    public static volatile long time;
    public static volatile int localId = -1;
    public static volatile boolean menuOpen;
    public static volatile boolean localAiming;

    private CannonState() {
    }

    public static long fireAge(int id) {
        Long t = FIRED.get(id);
        return t == null ? Long.MAX_VALUE : time - t;
    }

    public static boolean reloading(int id) {
        Long t = RELOAD_UNTIL.get(id);
        return t != null && t > time;
    }
}
