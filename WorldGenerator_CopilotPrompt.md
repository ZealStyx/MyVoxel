# GitHub Copilot Prompt — `WorldGenerator.java` Full Implementation

> **How to use:** Open `WorldGenerator.java` in VS Code / IntelliJ with Copilot active.
> Paste each **`// COPILOT:`** comment block directly above the method or constant it describes.
> Copilot will use the surrounding context (constants, helper methods, existing zones) to generate
> matching code. Accept, tweak, and move to the next block.

---

## 0 · New Block Types Required

Add these values to `BlockType` before generating any world code.
Copilot will reference them automatically once they exist.

```java
// COPILOT: Add the following entries to the BlockType enum.
// These are needed by WorldGenerator for terrain surface and pillar materials.
//
//   GRASS        – top surface of plateau and island soil
//   DIRT         – one block below a grass surface
//   SAND         – beach / shallow seafloor near sea level
//   GRAVEL       – sub-sand seafloor layer
//   STONE        – generic mid-depth rock
//   DARK_STONE   – deep rock, pillar interior, stalactite tips
//   MOSSY_STONE  – wet underside of plateau, stalactite bases
//   CLIFF_STONE  – exposed lateral cliff face on plateau rim
//   WATER        – fills ocean basin up to SEA_WATER_LEVEL
//   AIR          – empty / default
//
// Example skeleton (adapt to your enum style):
//
//   GRASS, DIRT, SAND, GRAVEL, STONE, DARK_STONE, MOSSY_STONE, CLIFF_STONE, WATER, AIR;
```

---

## 1 · `getBlock` — Zone Router

This is the **sole public entry point**. All zone logic is delegated to private helpers.

```java
// COPILOT: Implement getBlock(int x, int y, int z) → BlockType.
//
// Zone layout (bottom to top, Y increases upward):
//
//   [0 .. BASE_LAND_CEILING=96]
//       → getBaseSeaBlock(x, y, z)
//
//   (BASE_LAND_CEILING .. PLATEAU_SLAB_BOTTOM=192)  ← "pillar zone"
//       → if y >= PLATEAU_SLAB_BOTTOM - STALACTITE_MAX_LENGTH (168):
//             BlockType stal = getStalactiteBlock(x, y, z);
//             if (stal != AIR) return stal;
//       → getPillarZoneBlock(x, y, z)
//
//   [PLATEAU_SLAB_BOTTOM=192 .. PLATEAU_TERRAIN_MAX=300]  ← slab + terrain
//       → if y < PLATEAU_SLAB_BOTTOM + PLATEAU_BOTTOM_MAX_DEPTH + 16 (240):
//             BlockType stal = getStalactiteBlock(x, y, z);
//             if (stal != AIR) return stal;
//       → getPlateauBlock(x, y, z)
//
//   Anything outside all ranges → AIR
//
// IMPORTANT: stalactites are checked BEFORE the plateau/pillar fallback so that
// the dish-carved air gap is filled by stalactites, not left empty.
```

---

## 2 · Pillar Zone — Shape & Surface

The pillar is a single large organic column that supports the plateau from below.
Its cross-section is an irregular blob (not a circle), tapers slightly with height,
and its base fans out and merges smoothly with the seafloor.

### 2a · Constants — add to the `PILLAR GRID CONSTANTS` section

```java
// COPILOT: Add pillar shape constants in the PILLAR GRID CONSTANTS block.
//
// The pillar is centred at world origin (0, 0).
// Its cross-section radius varies with height AND with multi-frequency noise
// so the silhouette is organic rather than cylindrical.
//
//   PILLAR_BASE_RADIUS      = 180    // half-width at sea floor (y ≈ 0)
//   PILLAR_TOP_RADIUS       = 95     // half-width just below the plateau slab
//   PILLAR_WAIST_FRACTION   = 0.55f  // height fraction (0-1) where narrowing ends
//   PILLAR_BLOB_FREQ        = 0.0035 // coarse blob noise frequency
//   PILLAR_BLOB_AMPLITUDE   = 0.28   // how much the blob noise distorts the radius
//   PILLAR_DETAIL_FREQ      = 0.011  // medium surface-crease noise
//   PILLAR_DETAIL_AMPLITUDE = 0.13
//   PILLAR_MICRO_FREQ       = 0.041  // fine surface chip noise
//   PILLAR_MICRO_AMPLITUDE  = 0.055
//   PILLAR_SEAFLOOR_BLEND   = 40     // vertical blend distance (blocks) at base
//   PILLAR_TOP_BLEND        = 30     // vertical blend distance at top
//   PILLAR_SEED_OFFSET      = 3711.0 // noise offset so pillar shape differs from plateau
```

