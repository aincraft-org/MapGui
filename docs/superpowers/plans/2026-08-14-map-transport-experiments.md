# Map Transport Experiments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce decision-grade, reproducible evidence for map rectangle planning, extraction allocation, and prerender break-even without modifying production behavior.

**Architecture:** Three independent test-source experiment mains exercise production seams and emit CSV. They run concurrently in isolated worktrees, then their commits are integrated and verified together. Durable conclusions are recorded in the existing map-transport living spec.

**Tech Stack:** Java 25, Gradle, existing MapGUI API tests, JVM/JFR tooling where available.

## Global Constraints

- Do not modify production classes.
- Preserve exclusive ownership of every retained patch pixel array.
- Use production `Patches.plan` and production prerender seams rather than local planner/runtime substitutes.
- Seed randomized traces and print every model assumption.
- Skip formatters, linters, and project-wide test suites inside fan-out workers.

---

### Task 1: Planner Decision Experiment

**Files:**
- Create: `mapgui-api/src/test/java/de/flog99/mapgui/PlannerDecisionExperiment.java`

**Interfaces:**
- Consumes: package-private `Patches.plan(int[], int[], int, int, int, int, int)` and `Rect`.
- Produces: `build/experiments/planner/results.csv` with scenario, seed, evaluationCost, plannerCost, packets, payload, changed, waste, and score.

- [ ] Define deterministic fixtures for horizontal strips, separated widgets, same-row gaps, diagonal motion, sparse rows, full redraw, and seeded random rectangles.
- [ ] Add geometry-derived checks that every changed pixel is covered and report unchanged pixels covered.
- [ ] Sweep planner costs `0, 12, 64, 128, 256, 512, 1024, 2048, 4096`.
- [ ] Evaluate every returned plan under common costs rather than scoring each candidate only with its own planner cost.
- [ ] Compute an explicit multi-interval payload lower bound for same-row gaps without presenting it as an implementable packet plan.
- [ ] Compile and run the main; expect deterministic CSV and no assertion failures.

### Task 2: Extraction Allocation Experiment

**Files:**
- Create: `mapgui-api/src/test/java/de/flog99/mapgui/RegionOwnershipBenchmark.java`

**Interfaces:**
- Consumes: `MapSurface.region` and `MapSurface.pixels`.
- Produces: `build/experiments/region/results.csv` with fork, variant, patch shape, repetitions, elapsedNs, checksum, and retainedBytes; optional JFR recording command documented in console output.

- [ ] Define representative patch shapes: 8×8, 128×2, 32×64, 64×64, and 128×128.
- [ ] Retain every extracted result until the measured batch ends and verify earlier arrays remain unchanged.
- [ ] Compare production extraction with an ownership-safe explicit copy implementation; do not benchmark a shared destination as a candidate.
- [ ] Run warmup plus at least five alternating-order measurement forks in separate JVM invocations controlled by the main.
- [ ] Report median/range and theoretical retained allocation; do not label inferred values as measured allocation.
- [ ] Compile and run the main; expect matching checksums and ownership assertions.

### Task 3: Prerender Break-even Experiment

**Files:**
- Create: `mapgui-api/src/test/java/de/flog99/mapgui/PrerenderBreakEvenExperiment.java`
- Modify: `mapgui-api/src/test/java/de/flog99/mapgui/WallLoopTest.java` only if an existing helper is required for production-counter validation.

**Interfaces:**
- Consumes: production maximum `WallDisplay.MAX_PRERENDER_STEPS`, `FakeTransport`, and existing wall-loop test helpers.
- Produces: `build/experiments/prerender/results.csv` with maps, requestedSteps, actualSteps, dirtyFraction, switches, streamingBytes, prerenderInitialBytes, repointBytes, prerenderTotalBytes, and breakEvenSwitches.

- [ ] Separate requested steps, clamped distinct steps, playback switches, and dirty streaming fraction.
- [ ] Include map counts `1, 4, 9, 16`, steps `2, 12, 32, 50`, and dirty fractions `0.1, 0.5, 1.0`.
- [ ] Account for every distinct map layer, per-update packet overhead, and each repoint operation.
- [ ] Assert the 9-map×12-step raw initial payload equals `1,769,472` bytes.
- [ ] Validate representative initial-send and replay cases through existing production wall/FakeTransport seams where feasible.
- [ ] Compile and run the main; expect clamping and accounting assertions to pass.

### Task 4: Integrated Verification and Catalog

**Files:**
- Modify: `docs/living-specs/map-transport-optimization.md`

**Interfaces:**
- Consumes: CSV output from Tasks 1–3.
- Produces: evidence-backed decisions, checked experiment items, and remaining open questions.

- [ ] Integrate all three isolated commits without production changes.
- [ ] Run `./gradlew :mapgui-api:compileTestJava`.
- [ ] Run all three experiment mains with the full API and layout test classpaths.
- [ ] Inspect CSV headers, row counts, deterministic planner/prerender reruns, and region ownership assertions.
- [ ] Record conclusions and limitations in the living spec; check only verified items.
- [ ] Run the focused existing tests covering `Patches`, `MapSurface`, and wall-loop prerender behavior.
