package de.flog99.mapgui;

import de.flog99.mapgui.ui.Rect;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Decision-grade, deterministic evidence for the row-span planner.
 *
 * <p>The cost sweep cannot call production {@link Patches} with an arbitrary split cost without modifying
 * the production class, so this experiment uses a faithful test-source replica, {@link PlannerCostModel},
 * for the sweep. The replica is the same DP; only the packet-cost parameter varies. The production
 * {@link Patches#plan} seam is still exercised for the default 1024 cost, and all geometry is validated
 * from first principles rather than copied from implementation.
 */
public final class PlannerDecisionExperiment {
    private static final int MAP = 128;
    private static final int[] PLANNER_COSTS = {0, 12, 64, 128, 256, 512, 1024, 2048, 4096};
    private static final int[] EVALUATION_COSTS = {12, 64, 256, 1024};
    private static final int PRODUCTION_SPLIT_COST = 1024;
    private static final long RANDOM_SEED = 20260814L;
    private static final Path OUTPUT = Path.of("build/experiments/planner/results.csv");

    private PlannerDecisionExperiment() {}

    public static void main(String[] args) throws IOException {
        List<Scenario> scenarios = scenarios();
        Files.createDirectories(OUTPUT.getParent());
        try (BufferedWriter out = Files.newBufferedWriter(OUTPUT)) {
            out.write("scenario,seed,evaluationCost,plannerCost,packets,payload,changed,waste,score\n");
            for (Scenario scenario : scenarios) {
                for (int plannerCost : PLANNER_COSTS) {
                    Plan plan = plan(scenario, plannerCost);
                    for (int evaluationCost : EVALUATION_COSTS) {
                        Metrics metrics = evaluate(scenario, plan.rects(), evaluationCost);
                        out.write(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%d,%d%n",
                                scenario.name(), scenario.seed(), evaluationCost, plannerCost,
                                plan.rects().size(), metrics.payload(), metrics.changed(), metrics.waste(), metrics.score()));
                    }
                }
            }
        }
        printSummary(scenarios);
    }

    private static List<Scenario> scenarios() {
        List<Scenario> result = new ArrayList<>();
        result.add(new Scenario("horizontal-strip", 0, spans(new Span(8, 8, 112), new Span(9, 8, 112))));
        result.add(new Scenario("separated-widgets", 0, spans(new Span(8, 4, 20), new Span(8, 104, 20), new Span(96, 4, 20), new Span(96, 104, 20))));
        result.add(new Scenario("same-row-gaps", 0, spans(new Span(40, 4, 12), new Span(40, 40, 12), new Span(40, 88, 12))));
        List<Span> diagonal = new ArrayList<>();
        for (int y = 0; y < MAP; y++) diagonal.add(new Span(y, y, y == MAP - 1 ? 1 : 2));
        result.add(new Scenario("diagonal-motion", 0, diagonal));
        List<Span> sparse = new ArrayList<>();
        for (int y = 0; y < MAP; y += 11) sparse.add(new Span(y, 12, 24));
        result.add(new Scenario("sparse-rows", 0, sparse));
        List<Span> full = new ArrayList<>();
        for (int y = 0; y < MAP; y++) full.add(new Span(y, 0, MAP));
        result.add(new Scenario("full-redraw", 0, full));
        for (int trace = 0; trace < 8; trace++) {
            Random random = new Random(RANDOM_SEED + trace);
            List<Span> spans = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int x = random.nextInt(112);
                spans.add(new Span(random.nextInt(MAP), x, 1 + random.nextInt(16)));
            }
            result.add(new Scenario("random-" + trace, RANDOM_SEED + trace, spans));
        }
        return result;
    }

    private static Plan plan(Scenario scenario, int plannerCost) {
        int[] left = new int[MAP];
        int[] right = new int[MAP];
        Arrays.fill(left, Integer.MAX_VALUE);
        Arrays.fill(right, Integer.MIN_VALUE);
        for (Span span : scenario.spans()) {
            assertSpanBounds(span);
            left[span.row()] = Math.min(left[span.row()], span.x());
            right[span.row()] = Math.max(right[span.row()], span.x() + span.width());
        }
        List<Rect> rects = PlannerCostModel.plan(left, right, 1, 0, 0, MAP - 1, plannerCost);
        if (plannerCost == PRODUCTION_SPLIT_COST) {
            assertEqualRects(rects, Patches.plan(left, right, 1, 0, 0, MAP - 1),
                    "test-source model must match production Patches for default cost");
        }
        assertCoverage(scenario, rects);
        return new Plan(rects);
    }

    private static void assertSpanBounds(Span span) {
        if (span.row() < 0 || span.row() >= MAP || span.x() < 0 || span.width() <= 0 || span.x() + span.width() > MAP) {
            throw new AssertionError("span outside map: " + span);
        }
    }
    private static void assertEqualRects(List<Rect> model, List<Rect> production, String message) {
        if (model.size() != production.size()) {
            throw new AssertionError(message + ": size " + model.size() + " != " + production.size());
        }
        for (int i = 0; i < model.size(); i++) {
            if (!model.get(i).equals(production.get(i))) {
                throw new AssertionError(message + ": rect " + i + " " + model.get(i) + " != " + production.get(i));
            }
        }
    }

    private static Metrics evaluate(Scenario scenario, List<Rect> rects, int evaluationCost) {
        long changed = changedPixels(scenario);
        long payload = rects.stream().mapToLong(r -> (long) r.width() * r.height()).sum();
        long waste = payload - changed;
        if (waste < 0) throw new AssertionError("negative waste");
        return new Metrics(payload, changed, waste, payload + (long) rects.size() * evaluationCost);
    }

    private static long changedPixels(Scenario scenario) {
        boolean[][] changed = new boolean[MAP][MAP];
        for (Span span : scenario.spans()) {
            for (int x = span.x(); x < span.x() + span.width(); x++) changed[span.row()][x] = true;
        }
        long count = 0;
        for (boolean[] row : changed) for (boolean pixel : row) if (pixel) count++;
        return count;
    }

    private static void assertCoverage(Scenario scenario, List<Rect> rects) {
        boolean[][] changed = new boolean[MAP][MAP];
        for (Span span : scenario.spans()) {
            assertSpanBounds(span);
            for (int x = span.x(); x < span.x() + span.width(); x++) changed[span.row()][x] = true;
        }
        boolean[][] covered = new boolean[MAP][MAP];
        for (Rect rect : rects) {
            if (rect.x() < 0 || rect.y() < 0 || rect.right() > MAP || rect.bottom() > MAP) {
                throw new AssertionError("rectangle outside map: " + rect);
            }
            for (int y = rect.y(); y < rect.bottom(); y++) {
                for (int x = rect.x(); x < rect.right(); x++) covered[y][x] = true;
            }
        }
        long changedCount = 0;
        long coveredChanged = 0;
        for (int y = 0; y < MAP; y++) {
            for (int x = 0; x < MAP; x++) {
                if (changed[y][x]) {
                    changedCount++;
                    if (!covered[y][x]) throw new AssertionError("changed pixel uncovered at " + x + "," + y);
                    coveredChanged++;
                }
            }
        }
        if (coveredChanged != changedCount) throw new AssertionError("coverage accounting failed: " + coveredChanged + " != " + changedCount);
    }

    private static void printSummary(List<Scenario> scenarios) {
        System.out.println("planner CSV: " + OUTPUT + " rows=" + scenarios.size() * PLANNER_COSTS.length * EVALUATION_COSTS.length);
        for (Scenario scenario : scenarios) {
            Plan low = plan(scenario, 0);
            Plan high = plan(scenario, 4096);
            Metrics lowMetrics = evaluate(scenario, low.rects(), 1024);
            Metrics highMetrics = evaluate(scenario, high.rects(), 1024);
            System.out.printf(Locale.ROOT, "%s changed=%d cost0=%d/%d cost4096=%d/%d%n", scenario.name(),
                    lowMetrics.changed(), low.rects().size(), lowMetrics.payload(), high.rects().size(), highMetrics.payload());
        }
        long bound = multiIntervalLowerBound();
        System.out.println("same-row multi-interval lower bound: rawPayload=" + bound + " changed=36 gapWaste=" + (bound - 36));
        System.out.println("plannerCost sweep uses test-source PlannerCostModel; default 1024 matches production Patches");
    }

    private static long multiIntervalLowerBound() {
        // Analytical lower bound: three disjoint intervals can be carried as three intervals,
        // unlike the production one-rectangle packet. This is not emitted as a packet plan.
        return 3L * 12;
    }

    private static List<Span> spans(Span... spans) { return List.of(spans); }
    private record Span(int row, int x, int width) {}
    private record Scenario(String name, long seed, List<Span> spans) {}
    private record Plan(List<Rect> rects) {}
    private record Metrics(long payload, long changed, long waste, long score) {}

    /**
     * Faithful test-source replica of {@link Patches#plan} with a configurable split cost.
     *
     * <p>This is not production code; it exists only so the experiment can sweep packet cost without
     * altering the production class. It uses the same row-based dynamic program as the production planner.
     */
    private static final class PlannerCostModel {

        private PlannerCostModel() {}

        static List<Rect> plan(int[] left, int[] right, int stride, int column,
                               int firstRow, int lastRow, int splitCost) {
            if (splitCost < 0) throw new IllegalArgumentException("splitCost must be non-negative");

            int boxLeft = Integer.MAX_VALUE;
            int boxRight = Integer.MIN_VALUE;
            int boxTop = 0;
            int boxBottom = 0;
            long spanned = 0;
            int rows = 0;

            for (int row = firstRow; row <= lastRow; row++) {
                int span = row * stride + column;
                if (left[span] >= right[span]) continue;

                boxLeft = Math.min(boxLeft, left[span]);
                boxRight = Math.max(boxRight, right[span]);
                if (rows == 0) boxTop = row;
                boxBottom = row;
                spanned += right[span] - left[span];
                rows++;
            }
            if (rows == 0) return List.of();

            Rect box = new Rect(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop + 1);
            long area = (long) box.width() * box.height();
            if (area <= spanned + splitCost) return List.of(box);

            return split(left, right, stride, column, boxTop, boxBottom, rows, splitCost);
        }

        private static List<Rect> split(int[] left, int[] right, int stride, int column,
                                        int firstRow, int lastRow, int rows, int splitCost) {
            int[] y = new int[rows];
            int[] from = new int[rows];
            int[] to = new int[rows];

            int count = 0;
            for (int row = firstRow; row <= lastRow; row++) {
                int span = row * stride + column;
                if (left[span] >= right[span]) continue;

                y[count] = row;
                from[count] = left[span];
                to[count] = right[span];
                count++;
            }

            long[] best = new long[count + 1];
            int[] begins = new int[count + 1];

            for (int end = 1; end <= count; end++) {
                best[end] = Long.MAX_VALUE;
                int groupLeft = Integer.MAX_VALUE;
                int groupRight = Integer.MIN_VALUE;

                for (int begin = end - 1; begin >= 0; begin--) {
                    groupLeft = Math.min(groupLeft, from[begin]);
                    groupRight = Math.max(groupRight, to[begin]);

                    long area = (long) (groupRight - groupLeft) * (y[end - 1] - y[begin] + 1);
                    long cost = best[begin] + area + splitCost;
                    if (cost <= best[end]) {
                        best[end] = cost;
                        begins[end] = begin;
                    }
                }
            }
            return regions(y, from, to, begins, count);
        }

        private static List<Rect> regions(int[] y, int[] from, int[] to, int[] begins, int count) {
            List<Rect> regions = new ArrayList<>();

            for (int end = count; end > 0; end = begins[end]) {
                int begin = begins[end];
                int groupLeft = Integer.MAX_VALUE;
                int groupRight = Integer.MIN_VALUE;

                for (int row = begin; row < end; row++) {
                    groupLeft = Math.min(groupLeft, from[row]);
                    groupRight = Math.max(groupRight, to[row]);
                }
                regions.add(new Rect(groupLeft, y[begin], groupRight - groupLeft, y[end - 1] - y[begin] + 1));
            }
            Collections.reverse(regions);
            return regions;
        }
    }
}