### 2b · `isInsidePillar`

```java
// COPILOT: Replace the stub isInsidePillar(int x, int y, int z) → boolean.
//
// Algorithm:
//   1. Compute pillar height fraction  hFrac = y / (float) BASE_LAND_CEILING  [0..1]
//
//   2. Interpolate nominal radius:
//        if hFrac < PILLAR_WAIST_FRACTION:
//            nomRadius = lerp(PILLAR_BASE_RADIUS, PILLAR_TOP_RADIUS, hFrac / PILLAR_WAIST_FRACTION)
//        else:
//            nomRadius = PILLAR_TOP_RADIUS   // stays constant above waist
//
//   3. Noise-driven radius multiplier (sampled at the XZ column, constant per column):
//        double blob   = remap(noise2(x * PILLAR_BLOB_FREQ   + PILLAR_SEED_OFFSET, z * PILLAR_BLOB_FREQ))   * 2 - 1
//        double detail = remap(noise2(x * PILLAR_DETAIL_FREQ + PILLAR_SEED_OFFSET, z * PILLAR_DETAIL_FREQ)) * 2 - 1
//        double micro  = remap(noise2(x * PILLAR_MICRO_FREQ  + PILLAR_SEED_OFFSET, z * PILLAR_MICRO_FREQ))  * 2 - 1
//        double noiseOffset = blob * PILLAR_BLOB_AMPLITUDE
//                           + detail * PILLAR_DETAIL_AMPLITUDE
//                           + micro  * PILLAR_MICRO_AMPLITUDE
//        double effectiveRadius = nomRadius * (1.0 + noiseOffset)
//
//   4. Horizontal distance from world centre:
//        double dist = Math.sqrt(x*x + z*z)
//
//   5. Core solid test:  dist <= effectiveRadius
//
//   6. Seafloor base flare: near the bottom (hFrac < 0.25) blend extra radius
//      so the pillar fans out smoothly onto the ocean floor.
//        double baseFlare = (1.0 - hFrac / 0.25) * PILLAR_BASE_RADIUS * 0.35
//        if hFrac < 0.25:  effectiveRadius += baseFlare * (1 - hFrac/0.25)
//
//   7. Soft edge fade at top and bottom using smoothstep on (dist / effectiveRadius)
//      so the pillar boundary isn't a hard cliff.
//      Use a hash of (x, z) to seed a per-column dither threshold so the boundary
//      looks eroded rather than perfect.
//
// Returns true if the block is inside the pillar solid mass.
```

### 2c · `getPillarSurfaceBlock`

```java
// COPILOT: Replace the stub getPillarSurfaceBlock(int x, int y, int z) → BlockType.
//
// Rules (check in order):
//   • Top face exposed (isInsidePillar(x,y+1,z) == false AND y >= SEA_WATER_LEVEL):
//       → MOSSY_STONE  (wet top, often submerged or wave-washed)
//   • Any lateral neighbour exposed (±x or ±z is outside pillar):
//       → CLIFF_STONE  (visible cliff surface)
//   • Deep interior (no exposed neighbour within 2 blocks in any direction):
//       → DARK_STONE
//   • Default:
//       → STONE
```

---

## 3 · Plateau Slab Bottom — Dish + Roughness

