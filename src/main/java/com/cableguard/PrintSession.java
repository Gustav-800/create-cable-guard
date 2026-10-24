package com.cableguard;

/**
 * Tracks whether a schematic printing session is currently active.
 * <p>
 * Create's cannon launches blocks one by one (one every {@code schematicannonDelay} ticks),
 * so a wire endpoint can land many ticks before its partner. createaddition's wire graph
 * treats "chunk loaded but block entity absent" as "wire removed" and prunes the wire on the
 * next tick. While a print is running we suppress that pruning so both endpoints can arrive.
 * <p>
 * Every call to Create's {@code BlockHelper.prepareBlockEntityData} pings this session; that
 * method is only used by the schematic printing paths.
 */
public final class PrintSession {

    private static final long TIMEOUT_MS = 600_000L; // safety net if a print never "finishes"

    private static volatile long lastPing = 0L;

    private PrintSession() {
    }

    public static void ping() {
        lastPing = System.currentTimeMillis();
    }

    public static void begin() {
        ping();
    }

    public static void end() {
        lastPing = 0L;
    }

    public static boolean active() {
        long t = lastPing;
        return t != 0L && System.currentTimeMillis() - t < TIMEOUT_MS;
    }
}
