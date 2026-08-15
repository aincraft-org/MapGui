# Map Transport Optimization — Living Spec

> Status: active
> Last updated: 2026-08-14

## Intent

Keep vanilla Minecraft map displays responsive while minimizing payload, packet count, allocation pressure, and client texture uploads. Optimize from measured end-to-end behavior; packet-byte reductions alone are not sufficient evidence.

## Boundaries

### In scope

- Dirty-region tracking and rectangle planning for 128×128 map tiles.
- Pixel extraction ownership through packet construction and bundled delivery.
- Streaming-versus-prerender accounting for repeating wall content.
- Reproducible performance experiments that exercise production paths.

### Out of scope / non-goals

- Custom client codecs or Kryo/LZ4 payloads inside vanilla map packets.
- Changing Minecraft's `ClientboundMapItemDataPacket` format.
- Optimizing compute without evidence that it affects server or client behavior.

## Invariants

- Every vanilla `MapPatch` carries palette-index bytes in the protocol-defined rectangle layout.
- A pixel array retained by a queued packet remains exclusively owned and unchanged until encoding completes.
- Dirty-region candidates are compared using payload and packet/client-update cost, not payload alone.
- Prerender accounting includes every distinct step sent under its own map IDs and separates that one-time cost from playback switches.
- Claims about allocation or throughput require a benchmark that controls warmup, order, forks, and allocation measurement.

## Implementation guidance

- Keep `MapSurface` as the dirty-state owner and `Patches` as the rectangle-planning seam; do not add a second planner convention.
- Represent side-by-side changes explicitly before calling a strategy “multi-span”; per-row `min`/`max` still represents one span.
- Use production `Patches.plan` in split-cost experiments. Report returned rectangles, total area, packet count, and the selected objective.
- Do not reuse one extraction buffer across `MapPatch` instances. A safe allocation reduction must preserve per-packet ownership, such as encoding before reuse or pooling buffers only after confirmed send completion.
- Measure extraction with JMH or forked/interleaved runs and JFR/profiler allocation data. Include packet construction and bundle lifetime when evaluating a production alternative.
- Model prerender with distinct variables: map count, distinct steps (clamped to 32), playback horizon/switches, dirty streaming area, packet overhead, and compression assumptions. Validate accounting against transport counters or a packet capture.
- Keep experimental mains outside production integration until their assertions validate emitted geometry and their fixtures can distinguish candidates.

## Current

- [x] Vanilla transport sends raw palette-index rectangle patches.
- [x] Dirty tracking records one horizontal span per row and cost-partitions rows into rectangles.
- [x] Prerendered loops send distinct map-ID layers once and then repoint client item frames.
- [x] Review the four 2026-08-14 experimental branches for correctness and production relevance.
- [x] Replace the multi-span experiment with explicit interval geometry and non-tautological coverage assertions.
- [ ] Replace the split-cost sweep with calls to production `Patches.plan` and fixtures that cross decision thresholds.
- [x] Run a split-cost sweep with a labeled test-source `PlannerCostModel` because exposing the split cost in `Patches` would modify production.
- [x] Assert the test-source model's default `1024` output equals production `Patches.plan` for every fixture.
- [ ] Replace the extraction microbenchmark with forked, allocation-measured production-path benchmarking.
- [x] Correct prerender accounting by separating distinct steps from playback horizon and including all initial frame layers.

### Current notes

The planner experiment is deterministic and validates coverage from first principles. Its cost sweep uses a
faithful test-source `PlannerCostModel` (the same DP as `Patches`) because exposing a split-cost parameter
would modify the production class. The default `1024` cost in the model is asserted equal to production
`Patches.plan` for every fixture. Its same-row fixture shows only 60 raw bytes recoverable beyond the current
one-span representation, which is below one additional packet under the current objective. The extraction benchmark
now preserves result ownership and is useful only as a timing diagnostic: its five in-process batches alternate
order and retain arrays, but they measure no JVM allocation counters. The corrected prerender model counts all
layers and symmetric assumed packet overhead, but remains model-only until checked against production transport
counters. None of these results justifies a production change yet.

## Next

- [ ] Capture representative GUI, sparse animation, and full-video dirty-region traces.
- [ ] Evaluate planner objectives across those traces, including packet count and client texture-update cost.
- [ ] Measure extraction allocation and latency with forks, interleaved variants, and retained packet lifetime.
- [ ] Validate prerender break-even against `Bandwidth` counters and, where practical, compressed wire capture.

## Future

- [ ] Evaluate a true multi-interval-per-row planner only if representative traces show meaningful same-row gaps.
- [ ] Evaluate ownership-safe buffer pooling only if allocation profiling identifies extraction as material.
- [ ] Consider custom compressed payloads only for an explicitly non-vanilla client protocol.

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-14 | Do not integrate any of the four experimental branches. | Multi-span collapses intervals, split-cost ignores the production planner, extraction reuses storage incompatible with queued packet ownership, and prerender omits most initial payload. |
| 2026-08-14 | Preserve fresh per-patch pixel ownership until encoding lifetime is redesigned. | `MapPatch` retains the supplied array and bundles retain packets until delivery; shared scratch storage aliases queued updates. |
| 2026-08-14 | Treat prerendering as an existing production capability, not a proposed feature. | `WallDisplay`, `WallLoop`, `WallTiles`, and NMS map repointing already implement and document it. |
| 2026-08-14 | Do not infer a new `Patches` split cost from payload-only experiments. | The current 1024 cost models client update work; changing it requires representative end-to-end evidence. |
| 2026-08-14 | Keep the current one-span-per-row representation pending real traces. | The corrected same-row fixture recovers 60 raw bytes with true intervals, less than the current modeled cost of another packet. |
| 2026-08-14 | Classify extraction timing and prerender break-even as diagnostic, not decision-grade. | The extraction benchmark lacks forked JVM allocation counters, and the prerender model lacks production transport-counter validation. |

## Open questions

- [ ] What representative workload traces should define planner tuning?
- [ ] Is region extraction a material allocator after packet and network costs are included?
- [ ] What playback horizon and audience distribution define the operational prerender break-even point?