```java
// COPILOT: Implement getPlateauSlabBottomHeight(int x, int z, double radialDistance) → int.
//
// Purpose: returns the Y coordinate of the plateau's curved underside at column (x,z).
// The underside is a shallow dish (flat centre, curving up toward the rim) with
// multi-frequency ridged noise that creates organic hanging columns.
//
// Step-by-step:
//
//   1. Parabolic dish base
//        normalizedRadius = clamp01(radialDistance / PLATEAU_BOTTOM_CURVE_RADIUS)  // 900
//        parabola         = pow(normalizedRadius, PLATEAU_BOTTOM_CURVE_POWER)       // 2.2
//        baseDepth        = parabola * PLATEAU_BOTTOM_MAX_DEPTH                     // 32
//      → at centre: depth=0 so slabBottom = PLATEAU_SLAB_BOTTOM (192)
//      → at outer radius: depth=32 so slabBottom = 224
//
//   2. Multi-frequency ridged underside roughness
//        ridgeA  = remap(noise2(x * 0.027 + 17.31, z * 0.027 +  5.13))   // broad columns
//        ridgeB  = remap(noise2(x * 0.058 +  3.71, z * 0.058 + 29.37))   // secondary columns
//        valleyA = remap(1 - |noise2(x * 0.0093 + 43.11, z * 0.0093 + 11.71)|)  // ridged valleys
//        valleyB = remap(1 - |noise2(x * 0.0178 +  7.97, z * 0.0178 + 63.31)|)
//        micro   = remap(noise2(x * 0.119  +  2.37, z * 0.119  + 51.13))
//
//        roughness = ridgeA*0.38 + ridgeB*0.24 + valleyA*0.22 + valleyB*0.12 + micro*0.04
//
//   3. Edge influence (roughness is zero at centre, full at edge)
//        edgeInfluence  = clamp01(radialDistance / (PLATEAU_BOTTOM_CURVE_RADIUS * 0.45))
//        scaledRoughness = roughness * 16.0 * edgeInfluence
//
//   4. Final height
//        finalDepth = baseDepth + scaledRoughness
//        return PLATEAU_SLAB_BOTTOM + (int) round(finalDepth)
```

---

## 4 · `isPlateauSolid` — Rim Thinning

```java
// COPILOT: Implement isPlateauSolid(int x, int y, int z) → boolean.
//
// Checks (in order — return false on first failure):
//
//   1. Dish bottom:   y < getPlateauSlabBottomHeight(x, z, sqrt(x²+z²))  → false
//   2. Slab top cap:  y > PLATEAU_SLAB_TOP (240)                          → false
//   3. Outer disk:    radialDistance > PLATEAU_DISK_RADIUS (1200)         → false
//
//   4. Noise-based shape edge:
//        edgeDensity = getPlateauEdgeDensity(x, z)
//        if edgeDensity <= PLATEAU_EDGE_THRESHOLD (0.5)                   → false
//
//   5. Rim thinning via QUADRATIC power curve on edgeStrength:
//        edgeStrength   = clamp01((edgeDensity - EDGE_THRESHOLD) / (1 - EDGE_THRESHOLD))
//        rimCurve       = edgeStrength²                        ← quadratic, not linear
//        diskStrength   = clamp01((DISK_RADIUS - radialDistance) / DISK_EDGE_BAND)
//        combinedFactor = rimCurve * pow(diskStrength, 1.3)
//
//        edgeThickNoise = remap(noise2(x * PLATEAU_EDGE_THICKNESS_NOISE_FREQ + CLIFF_SEED * 1.17,
//                                     z * PLATEAU_EDGE_THICKNESS_NOISE_FREQ + CLIFF_SEED * 0.83))
//        minThickness   = round(lerp(EDGE_MIN_THICKNESS, EDGE_MAX_THICKNESS, edgeThickNoise))  // 2-4
//        localThickness = max(minThickness, round(lerp(minThickness, SLAB_THICKNESS, combinedFactor)))
//        localTop       = slabBottomAtXZ + localThickness - 1
//        if y > localTop                                                   → false
//
//   6. Vertical fade (probabilistic soft boundary):
//        slabFraction = (y - slabBottomAtXZ) / localThickness
//        if slabFraction >= PLATEAU_VERTICAL_FADE_START (0.65):
//            t = (slabFraction - FADE_START) / (1 - FADE_START)
//            fadeFactor = lerp(1.0, PLATEAU_TOP_FADE_MIN (0.75), clamp01(t))
//        else: fadeFactor = 1.0
//        return (edgeDensity * fadeFactor) > EDGE_THRESHOLD
//
// Key thickness outputs to verify:
//   edgeStrength=0.0 → localThickness ≈ 2-4 blocks  (rim)
//   edgeStrength=0.3 → localThickness ≈ 6  blocks
//   edgeStrength=0.55→ localThickness ≈ 16 blocks
//   edgeStrength=1.0 → localThickness = SLAB_THICKNESS (48)
// Note: thickness is purely emergent from combinedFactor — do NOT hardcode these values.
```

---

## 5 · Stalactites — Full Implementation

