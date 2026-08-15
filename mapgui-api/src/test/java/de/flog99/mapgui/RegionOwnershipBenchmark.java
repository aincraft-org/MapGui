package de.flog99.mapgui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Measures MapSurface.region against an ownership-safe explicit-copy equivalent. */
public final class RegionOwnershipBenchmark {
    private static final int SURFACE_WIDTH = 256;
    private static final int SURFACE_HEIGHT = 256;
    private static final int WARMUP_BATCHES = 3;
    private static final int MEASURE_BATCHES = 5;
    private static final int REPETITIONS = 200;
    private static final String PRODUCTION = "production-region";
    private static final String EXPLICIT_COPY = "explicit-copy";

    private RegionOwnershipBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? Path.of("build/experiments/region") : Path.of(args[0]);
        Files.createDirectories(output);
        List<Shape> shapes = List.of(
                new Shape("8x8", 8, 8, 12, 19),
                new Shape("128x2", 128, 2, 31, 77),
                new Shape("32x64", 32, 64, 91, 13),
                new Shape("64x64", 64, 64, 47, 101),
                new Shape("128x128", 128, 128, 53, 29));
        List<Result> results = new ArrayList<>();
        for (int batch = 0; batch < MEASURE_BATCHES; batch++) {
            boolean productionFirst = (batch & 1) == 0;
            for (Shape shape : shapes) {
                SurfaceFixture fixture = new SurfaceFixture(shape);
                warmup(fixture, shape, productionFirst);
                Result first = measure(fixture, shape, batch, productionFirst, productionFirst ? PRODUCTION : EXPLICIT_COPY);
                Result second = measure(fixture, shape, batch, productionFirst, productionFirst ? EXPLICIT_COPY : PRODUCTION);
                results.add(first);
                results.add(second);
            }
        }
        Path csv = output.resolve("results.csv");
        Files.writeString(csv, csv(results));
        printSummary(csv, results);
    }

    private static void warmup(SurfaceFixture fixture, Shape shape, boolean productionFirst) {
        for (int batch = 0; batch < WARMUP_BATCHES; batch++) {
            runBatch(fixture, shape, productionFirst ? PRODUCTION : EXPLICIT_COPY, 20, false);
            runBatch(fixture, shape, productionFirst ? EXPLICIT_COPY : PRODUCTION, 20, false);
        }
    }

    private static Result measure(SurfaceFixture fixture, Shape shape, int fork, boolean productionFirst, String variant) {
        long start = System.nanoTime();
        long checksum = runBatch(fixture, shape, variant, REPETITIONS, true);
        long elapsed = System.nanoTime() - start;
        long retainedBytes = (long) shape.width * shape.height * REPETITIONS;
        return new Result(fork, productionFirst ? "production-first" : "copy-first", shape.name, shape.width, shape.height,
                variant, REPETITIONS, elapsed, checksum, retainedBytes);
    }
    private static long runBatch(SurfaceFixture fixture, Shape shape, String variant, int repetitions, boolean retain) {
        List<byte[]> retained = new ArrayList<>(repetitions);
        long checksum = 0;
        for (int repetition = 0; repetition < repetitions; repetition++) {
            byte[] result = variant.equals(PRODUCTION)
                    ? fixture.surface.region(shape.x, shape.y, shape.width, shape.height)
                    : explicitCopy(fixture.surface.pixels(), fixture.surface.width(), shape.x, shape.y, shape.width, shape.height);
            checksum += checksum(result);
            retained.add(result);
        }
        long expectedChecksum = fixture.expectedChecksum(shape);
        for (byte[] result : retained) {
            if (checksum(result) != expectedChecksum) throw new AssertionError("retained region mismatch for " + variant);
        }
        long expected = expectedChecksum * repetitions;
        if (checksum != expected) throw new AssertionError("checksum mismatch for " + shape.name + ": " + checksum + " != " + expected);
        byte[] before = retained.get(0).clone();
        byte[] later = variant.equals(PRODUCTION)
                ? fixture.surface.region(shape.x, shape.y, shape.width, shape.height)
                : explicitCopy(fixture.surface.pixels(), fixture.surface.width(), shape.x, shape.y, shape.width, shape.height);
        if (!Arrays.equals(before, retained.get(0))) throw new AssertionError("retained region mutated for " + variant);
        if (!Arrays.equals(before, later)) throw new AssertionError("non-deterministic region for " + variant);
        if (!retain) retained.clear();
        return checksum;
    }

    private static byte[] explicitCopy(byte[] pixels, int surfaceWidth, int x, int y, int width, int height) {
        byte[] result = new byte[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(pixels, (y + row) * surfaceWidth + x, result, row * width, width);
        }
        return result;
    }

    private static long checksum(byte[] bytes) {
        long hash = 1125899906842597L;
        for (byte value : bytes) hash = 31 * hash + (value & 0xff);
        return hash;
    }

    private static String csv(List<Result> results) {
        StringBuilder out = new StringBuilder("fork,order,shape,width,height,variant,repetitions,elapsedNs,checksum,retainedBytes\n");
        for (Result result : results) {
            out.append(result.fork).append(',').append(result.order).append(',').append(result.shape).append(',')
                    .append(result.width).append(',').append(result.height).append(',').append(result.variant).append(',')
                    .append(result.repetitions).append(',').append(result.elapsedNs).append(',').append(result.checksum).append(',')
                    .append(result.retainedBytes).append('\n');
        }
        return out.toString();
    }

    private static void printSummary(Path csv, List<Result> results) {
        System.out.println("CSV: " + csv.toAbsolutePath());
        System.out.println("Methodology: 3 warmup batches; 5 in-process measured batches; alternating variant order; " + REPETITIONS + " retained results per measured batch.");
        System.out.println("Allocation note: retainedBytes is theoretical result-array payload (width*height*repetitions), not measured JVM allocation.");
        for (Shape shape : List.of(new Shape("8x8", 8, 8, 0, 0), new Shape("128x2", 128, 2, 0, 0), new Shape("32x64", 32, 64, 0, 0), new Shape("64x64", 64, 64, 0, 0), new Shape("128x128", 128, 128, 0, 0))) {
            List<Result> matching = results.stream().filter(r -> r.shape.equals(shape.name)).toList();
            for (String variant : List.of(PRODUCTION, EXPLICIT_COPY)) {
                long[] times = matching.stream().filter(r -> r.variant.equals(variant)).mapToLong(r -> r.elapsedNs).sorted().toArray();
                long median = times.length == 0 ? 0 : times[times.length / 2];
                long min = times.length == 0 ? 0 : times[0];
                long max = times.length == 0 ? 0 : times[times.length - 1];
                System.out.println(shape.name + " " + variant + " elapsedNs median=" + median + " range=" + min + ".." + max);
            }
        }
        System.out.println("Principal finding: both variants produce equal checksums and retained arrays remain stable; timing differences are workload/JVM observations, not allocation proof.");
        System.out.println("Limits: elapsed time includes loop/checksum/retention overhead; no JFR or JVM allocation counters are collected by this main. Run with JFR separately for measured allocation.");
    }
    private record Result(int fork, String order, String shape, int width, int height, String variant, int repetitions,
                          long elapsedNs, long checksum, long retainedBytes) {
    }
    private record Shape(String name, int width, int height, int x, int y) {
    }


    private static final class SurfaceFixture {
        private final MapSurface surface = new MapSurface(SURFACE_WIDTH, SURFACE_HEIGHT);

        private SurfaceFixture(Shape shape) {
            for (int y = 0; y < SURFACE_HEIGHT; y++) {
                for (int x = 0; x < SURFACE_WIDTH; x++) surface.set(x, y, (byte) ((x * 17 + y * 31 + shape.width + shape.height) & 0xff));
            }
        }

        private long expectedChecksum(Shape shape) {
            byte[] expected = explicitCopy(surface.pixels(), surface.width(), shape.x, shape.y, shape.width, shape.height);
            return checksum(expected);
        }
    }
}
