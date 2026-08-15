# Map Transport Experiments Design

## Objective

Produce decision-grade evidence for the three unresolved map-transport choices: rectangle planning, extraction allocation, and prerender break-even. Experiments must exercise production seams, preserve packet ownership, and report assumptions separately from observations.

## Alternatives considered

1. **Synthetic screening only:** fastest, but repeats the prior failure mode where local models diverge from production.
2. **Live-server profiling only:** closest to users, but noisy and weak at isolating planner decisions or allocation sources.
3. **Layered decision-grade campaign:** deterministic production-path experiments first, then focused runtime validation. This is selected because it identifies causal behavior before adding server/network noise.

## Fan-out tracks

### Planner trace and cost sweep

Run `Patches.plan` directly over deterministic dirty-span traces representing GUI strips, separated widgets, diagonals, sparse animation, full redraws, and seeded random traces. Sweep packet costs and report rectangle geometry, payload, packet count, waste, and a common evaluation matrix. A true multi-interval lower bound will quantify the maximum possible gain from changing `MapSurface`; it is not treated as a production implementation.

Success criterion: identify cost ranges that are stable across representative traces and whether same-row disjoint intervals leave enough recoverable payload to justify more dirty-state complexity.

### Extraction ownership and allocation

Benchmark production `MapSurface.region` against ownership-safe alternatives only. Retain every result through the measurement window so no candidate relies on a shared mutable array. Use warmups, alternating order, multiple forks, elapsed-time distributions, and JFR allocation recording or JVM allocation counters where available. Include patch-sized distributions rather than one fixed 64×64 rectangle.

Success criterion: establish allocation bytes and latency attributable to extraction, then determine whether it is material relative to packet construction. No production change is proposed unless an alternative preserves exclusive array ownership.

### Prerender break-even

Model distinct steps, map count, dirty streaming fraction, playback cycles, repoint payload, and packet overhead as separate inputs. Clamp distinct steps to production's 32-step limit. Validate key cases against `WallLoop`/`FakeTransport` counters, including the documented 3×3×12 initial burst.

Success criterion: produce break-even cycles/time for explicit workload assumptions and identify when prerendering loses because viewers leave before amortizing the initial burst.

## Evidence contract

- Experimental code remains test-source tooling; production behavior is unchanged.
- Every generated result includes fixture parameters and formulas.
- Assertions calculate emitted coverage from geometry; expected counts are never copied into results.
- Planner comparisons use the same evaluation costs for all candidates.
- Timing claims include forks, warmup, ordering, and variability.
- Modeled byte counts are labeled raw payload, transport-accounted, or compressed wire bytes; these are never conflated.
- Each track emits machine-readable CSV plus a concise console summary.

## Integration and verification

Independent tracks run in isolated worktrees and are reviewed before integration. The parent session reruns each experiment from the integrated tree, checks deterministic outputs where applicable, and updates `docs/living-specs/map-transport-optimization.md` with conclusions and honest checkbox state.

## Non-goals

- No custom map protocol or compression codec.
- No production planner, surface, or transport modification during this campaign.
- No claim about client texture-upload cost without client-side measurement.
- No shared reusable pixel buffer across retained packets.