```java
// COPILOT: Implement getStalactiteBlock(int x, int y, int z) → BlockType.
//
// Stalactites hang from the curved underside of the plateau slab.
// They are cone-shaped, domain-warped, and scaled by how deep the dish is at (x,z).
//
// Full algorithm:
//
//   ── Quick cull ──────────────────────────────────────────────────────────────
//   double radialDistance = sqrt(x²+z²)
//   if radialDistance > PLATEAU_DISK_RADIUS + 60  → AIR
//
//   ── Plateau presence ────────────────────────────────────────────────────────
//   double edgeDensity = getPlateauEdgeDensity(x, z)
//   if edgeDensity <= PLATEAU_EDGE_THRESHOLD       → AIR
//
//   ── Slab bottom at this XZ column ───────────────────────────────────────────
//   int slabBottom = getPlateauSlabBottomHeight(x, z, radialDistance)
//   if y >= slabBottom                             → AIR   (inside slab, not below it)
//
//   ── Dish depth gate ─────────────────────────────────────────────────────────
//   int dishDepth = slabBottom - PLATEAU_SLAB_BOTTOM    // 0 at centre, up to ~48 at edge
//   if dishDepth < STALACTITE_MIN_DISH_DEPTH (4)        → AIR   (underside too flat)
//
//   int depth = slabBottom - y                          // 1 = just below surface
//   if depth > STALACTITE_MAX_LENGTH (24)               → AIR
//
//   ── Domain warp ─────────────────────────────────────────────────────────────
//   double wx = noise2(x * WARP_FREQ + 11.37, z * WARP_FREQ +  5.71) * WARP_STRENGTH
//   double wz = noise2(x * WARP_FREQ + 23.13, z * WARP_FREQ + 17.93) * WARP_STRENGTH
//   double sx = x + wx,  sz = z + wz
//     (STALACTITE_WARP_FREQ = 0.0065, STALACTITE_WARP_STRENGTH = 11.0)
//
//   ── Cluster noise — broad grouping ──────────────────────────────────────────
//   double cluster = remap(noise2(sx * CLUSTER_FREQ, sz * CLUSTER_FREQ))
//     (STALACTITE_CLUSTER_FREQ = 0.019)
//
//   // Rim boost: denser stalactites just inside the plateau edge
//   double edgeStrength = clamp01((edgeDensity - EDGE_THRESHOLD) / (1 - EDGE_THRESHOLD))
//   double rimBoost = (1 - smoothstep(0, STALACTITE_RIM_BOOST_FADE (0.35), edgeStrength))
//                     * STALACTITE_RIM_BOOST (0.28)
//   cluster = min(1.0, cluster + rimBoost)
//
//   if cluster < STALACTITE_CLUSTER_THRESH (0.36)       → AIR
//
//   ── Spike noise — per-column height variation ────────────────────────────────
//   double spike  = remap(noise2(sx * SPIKE_FREQ, sz * SPIKE_FREQ))
//   double ridged = remap(1 - |noise2(sx * RIDGE_FREQ + 41.11, sz * RIDGE_FREQ + 13.37)|)
//   double micro  = remap(noise2(sx * SPIKE_FREQ*3.13 + 7.77, sz * SPIKE_FREQ*3.13 + 19.31))
//   double heightField = spike*0.50 + ridged*0.35 + micro*0.15
//     (STALACTITE_SPIKE_FREQ = 0.105, STALACTITE_RIDGE_FREQ = 0.071)
//
//   ── Maximum length for this column ──────────────────────────────────────────
//   double clusterStrength = (cluster - CLUSTER_THRESH) / (1 - CLUSTER_THRESH)
//   double dishScale       = min(1.0, dishDepth / (double) PLATEAU_BOTTOM_MAX_DEPTH)
//   int maxLen = max(1, round(heightField * clusterStrength * dishScale * STALACTITE_MAX_LENGTH))
//
//   if depth > maxLen                                   → AIR
//
//   ── Cone taper via threshold erosion ────────────────────────────────────────
//   // At base (depth=1): requiredStrength ≈ CLUSTER_THRESH  → wide base survives
//   // At tip  (depth=maxLen): requiredStrength = 1.0         → only spire centres survive
//   double tipFrac          = (double) depth / max(1, maxLen)
//   double requiredStrength = lerp(CLUSTER_THRESH, 1.0, tipFrac * tipFrac)
//   if cluster < requiredStrength                       → AIR
//
//   ── Material gradient ────────────────────────────────────────────────────────
//   if tipFrac < 0.35  → MOSSY_STONE   (wet, near attachment point)
//   if tipFrac < 0.72  → STONE         (mid shaft)
//   else               → DARK_STONE    (dry tip)
```

