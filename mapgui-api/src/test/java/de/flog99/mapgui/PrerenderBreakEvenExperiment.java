package de.flog99.mapgui;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Deterministic accounting model for prerendered map-loop break-even. */
public final class PrerenderBreakEvenExperiment {
    private static final int MAP_BYTES = 128 * 128;
    private static final int MAX_STEPS = WallDisplay.MAX_PRERENDER_STEPS;
    private static final int PACKET_OVERHEAD_BYTES = 32;
    private static final int REPOINT_OVERHEAD_BYTES = 8;
    private static final int[] MAPS = {1, 4, 9, 16};
    private static final int[] REQUESTED_STEPS = {2, 12, 32, 50};
    private static final double[] DIRTY_FRACTIONS = {0.1, 0.5, 1.0};
    private static final int[] SWITCHES = {1, 2, 4, 8, 16, 32, 64, 128};
    private static final Path OUTPUT = Path.of("build/experiments/prerender/results.csv");

    private PrerenderBreakEvenExperiment() {}

    public static void main(String[] args) throws IOException {
        if (MAP_BYTES != 16384) throw new AssertionError("map bytes changed: " + MAP_BYTES);
        if (MAX_STEPS != 32) throw new AssertionError("production prerender cap changed: " + MAX_STEPS);
        long expectedInitial = 9L * 12 * MAP_BYTES;
        if (expectedInitial != 1_769_472L) throw new AssertionError("9x12 initial payload: " + expectedInitial);

        Files.createDirectories(OUTPUT.getParent());
        try (BufferedWriter out = Files.newBufferedWriter(OUTPUT)) {
            out.write("maps,requestedSteps,actualSteps,dirtyFraction,switches,streamingBytes,prerenderInitialBytes,repointBytes,packetOverheadBytes,prerenderTotalBytes,breakEvenSwitches\n");
            for (int maps : MAPS) {
                for (int requested : REQUESTED_STEPS) {
                    int actual = Math.clamp(requested, 1, MAX_STEPS);
                    for (double dirtyFraction : DIRTY_FRACTIONS) {
                        for (int switches : SWITCHES) {
                            Accounting a = account(maps, requested, actual, dirtyFraction, switches);
                            out.write(String.format(Locale.ROOT, "%d,%d,%d,%.1f,%d,%d,%d,%d,%d,%d,%d%n",
                                    maps, requested, actual, dirtyFraction, switches,
                                    a.streamingBytes, a.prerenderInitialBytes, a.repointBytes,
                                    a.packetOverheadBytes, a.prerenderTotalBytes, a.breakEvenSwitches));
                        }
                    }
                }
            }
        }
        printSummary();
    }

    private static Accounting account(int maps, int requested, int actual, double dirtyFraction, int switches) {
        if (dirtyFraction < 0.0 || dirtyFraction > 1.0) throw new AssertionError("dirty fraction");
        long fullFramePayload = (long) maps * MAP_BYTES;
        long dirtyPayload = Math.round(fullFramePayload * dirtyFraction);
        long streamingBytes = switches * (dirtyPayload + (long) maps * PACKET_OVERHEAD_BYTES);
        long prerenderInitialBytes = (long) maps * actual * MAP_BYTES;
        long repointBytes = (long) maps * switches * REPOINT_OVERHEAD_BYTES;
        long packetOverheadBytes = (long) maps * actual * PACKET_OVERHEAD_BYTES;
        long prerenderTotalBytes = prerenderInitialBytes + packetOverheadBytes + repointBytes;
        long breakEvenSwitches = breakEven(prerenderInitialBytes + packetOverheadBytes,
                dirtyPayload + (long) maps * PACKET_OVERHEAD_BYTES, maps * REPOINT_OVERHEAD_BYTES);
        if (streamingBytes < 0 || prerenderTotalBytes < prerenderInitialBytes) throw new AssertionError("accounting overflow");
        return new Accounting(streamingBytes, prerenderInitialBytes, repointBytes, packetOverheadBytes,
                prerenderTotalBytes, breakEvenSwitches);
    }
    private static long breakEven(long initialBytes, long streamingBytesPerSwitch, long repointBytesPerSwitch) {
        long savingPerSwitch = streamingBytesPerSwitch - repointBytesPerSwitch;
        if (savingPerSwitch <= 0) return Long.MAX_VALUE;
        return Math.ceilDiv(initialBytes, savingPerSwitch);
    }


    private static void printSummary() {
        Accounting twelve = account(9, 12, 12, 1.0, 1);
        Accounting clamped = account(9, 50, 32, 1.0, 1);
        System.out.println("prerender CSV: " + OUTPUT + " rows=" + (MAPS.length * REQUESTED_STEPS.length * DIRTY_FRACTIONS.length * SWITCHES.length));
        System.out.printf(Locale.ROOT, "9 maps x 12 steps: initialRaw=%d bytes; full-dirty breakEvenSwitches=%d%n",
                twelve.prerenderInitialBytes, twelve.breakEvenSwitches);
        System.out.printf(Locale.ROOT, "requested 50 steps clamps to actual %d; initialRaw=%d bytes%n",
                Math.clamp(50, 1, MAX_STEPS), clamped.prerenderInitialBytes);
        System.out.println("limitations: model-only diagnostic; streaming and prerender paths use comparable assumed packet overhead, while repoint overhead remains an explicit assumption; no production-seam validation is performed, and viewer departures/client texture costs are not modeled.");
    }

    private record Accounting(long streamingBytes, long prerenderInitialBytes, long repointBytes,
                              long packetOverheadBytes, long prerenderTotalBytes, long breakEvenSwitches) {}
}