---

## 6 · Water Features (Plateau Surface)

### 6a · Lake Basins

```java
// COPILOT: Implement isInsideLakeBasin(int x, int z) → boolean.
//
// Lakes sit on the plateau surface (above PLATEAU_SLAB_TOP) in low-elevation pockets.
// They must NOT overlap hot springs.
//
// Algorithm:
//   1. Sample a blob-shaped noise field:
//        double lakeNoise = remap(noise2(x * 0.008 + 500.0, z * 0.008 + 500.0))
//                         + remap(noise2(x * 0.018 + 500.0, z * 0.018 + 500.0)) * 0.4
//      Normalise to [0,1].
//   2. Terrain elevation at this column (distance above PLATEAU_SLAB_TOP).
//        Use a cheap proxy: remap(noise2(x*0.012, z*0.012)) * 30  (0-30 blocks above slab).
//   3. Lake forms where:
//        lakeNoise   > LAKE_THRESHOLD (0.4)
//        elevation   < LAKE_ELEVATION_CUTOFF (5)
//        !isInsideHotSpring(x, z)
// Returns true if this XZ column is inside a lake basin.
```

### 6b · Hot Springs

```java
// COPILOT: Implement isInsideHotSpring(int x, int z) → boolean.
//
// Hot springs use a sparse grid with per-cell random placement.
// Grid spacing: HOT_SPRING_GRID_SPACING = 64 blocks.
// Spawn probability per cell: HOT_SPRING_SPAWN_PROBABILITY = 0.25.
//
// Algorithm:
//   1. Determine grid cell:
//        int cellX = floor(x / HOT_SPRING_GRID_SPACING)
//        int cellZ = floor(z / HOT_SPRING_GRID_SPACING)
//   2. Seed a deterministic hash from (cellX, cellZ, worldSeed):
//        long hash = cellX * 341873128712L ^ cellZ * 132897987541L ^ worldSeed
//        Use this hash to generate:
//          · spawnRoll (0-1): if > SPAWN_PROBABILITY → no spring in this cell
//          · jitterX, jitterZ in [0, HOT_SPRING_GRID_SPACING)
//          · springRadius in [HOT_SPRING_MIN_ELEVATION, HOT_SPRING_MAX_ELEVATION]
//            (HOT_SPRING_MIN_ELEVATION=2, HOT_SPRING_MAX_ELEVATION=30  → reuse as min/max RADIUS)
//   3. Spring centre = (cellX * SPACING + jitterX,  cellZ * SPACING + jitterZ)
//   4. Return true if dist(x,z, springCentre) <= springRadius
//      AND the terrain elevation at that centre is within the hot-spring elevation range.
```

### 6c · River Channels

```java
// COPILOT: Implement isInsideRiverChannel(int x, int z) → boolean.
//
// Rivers are domain-warped ridge-noise channels that flow across the plateau surface.
//
// Algorithm:
//   1. Domain warp:
//        double wx = noise2(x * 0.007 + 800.0, z * 0.007 + 800.0) * 28.0
//        double wz = noise2(x * 0.007 + 900.0, z * 0.007 + 900.0) * 28.0
//        double sx = x + wx,  sz = z + wz
//
//   2. Ridge value from folded noise:
//        double ridge = 1.0 - |noise2(sx * 0.005, sz * 0.005)|   // [0..1], 1 = ridge centre
//
//   3. Threshold to create a narrow channel:
//        double sharpRidge = smoothstep(RIVER_NOISE_TARGET - 0.08, RIVER_NOISE_TARGET + 0.01, ridge)
//        (RIVER_NOISE_TARGET = 0.75)
//
//   4. Suppress rivers inside hot springs and inside lakes:
//        if isInsideHotSpring(x, z) || isInsideLakeBasin(x, z) → return false
//
//   5. Elevation bounds: channel only exists where terrain is not too high or too low
//        (placeholder: always true if passes step 4 — implement full elevation check later)
//
//   Returns true if sharpRidge > 0.5.
```

---

## 7 · Plateau Surface Block Selection

```java
// COPILOT: Rewrite getPlateauSlabBlock(int x, int y, int z) → BlockType.
//
// Priority order (check from highest to lowest priority):
//
//   1. Top-exposed surface:
//        if !isPlateauSolid(x, y+1, z):
//          · above snowline (y >= SNOWLINE=170) AND y >= PLATEAU_SLAB_TOP - 5:
//              → SNOW (if BlockType.SNOW exists, else STONE)
//          · inside hot spring basin (isInsideHotSpring(x,z)):
//              → HOT_SPRING_STONE or MOSSY_STONE
//          · inside lake basin (isInsideLakeBasin(x,z)):
//              → SAND  (lake bed under water)
//          · default top surface:
//              → GRASS
//
//   2. One block below top (dirt layer):
//        if isPlateauSolid(x,y+1,z) && !isPlateauSolid(x,y+2,z):
//            → DIRT
//
//   3. Underside exposed (bottom of slab):
//        if !isPlateauSolid(x,y-1,z) && (y - PLATEAU_SLAB_BOTTOM) <= PLATEAU_UNDERSIDE_DEPTH (8):
//            → MOSSY_STONE
//
//   4. Lateral cliff face:
//        if any of (x±1,y,z) or (x,y,z±1) is not solid
//           AND (y - PLATEAU_SLAB_BOTTOM) <= PLATEAU_CLIFF_FACE_DEPTH (16):
//            → CLIFF_STONE
//
//   5. Default interior:
//        → STONE
```

---

## 8 · Integration Checklist

Run these checks after Copilot fills each method:

| # | Check | Expected |
|---|-------|----------|
| 1 | `getBlock(0, 64, 0)` | `WATER` or seafloor material |
| 2 | `getBlock(0, 192, 0)` *(centre bottom of plateau)* | Should be AIR (dish carves centre) or STONE if dish=0 |
| 3 | `getBlock(0, 240, 0)` *(plateau top surface)* | `GRASS` |
| 4 | `getBlock(50, 185, 0)` *(pillar zone, rim stalactite range)* | `MOSSY_STONE` / `STONE` / `DARK_STONE` or `AIR` |
| 5 | `getBlock(0, 100, 0)` *(pillar zone, inside pillar)* | `STONE` / `CLIFF_STONE` / `DARK_STONE` |
| 6 | `getBlock(1300, 220, 0)` *(outside disk radius)* | `AIR` |
| 7 | Rim at edge of plateau | Thickness 2-4 blocks |
| 8 | Centre of plateau | Thickness ≈ 48 blocks |

---

## 9 · Noise Helpers (already implemented — reference only)

Copilot will reference these. Do **not** regenerate them.

```
noise2(x, z)               // PerlinNoise raw 2-D sample → [-1, 1]
noise3(x, y, z)            // PerlinNoise raw 3-D sample
fractal2 / fractal3        // fBm wrappers
remap(v)                   // [-1,1] → [0,1]  i.e. (v+1)*0.5
lerp(a, b, t)
smoothstep(e0, e1, x)
clamp01(v)
multiScaleNoise2(x, z, offset)
getPlateauEdgeDensity(x, z)
getPlateauSlabBottomHeight(x, z, radialDist)
isPlateauSolid(x, y, z)
```

---

## 10 · Suggested Copilot Workflow

1. **Add BlockType entries** (section 0) — compile clean.
2. **`getBlock`** (section 1) — router only, ~25 lines.
3. **Pillar constants** (section 2a) — paste into the existing constants block.
4. **`isInsidePillar`** (section 2b) — ~40 lines with seafloor flare.
5. **`getPillarSurfaceBlock`** (section 2c) — ~15 lines.
6. **`getPlateauSlabBottomHeight`** (section 3) — replaces the existing stub.
7. **`isPlateauSolid`** (section 4) — replaces the existing stub.
8. **`getStalactiteBlock`** (section 5) — fills the stub.
9. **Water features** (section 6) — fill stubs in order: lakes → hot springs → rivers.
10. **`getPlateauSlabBlock`** (section 7) — replaces existing method.
11. Run integration checks (section 8).

> **Tip:** If Copilot drifts from the spec, prepend the relevant constant names
> (e.g. `// Uses: STALACTITE_CLUSTER_THRESH, STALACTITE_WARP_FREQ`) to the comment
> and re-trigger. The constant names are the strongest anchors for Copilot.
