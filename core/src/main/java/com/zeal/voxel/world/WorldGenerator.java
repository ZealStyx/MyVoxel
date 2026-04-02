package com.zeal.voxel.world;

import com.zeal.voxel.block.BlockType;

/**
 * Procedural voxel world generator implementing a multi-zone terrain generation system.
 * Zones are checked in order from bottom to top using Y-range guards.
 * The sole public entry point is getBlock(x, y, z) which returns the block type at that position.
 */
@SuppressWarnings("unused")
public class WorldGenerator {
    private final long worldSeed;
    private final PerlinNoise noise;

    // ============================================================================
    // VERTICAL ZONE BOUNDARIES
    // ============================================================================
    
    // Y value where base sea water surface sits
    private static final int SEA_WATER_LEVEL = 64;

    // Y where base sea zone ends (must stay above island surface max to avoid hard top clipping)
    private static final int BASE_LAND_CEILING = 80; // tightened to keep deep sea zone visually focused

    // Y where pillar zone begins
    private static final int PILLAR_ZONE_BOTTOM = BASE_LAND_CEILING + 1;

    // Maximum height in blocks that mountains can rise above the plateau slab surface
    private static final int MAX_MOUNTAIN_HEIGHT = 0;
    
    // Floating slab placement limit and thickness definitions.
    private static final int PLATEAU_SLAB_BOTTOM = 150; // minimum possible base
    private static final int PLATEAU_SLAB_THICKNESS = 16;
    private static final int PLATEAU_SLAB_TOP = 200; // global maximum cap for plateau

    // Maximum height that plateau terrain can reach (hard cap for the whole world)
    private static final int PLATEAU_TERRAIN_MAX = PLATEAU_SLAB_TOP;

    // Allow plateau base to slide through this range (with thickness still 16).
    private static final int PLATEAU_SPAWN_Y_MIN = 150;
    private static final int PLATEAU_SPAWN_Y_MAX = 184; // so base+thickness stays <= 200

    private static final double PLATEAU_SPAWN_Y_NOISE_FREQ = 0.00055;
    private static final double PLATEAU_SPAWN_Y_SEED_OFFSET = 8831.0;
    
    private static final int STALACTITE_MAX_LENGTH = 24;
    private static final int PLATEAU_STALACTITE_DISH_BUFFER = 16;

    // Cave zone below sea level
    private static final int CAVE_ZONE_BOTTOM = -128;
    private static final int CAVE_ZONE_TOP = -1;
    private static final double CAVE_NOISE_FREQ = 0.035;
    private static final double CAVE_VERTICAL_FREQ = 0.028;
    private static final double CAVE_THRESHOLD = 0.5;
    private static final double CAVE_DEPTH_FALLOFF = 0.40;

    // Fracture chasms through plateau
    private static final double PLATEAU_FRACTURE_FREQ = 0.0065;
    private static final double PLATEAU_FRACTURE_THRESHOLD = 0.82;
    private static final int PLATEAU_FRACTURE_OPEN_MIN_Y = SEA_WATER_LEVEL + 4;
    private static final int PLATEAU_FRACTURE_OPEN_MAX_Y = PLATEAU_SLAB_TOP + 6;

    // ============================================================================
    // PLATEAU TERRAIN CONSTANTS
    // ============================================================================
    
    // Y above which snow replaces grass on mountain surfaces
    private static final int SNOWLINE = 195;

    // Plateau surface flattening (more plains like).
    private static final double PLATEAU_SURFACE_FLAT_FRACTION = 0.65;
    private static final int PLATEAU_SURFACE_MAX_VAR = 12;
    
    // Y at which still lake water surfaces sit (a few blocks above plateau slab top)
    private static final int LAKE_LEVEL = PLATEAU_SLAB_TOP + 3;
    
    // Y at which hot spring water surfaces sit (slightly higher than lake level)
    private static final int HOT_SPRING_LEVEL = LAKE_LEVEL + 2;
    
    // ============================================================================
    // PILLAR SHAPE CONSTANTS
    // ============================================================================

    private static final double PILLAR_BASE_RADIUS = 72.0; // reduced for thinner support
    private static final double PILLAR_TOP_RADIUS = 36.0;
    private static final int PILLAR_WARP_OCTAVES = 5;
    private static final double PILLAR_WARP_BASE_FREQ = 0.0028;
    private static final double PILLAR_WARP_LACUNARITY = 2.1;
    private static final double PILLAR_WARP_PERSISTENCE = 0.52;
    private static final double PILLAR_WARP_MAX_DISPLACEMENT = 0.55;
    private static final double PILLAR_DOMAIN_WARP_FREQ = 0.0009;
    private static final double PILLAR_DOMAIN_WARP_STRENGTH = 140.0;
    private static final double PILLAR_WARP_SEED_A = 2311.0;
    private static final double PILLAR_WARP_SEED_B = 4703.0;
    private static final double PILLAR_WARP_SEED_C = 7129.0;
    private static final double PILLAR_WARP_OCTAVE_SEED_SCALE = 0.001;
    private static final double PILLAR_WARP_OCTAVE_SEED_STEP = 1337.0;
    private static final int PILLAR_SEAFLOOR_PENETRATION = 12;
    private static final int PILLAR_SEAFLOOR_BLEND_HEIGHT = 40;
    private static final double PILLAR_SEAFLOOR_FLARE_STRENGTH = 1.4; // stronger root flaring for visible blend
    private static final double PILLAR_SEAFLOOR_DENSITY_THRESHOLD = 0.14;
    private static final double PILLAR_WAIST_HEIGHT_FRACTION = 0.46; // less symmetric hourglass
    private static final double PILLAR_WAIST_NARROWING = 0.58;
    private static final double PILLAR_RAGGEDNESS_STRENGTH = 0.18; // adds natural asymmetry
    private static final double PILLAR_RAGGEDNESS_FREQ = 0.022;
    private static final double PILLAR_EDGE_INNER_RATIO = 0.92;
    private static final double PILLAR_EDGE_OUTER_RATIO = 1.08;
    private static final double PILLAR_CRACKED_RATIO = 0.80;
    private static final int PILLAR_DEEP_INTERIOR_SAMPLE_DISTANCE = 3;
    private static final double PILLAR_EDGE_SURFACE_NOISE_FREQ = 0.13;
    private static final double PILLAR_EDGE_SURFACE_NOISE_SEED_X = 77.31;
    private static final double PILLAR_EDGE_SURFACE_NOISE_SEED_Z = 13.71;
    private static final double STEM_BLEND_START_FRACTION = 0.4;
    private static final double STEM_BLEND_END_FRACTION = 1.0;
    private static final double STEM_THRESHOLD_LOW = 0.35;
    private static final double STEM_THRESHOLD_HIGH = 0.5;
    
    // ============================================================================
    // HOT SPRING GRID CONSTANTS
    // ============================================================================
    
    // Block distance between hot spring grid cell centers
    private static final int HOT_SPRING_GRID_SPACING = 64;
    
    // Spawn probability per grid cell (relatively sparse — well under half)
    private static final float HOT_SPRING_SPAWN_PROBABILITY = 0.25f;
    
    // Elevation range above the plateau slab within which hot springs are allowed to form
    private static final int HOT_SPRING_MIN_ELEVATION = 2;
    private static final int HOT_SPRING_MAX_ELEVATION = 30;
    
    // ============================================================================
    // NOISE SAMPLING CONSTANTS
    // ============================================================================
    
    // Base sea zone boundaries and relief
    private static final int SEAFLOOR_BASE_Y = 2; // lower to deepen ocean
    private static final int SEAFLOOR_RELIEF = 28; // more variance and deeper channels

    // Island mask and cliff steepness
    private static final double ISLAND_MASK_THRESHOLD = 0.52; // raise threshold -> less land
    private static final double ISLAND_MASK_EDGE_WIDTH = 0.020;
    private static final double ISLAND_LAND_COVERAGE_BIAS = 0.020; // further reduce island coverage

    // Island underwater dome shape
    private static final double ISLAND_DENSITY_THRESHOLD = 0.0;
    private static final double ISLAND_BASE_RADIUS_SCALE = 3.5;
    private static final double ISLAND_EARLY_EXIT_FRACTION = 0.30;
    private static final int ISLAND_SEAFLOOR_BLEND_HEIGHT = 10;
    private static final double ISLAND_SEAFLOOR_BLEND_STRENGTH = 0.65;
    private static final double ISLAND_UNDERWATER_SPREAD = 0.24;

    // Island surface terrain (replaces flat top)
    private static final int ISLAND_SURFACE_AMPLITUDE = 8;
    private static final double ISLAND_SURFACE_FREQ_COARSE = 0.018;
    private static final double ISLAND_SURFACE_FREQ_MEDIUM = 0.045;
    private static final double ISLAND_SURFACE_FREQ_FINE = 0.110;
    private static final double ISLAND_SURFACE_FREQ_ULTRA = 0.245;
    private static final double ISLAND_SURFACE_AMP_COARSE = 0.45;
    private static final double ISLAND_SURFACE_AMP_MEDIUM = 0.28;
    private static final double ISLAND_SURFACE_AMP_FINE = 0.17;
    private static final double ISLAND_SURFACE_AMP_ULTRA = 0.10;
    private static final double ISLAND_SURFACE_SEED_OFFSET = 7391.0;
    private static final double ISLAND_SURFACE_EDGE_DAMPING = 0.7;
    private static final int ISLAND_SURFACE_MIN_ABOVE_SEA = 1;
    private static final int ISLAND_SURFACE_FBM_OCTAVES = 5;
    private static final double ISLAND_SURFACE_FBM_PERSISTENCE = 0.56;
    private static final double ISLAND_SURFACE_FBM_LACUNARITY = 2.12;
    private static final double ISLAND_SURFACE_DETAIL_SEED_OFFSET = 11231.0;
    private static final double ISLAND_SURFACE_WARP_FREQ = 0.009;
    private static final double ISLAND_SURFACE_WARP_STRENGTH = 22.0;

    // Island top range and hill variation
    private static final int ISLAND_TOP_MIN = SEA_WATER_LEVEL - 2;
    private static final int ISLAND_TOP_MAX = SEA_WATER_LEVEL + 6; // keep islands low and mostly shallow
    private static final int ISLAND_HILL_AMPLITUDE = 5;
    private static final int ISLAND_MAX_SURFACE_Y = SEA_WATER_LEVEL + 20;
    private static final double ISLAND_TOP_MASK_HEIGHT_MULTIPLIER = 1.0;
    private static final int ISLAND_PROFILE_POWER = 4; // Use 2 for steeper, 4 for smoother
    private static final double ISLAND_PROFILE_BLEND = 0.42;

    // Island clustering and shape frequencies
    private static final double ISLAND_CLUSTER_FREQ = 0.0007;
    private static final double ISLAND_CLUSTER_STRENGTH = 0.35;
    private static final double ISLAND_SHAPE_FREQ_COARSE = 0.003;
    private static final double ISLAND_SHAPE_FREQ_MEDIUM = 0.008;
    private static final double ISLAND_SHAPE_FREQ_FINE = 0.018;

    // Island hill frequencies
    private static final double ISLAND_HILL_FREQ_COARSE = 0.012;
    private static final double ISLAND_HILL_FREQ_FINE = 0.028;
    private static final double ISLAND_MICRO_FREQ = 0.061;
    private static final double ISLAND_MICRO_AMPLITUDE = 1.6;
    private static final double ISLAND_MASK_MICRO_FREQ = 0.041;
    private static final double ISLAND_MASK_MICRO_STRENGTH = 0.06;
    private static final double ISLAND_HILL_SEED_OFFSET = 4096.0;
    private static final double ISLAND_MASK_LAYER_SEED = 1711.0;

    // Seafloor frequencies
    private static final double SEAFLOOR_FREQ_LOW = 0.0017;
    private static final double SEAFLOOR_FREQ_MID = 0.0065;
    private static final double SEAFLOOR_FREQ_FINE = 0.017;
    private static final double SEAFLOOR_WEIGHT_LOW = 0.68;
    private static final double SEAFLOOR_WEIGHT_MID = 0.26;
    private static final double SEAFLOOR_WEIGHT_FINE = 0.06;
    private static final int SEAFLOOR_FBM_OCTAVES_LOW = 4;
    private static final int SEAFLOOR_FBM_OCTAVES_MID = 3;
    private static final int SEAFLOOR_FBM_OCTAVES_FINE = 2;
    private static final double SEAFLOOR_FBM_PERSISTENCE = 0.54;
    private static final double SEAFLOOR_FBM_LACUNARITY = 2.0;
    private static final double SEAFLOOR_PATCH_FREQ = 0.0009;
    private static final double SEAFLOOR_PATCH_RIDGE_FREQ = 0.0022;
    private static final double SEAFLOOR_PATCH_THRESHOLD = 0.72;
    private static final double SEAFLOOR_PATCH_STRENGTH = 0.18;
    private static final double SEAFLOOR_LAYER_SEED = 733.0;

    // Island shape/hill blend weights
    private static final double ISLAND_SHAPE_WEIGHT_COARSE = 0.60;
    private static final double ISLAND_SHAPE_WEIGHT_MEDIUM = 0.30;
    private static final double ISLAND_SHAPE_WEIGHT_FINE = 0.10;
    private static final double ISLAND_HILL_BLEND_COARSE = 0.65;
    private static final double ISLAND_HILL_BLEND_FINE = 0.35;
    private static final double ISLAND_EROSION_NEIGHBOR_RADIUS = 34.0;
    private static final double ISLAND_EROSION_STRENGTH = 2.0;
    private static final double ISLAND_RAVINE_FREQ = 0.013;
    private static final double ISLAND_RAVINE_THRESHOLD = 0.76;
    private static final double ISLAND_RAVINE_DEPTH = 4.5;

    private static final double BASE_SEA_EPSILON = 0.0001;
    private static final float BASE_SEA_EPSILON_F = 0.0001f;

    // Base sea material depth bands relative to sea level
    private static final int SAND_DEPTH = 2;
    private static final int GRAVEL_DEPTH = 7;
    private static final int STONE_DEPTH = 22;
    
    // Plateau edge definition and soft tapering for gradual thinning
    private static final float PLATEAU_EDGE_THRESHOLD = 0.48f;
    private static final float PLATEAU_EDGE_SOFT_BAND = 0.24f;
    private static final float PLATEAU_EDGE_HARD_BAND = 0.12f;
    private static final float PLATEAU_CLIFF_SHARPNESS_LOW = 0.15f;
    private static final float PLATEAU_CLIFF_SHARPNESS_HIGH = 0.75f;

    // Plateau slab shape field (3 octave-like bands)
    private static final double PLATEAU_SHAPE_FREQ_COARSE = 0.0016;
    private static final double PLATEAU_SHAPE_FREQ_MEDIUM = 0.0045;
    private static final double PLATEAU_SHAPE_FREQ_FINE = 0.011;
    private static final double PLATEAU_SHAPE_AMP_COARSE = 0.55;
    private static final double PLATEAU_SHAPE_AMP_MEDIUM = 0.30;
    private static final double PLATEAU_SHAPE_AMP_FINE = 0.15;

    // Plateau cliff sharpness noise
    private static final double PLATEAU_CLIFF_FREQ = 0.007;
    private static final double PLATEAU_CLIFF_SEED_OFFSET = 913.37;

    // Plateau slab vertical fade and material bands
    private static final float PLATEAU_VERTICAL_FADE_START = 0.65f;
    private static final float PLATEAU_TOP_FADE_MIN = 0.90f;
    private static final double PLATEAU_DISK_RADIUS = 1200.0;
    private static final int PLATEAU_UNDERSIDE_DEPTH = 8;
    private static final int PLATEAU_CLIFF_FACE_DEPTH = 16;

    // Plateau thickness scales by local footprint density.
    private static final double PLATEAU_THICKNESS_DENSITY_SCALE = 0.55;
    private static final double PLATEAU_THICKNESS_RIM_MIN_FRACTION = 0.03;
    private static final double PLATEAU_THICKNESS_TAPER_POWER = 1.0;
    private static final double PLATEAU_THICKNESS_NOISE_FREQ = 0.019;
    private static final int PLATEAU_THICKNESS_NOISE_AMPLITUDE = 5;
    private static final double PLATEAU_THICKNESS_NOISE_SEED = 6173.0;
    private static final double PLATEAU_CRACKED_STONE_THIN_OFFSET = 0.10;
    
    // Plateau slab bottom curvature parameters (dish-shaped underside)
    private static final int PLATEAU_BOTTOM_MAX_DEPTH = 10; // reduce gap from pillar to slab
    private static final double PLATEAU_BOTTOM_CURVE_RADIUS = 900.0; // Radius at which max depth is reached
    private static final double PLATEAU_BOTTOM_CURVE_POWER = 2.2;

    // Stalactite field parameters
    private static final int STALACTITE_MIN_DISH_DEPTH = 4;
    private static final double STALACTITE_WARP_FREQ = 0.0065;
    private static final double STALACTITE_WARP_STRENGTH = 11.0;
    private static final double STALACTITE_CLUSTER_FREQ = 0.019;
    private static final double STALACTITE_SPIKE_FREQ = 0.105;
    private static final double STALACTITE_RIDGE_FREQ = 0.071;
    private static final double STALACTITE_CLUSTER_THRESH = 0.36;
    private static final double STALACTITE_RIM_BOOST_FADE = 0.35;
    private static final double STALACTITE_RIM_BOOST = 0.28;
    private static final double STALACTITE_MOSSY_FRACTION = 0.35;
    private static final double STALACTITE_CALCITE_FRACTION = 0.82;
    
    // Lake and river parameters
    private static final float LAKE_THRESHOLD = 0.4f;
    private static final float LAKE_ELEVATION_CUTOFF = 5; // blocks above plateau slab
    private static final float RIVER_NOISE_TARGET = 0.75f;
    private static final float RIVER_SHARPNESS = 2.5f;
    
    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================
    
    public WorldGenerator(long worldSeed) {
        this.worldSeed = worldSeed;
        this.noise = new PerlinNoise(worldSeed);
    }

    public static int seaWaterLevel() {
        return SEA_WATER_LEVEL;
    }

    public static int plateauHeightAboveSea() {
        return PLATEAU_SLAB_BOTTOM - SEA_WATER_LEVEL;
    }

    public static int plateauSlabThickness() {
        return PLATEAU_SLAB_THICKNESS;
    }

    public static int baseLandCeiling() {
        return BASE_LAND_CEILING;
    }

    public static int plateauSpawnYMin() {
        return PLATEAU_SPAWN_Y_MIN;
    }

    public static int plateauSlabBottom() {
        return PLATEAU_SLAB_BOTTOM;
    }

    public static int plateauSlabTop() {
        return plateauSlabBottom() + PLATEAU_SLAB_THICKNESS;
    }

    public static int stalactiteMaxLength() {
        return STALACTITE_MAX_LENGTH;
    }

    public static double islandMaskThreshold() {
        return ISLAND_MASK_THRESHOLD;
    }

    /**
     * Returns the local plateau slab base Y for column (x, z).
     */
    public int getPlateauBaseY(int x, int z) {
        // Plateau base is a noise-driven value within [PLATEAU_SPAWN_Y_MIN, PLATEAU_SPAWN_Y_MAX].
        double n = remap(noise2(x * PLATEAU_SPAWN_Y_NOISE_FREQ,
                z * PLATEAU_SPAWN_Y_NOISE_FREQ + PLATEAU_SPAWN_Y_SEED_OFFSET));
        int candidate = PLATEAU_SPAWN_Y_MIN + (int) Math.round(n * (PLATEAU_SPAWN_Y_MAX - PLATEAU_SPAWN_Y_MIN));
        return Math.max(PLATEAU_SPAWN_Y_MIN, Math.min(candidate, PLATEAU_SPAWN_Y_MAX));
    }
    
    // ============================================================================
    // PUBLIC ENTRY POINT
    // ============================================================================
    
    /**
     * Gets the block type at the specified world coordinates.
     * This is the sole public entry point called once per voxel during chunk generation.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return the BlockType at this position
     */
    public BlockType getBlock(int x, int y, int z) {
        // Cave zone: use 3D cave noise in the underground region.
        if (y >= CAVE_ZONE_BOTTOM && y <= CAVE_ZONE_TOP && isCaveVoid(x, y, z)) {
            return BlockType.AIR;
        }

        int seafloorHeight = getSeafloorHeight(x, z);
        int localBaseY = getPlateauBaseY(x, z);
        double edgeDensity = getPlateauEdgeDensity(x, z);
        int localThickness = getLocalPlateauThickness(x, z, edgeDensity);
        double radialDistance = Math.sqrt((double) x * x + (double) z * z);
        int dishBottom = getPlateauSlabBottomHeight(x, z, radialDistance);
        double islandMaskRaw = getIslandMaskRaw(x, z);
        int islandSurface = islandMaskRaw >= ISLAND_MASK_THRESHOLD
            ? getIslandSurfaceTop(x, z, islandMaskRaw)
            : -1;

        return getBlock(
            x, y, z,
            seafloorHeight,
            localBaseY,
            edgeDensity,
            localThickness,
            radialDistance,
            dishBottom,
            islandMaskRaw,
            islandSurface);
    }

    /**
     * Cached variant used by column generators to avoid repeated 2D evaluations per voxel.
     */
    public BlockType getBlock(
            int x,
            int y,
            int z,
            int seafloorHeight,
            int localBaseY,
            double edgeDensity,
            int localThickness,
            double radialDistance,
            int dishBottom,
            double islandMaskRaw,
            int islandSurface) {

        // Fracture shafts provide direct column-access from sky plateau down to sea level.
        if (isFractureColumn(x, z) && y >= SEA_WATER_LEVEL && y <= PLATEAU_TERRAIN_MAX) {
            return BlockType.AIR;
        }

        if (y < dishBottom) {
            boolean stemSolid = isStructureSolid(
                    x,
                    y,
                    z,
                    seafloorHeight,
                    localBaseY,
                    edgeDensity,
                    localThickness,
                    radialDistance,
                    dishBottom);
            if (stemSolid) {
                return getPillarSurfaceBlock(x, y, z, seafloorHeight, localBaseY);
            }

            // Stalactites can hang in the region between pillar top and slab underside.
            if (y >= localBaseY && y < dishBottom) {
                BlockType stal = getStalactiteBlock(
                        x,
                        y,
                        z,
                        radialDistance,
                        localBaseY,
                        edgeDensity,
                        dishBottom);
                if (stal != BlockType.AIR) {
                    return stal;
                }
            }

            // Legacy stalactite check just below pillar top to preserve existing behavior.
            if (y >= localBaseY - STALACTITE_MAX_LENGTH && y < localBaseY) {
                BlockType stal = getStalactiteBlock(
                        x,
                        y,
                        z,
                        radialDistance,
                        localBaseY,
                        edgeDensity,
                        dishBottom);
                if (stal != BlockType.AIR) {
                    return stal;
                }
            }

            if (y <= SEA_WATER_LEVEL) {
                return getBaseSeaBlock(x, y, z);
            }
            return BlockType.AIR;
        }

        if (y <= PLATEAU_SLAB_TOP) {
            boolean plateauSolid = isPlateauSolid(
                    x,
                    y,
                    z,
                    radialDistance,
                    edgeDensity,
                    localThickness,
                    dishBottom);
            if (plateauSolid) {
                return getPlateauSlabBlock(
                        x,
                        y,
                        z,
                        radialDistance,
                        edgeDensity,
                        localThickness,
                        dishBottom);
            }

            // Minimize underside / stalactites to avoid double-layer between pillar and plateau.
            if (y < dishBottom && y >= dishBottom - 2) {
                return BlockType.AIR;
            }

            return BlockType.AIR;
        }

        if (y > PLATEAU_SLAB_TOP && y <= PLATEAU_TERRAIN_MAX) {
            if (edgeDensity > PLATEAU_EDGE_THRESHOLD && radialDistance < PLATEAU_DISK_RADIUS) {
                int surfaceTop = getPlateauSurfaceTop(x, z, edgeDensity);
                if (y <= surfaceTop) {
                    return getPlateauSurfaceBlock(x, y, z, surfaceTop);
                }
            }
            return BlockType.AIR;
        }

        return BlockType.AIR;
    }

    /**
     * Unified solid test for stem + slab.
     */
    private boolean isStructureSolid(
            int x,
            int y,
            int z,
            int seafloorHeight,
            int localBaseY,
            double edgeDensity,
            int localThickness,
            double radialDistance,
            int dishBottom) {
        if (isFractureColumn(x, z)) {
            return false;
        }

        // Above slab bottom is handled by plateau block logic.
        if (y >= dishBottom) {
            return isPlateauSolid(x, y, z, radialDistance, edgeDensity, localThickness, dishBottom);
        }

        // Crown zone between pillar top and slab underside: force solid to ensure connection.
        if (y >= localBaseY && y < dishBottom) {
            return true;
        }

        // Allow pillar root to extend below seafloor into the sediment.
        if (y < seafloorHeight - PILLAR_SEAFLOOR_PENETRATION) {
            return false;
        }

        // No forced full-band fill; preserve natural holes and sea ties.
        // Existing pillar-handling logic will determine local connectivity.

        double dwx = noise2(
            (x + PILLAR_WARP_SEED_A) * PILLAR_DOMAIN_WARP_FREQ,
            (z + PILLAR_WARP_SEED_A) * PILLAR_DOMAIN_WARP_FREQ) * PILLAR_DOMAIN_WARP_STRENGTH;
        double dwz = noise2(
            (x + PILLAR_WARP_SEED_B) * PILLAR_DOMAIN_WARP_FREQ,
            (z + PILLAR_WARP_SEED_B) * PILLAR_DOMAIN_WARP_FREQ) * PILLAR_DOMAIN_WARP_STRENGTH;
        double wx = x + dwx;
        double wz = z + dwz;
        double dist = Math.sqrt(wx * wx + wz * wz);

        double angle = Math.atan2(wz, wx);
        double radialWarp = 0.0;
        double freq = PILLAR_WARP_BASE_FREQ;
        double amp = PILLAR_WARP_MAX_DISPLACEMENT;
        for (int oct = 0; oct < PILLAR_WARP_OCTAVES; oct++) {
            double seed = (PILLAR_WARP_SEED_C + oct * PILLAR_WARP_OCTAVE_SEED_STEP)
                    * PILLAR_WARP_OCTAVE_SEED_SCALE;
            radialWarp += noise2(
                    Math.cos(angle) * freq * dist + seed,
                    Math.sin(angle) * freq * dist + seed) * amp;
            freq *= PILLAR_WARP_LACUNARITY;
            amp *= PILLAR_WARP_PERSISTENCE;
        }

        double pillarRadius = getPillarRadius(x, z, y, seafloorHeight, localBaseY)
                * Math.max(0.1, 1.0 + radialWarp);
        double pillarRatio = dist / Math.max(1.0, pillarRadius);

        // Guarantee some narrow pillar support near the connection zone.
        if (y >= localBaseY - 12 && y <= localBaseY) {
            double nearTopFalloff = clamp01((double) (y - (localBaseY - 12)) / 12.0);
            double maxRatio = 0.35 + 0.42 * nearTopFalloff;
            return pillarRatio <= maxRatio;
        }

        // Make middle pillar zone slimmer below the connection zone.
        if (y >= localBaseY - 40 && y < localBaseY - 12) {
            double midT = clamp01((double) (y - (localBaseY - 40)) / 28.0);
            double maxRatio = 0.2 + midT * 0.25;
            if (pillarRatio > maxRatio) {
                return false;
            }
        }

        double stemT = clamp01((double) (y - seafloorHeight)
                / Math.max(1.0, localBaseY - seafloorHeight));

        double plateauMembership = clamp01((edgeDensity - PLATEAU_EDGE_THRESHOLD)
                / Math.max(BASE_SEA_EPSILON, 1.0 - PLATEAU_EDGE_THRESHOLD));
        double pillarMembership = clamp01(1.0 - smoothstep(
                PILLAR_EDGE_INNER_RATIO,
                PILLAR_EDGE_OUTER_RATIO,
                pillarRatio));

        double blendT = smoothstep(STEM_BLEND_START_FRACTION, STEM_BLEND_END_FRACTION, stemT);
        double membership = lerp(pillarMembership, plateauMembership, blendT);

        double surfaceNoise =
            remap(noise3(x * 0.08 + 77.31, y * 0.06, z * 0.08 + 13.71)) * 0.45
            + remap(noise3(x * 0.18 + 31.17, y * 0.14 + 9.33, z * 0.18 + 55.91)) * 0.30
            + remap(noise3(x * 0.40 + 19.73, y * 0.33 + 4.17, z * 0.40 + 8.63)) * 0.15
            + remap(noise3(x * 0.009 + 5.11, y * 0.007, z * 0.009 + 2.37)) * 0.10;
        double threshold = lerp(STEM_THRESHOLD_LOW, STEM_THRESHOLD_HIGH, stemT);

        int rootBottom = seafloorHeight - PILLAR_SEAFLOOR_PENETRATION;
        int rootTop = seafloorHeight + PILLAR_SEAFLOOR_BLEND_HEIGHT;
        if (y < rootTop) {
            // Force stable central core on roots to avoid narrow, fast-tapering columns.
            double coreRadius = Math.max(1.0, getPillarRadius(x, z, y, seafloorHeight, localBaseY) * 0.6);
            if (dist < coreRadius) {
                return true;
            }

            double rootT = clamp01((double) (y - rootBottom) / Math.max(1, rootTop - rootBottom));
            double dissolveThreshold = PILLAR_SEAFLOOR_DENSITY_THRESHOLD * (1.0 - rootT);
            double densityAtDist = Math.max(0.0, 1.0 - (dist / Math.max(1.0, pillarRadius)));
            return densityAtDist > dissolveThreshold;
        }

        return membership > (threshold * surfaceNoise);
    }

    private boolean isStructureSolidAt(int x, int y, int z) {
        int seafloorHeight = getSeafloorHeight(x, z);
        int localBaseY = getPlateauBaseY(x, z);
        double edgeDensity = getPlateauEdgeDensity(x, z);
        int localThickness = getLocalPlateauThickness(x, z, edgeDensity);
        double radialDistance = Math.sqrt((double) x * x + (double) z * z);
        int dishBottom = getPlateauSlabBottomHeight(x, z, radialDistance);
        return isStructureSolid(
                x,
                y,
                z,
                seafloorHeight,
                localBaseY,
                edgeDensity,
                localThickness,
                radialDistance,
                dishBottom);
    }
    
    // ============================================================================
    // ZONE: BASE SEA
    // ============================================================================
    
    private BlockType getBaseSeaBlock(int x, int y, int z) {
        boolean isSolid = isBaseSeaSolid(x, y, z);
        
        if (!isSolid) {
            // Non-solid voxel at or below sea level is water
            if (y <= SEA_WATER_LEVEL) {
                return BlockType.WATER;
            }
            return BlockType.AIR;
        }
        
        // Solid block — determine specific type
        return getBaseSurfaceBlock(x, y, z);
    }
    
    private boolean isBaseSeaSolid(int x, int y, int z) {
        // Layer 1 — unconditional seafloor
        int seafloorHeight = getSeafloorHeight(x, z);
        if (y <= seafloorHeight) return true;

        // Layer 2 — 3D island density field
        double density = getIslandDensity(x, y, z, seafloorHeight);
        return density > ISLAND_DENSITY_THRESHOLD;
    }

    /**
     * Returns a density value for the island dome at world position (x, y, z).
     * Positive = solid (inside dome), negative = air/water (outside dome).
     */
    private double getIslandDensity(int x, int y, int z, int seafloorHeight) {
        // Use raw pre-smooth mask underwater; smoothstep output is too binary and causes vertical walls.
        double rawMask = getIslandMaskRaw(x, z);
        if (rawMask < ISLAND_MASK_THRESHOLD * ISLAND_EARLY_EXIT_FRACTION) {
            return -1.0;
        }

        // Above sea level: no base ocean islands in the sky region.
        if (y >= SEA_WATER_LEVEL) {
            return -1.0;
        }

        // Below sea level: smooth underwater spread anchored at sea level.
        int domeTop = SEA_WATER_LEVEL;
        if (y > domeTop) {
            return -1.0;
        }

        double smoothedSeafloor = getSmoothedSeafloorHeight(x, z);
        double waterColumnHeight = Math.max(1.0, domeTop - smoothedSeafloor);
        double t = (y - smoothedSeafloor) / waterColumnHeight;
        t = clamp01(t);

        // Smoothstep blend (no power-curve equation): threshold tight near sea level,
        // progressively looser toward seafloor for gradual outward spread.
        double blend = smoothstep(0.0, 1.0, t);
        double bottomThreshold = ISLAND_MASK_THRESHOLD - ISLAND_UNDERWATER_SPREAD;
        double thresholdAtY = lerp(bottomThreshold, ISLAND_MASK_THRESHOLD, blend);

        // Extra near-seafloor widening so island sides blend more naturally into the seabed.
        double seafloorBlendT = clamp01((y - smoothedSeafloor) / (double) Math.max(1, ISLAND_SEAFLOOR_BLEND_HEIGHT));
        double seafloorBlend = 1.0 - seafloorBlendT;
        thresholdAtY -= (ISLAND_MASK_THRESHOLD - bottomThreshold) * ISLAND_SEAFLOOR_BLEND_STRENGTH * seafloorBlend;

        return rawMask - thresholdAtY;
    }

    /**
     * Returns the absolute world Y of the island surface at (x, z).
     * Uses layered noise plus edge damping to produce uneven island tops.
     */
    public int getIslandSurfaceTop(int x, int z, double maskField) {
        double maskExcess = Math.max(0.0, maskField - ISLAND_MASK_THRESHOLD);
        double maskSpan = Math.max(BASE_SEA_EPSILON, 1.0 - ISLAND_MASK_THRESHOLD);
        double maskStrength = Math.min(1.0, maskExcess / maskSpan);

        double edgeDistance = 1.0 - maskStrength;
        double polynomialProfile = Math.max(0.0, Math.min(1.0,
            1.0 - Math.pow(edgeDistance, ISLAND_PROFILE_POWER)));
        double profiledStrength = maskStrength * (1.0 - ISLAND_PROFILE_BLEND)
            + polynomialProfile * ISLAND_PROFILE_BLEND;

        double baseHeight = ISLAND_TOP_MIN + profiledStrength * (ISLAND_TOP_MAX - ISLAND_TOP_MIN);

        // Multi-size layered fields with non-harmonic scales/offsets to avoid visible repeated patterning.
        double warpX = multiScaleNoise2(x, z, ISLAND_SURFACE_SEED_OFFSET * 0.77) * ISLAND_SURFACE_WARP_STRENGTH;
        double warpZ = multiScaleNoise2(z, x, ISLAND_SURFACE_SEED_OFFSET * 1.31) * ISLAND_SURFACE_WARP_STRENGTH;

        double sx = x + warpX;
        double sz = z + warpZ;

        double baseLayer = fractal2(
            sx * 0.0011 + ISLAND_SURFACE_SEED_OFFSET * 0.73,
            sz * 0.0013 - ISLAND_SURFACE_SEED_OFFSET * 0.41,
            6,
            0.56,
            2.12);
        double detailLayer = multiScaleNoise2(sx * 2.37, sz * 2.37, ISLAND_SURFACE_DETAIL_SEED_OFFSET);
        double ridgedLayer = 1.0 - Math.abs(multiScaleNoise2(
            sx * 3.91,
            sz * 3.91,
            ISLAND_SURFACE_DETAIL_SEED_OFFSET * 1.83));
        ridgedLayer = ridgedLayer * 2.0 - 1.0;

        double surfaceNoise = baseLayer * 0.62 + detailLayer * 0.28 + ridgedLayer * 0.10;

        double dampingFactor = lerp(1.0 - ISLAND_SURFACE_EDGE_DAMPING, 1.0, maskStrength);
        double scaledNoise = surfaceNoise * (ISLAND_SURFACE_AMPLITUDE * 0.5) * dampingFactor;

        // Lightweight erosion pass: smooth high local roughness by pulling peaks down.
        double coarseN1 = noise2(
            (x + ISLAND_SURFACE_SEED_OFFSET + ISLAND_EROSION_NEIGHBOR_RADIUS) * ISLAND_SURFACE_FREQ_COARSE,
            (z + ISLAND_SURFACE_SEED_OFFSET) * ISLAND_SURFACE_FREQ_COARSE);
        double coarseN2 = noise2(
            (x + ISLAND_SURFACE_SEED_OFFSET - ISLAND_EROSION_NEIGHBOR_RADIUS) * ISLAND_SURFACE_FREQ_COARSE,
            (z + ISLAND_SURFACE_SEED_OFFSET) * ISLAND_SURFACE_FREQ_COARSE);
        double coarseN3 = noise2(
            (x + ISLAND_SURFACE_SEED_OFFSET) * ISLAND_SURFACE_FREQ_COARSE,
            (z + ISLAND_SURFACE_SEED_OFFSET + ISLAND_EROSION_NEIGHBOR_RADIUS) * ISLAND_SURFACE_FREQ_COARSE);
        double coarseN4 = noise2(
            (x + ISLAND_SURFACE_SEED_OFFSET) * ISLAND_SURFACE_FREQ_COARSE,
            (z + ISLAND_SURFACE_SEED_OFFSET - ISLAND_EROSION_NEIGHBOR_RADIUS) * ISLAND_SURFACE_FREQ_COARSE);
        double coarseNeighborAvg = (coarseN1 + coarseN2 + coarseN3 + coarseN4) * 0.25;
        double localRoughness = Math.abs(baseLayer - coarseNeighborAvg);
        double erosionAmount = localRoughness * ISLAND_EROSION_STRENGTH * maskStrength;

        // Ravine carving with ridged noise to introduce natural channels.
        double ravineNoise = noise2(
            (x + ISLAND_SURFACE_SEED_OFFSET * 1.91) * ISLAND_RAVINE_FREQ,
            (z + ISLAND_SURFACE_SEED_OFFSET * 1.91) * ISLAND_RAVINE_FREQ);
        double ridged = 1.0 - Math.abs(ravineNoise);
        double ravineMask = smoothstep(ISLAND_RAVINE_THRESHOLD, 1.0, ridged);
        double ravineCarve = ravineMask * ISLAND_RAVINE_DEPTH * maskStrength;

        double surfaceY = baseHeight + scaledNoise - erosionAmount - ravineCarve;
        surfaceY = Math.max(SEA_WATER_LEVEL + ISLAND_SURFACE_MIN_ABOVE_SEA,
                Math.min(ISLAND_MAX_SURFACE_Y, surfaceY));
        return (int) Math.round(surfaceY);
    }

    public int getSeafloorHeight(int x, int z) {
        // Multi-size seabed field: broad smooth relief with less obvious repetitive perlin structure.
        double low = remap(multiScaleNoise2(x, z, SEAFLOOR_LAYER_SEED));
        double mid = remap(multiScaleNoise2(x * 1.91, z * 1.91, SEAFLOOR_LAYER_SEED * 1.63));
        double fine = remap(multiScaleNoise2(x * 3.47, z * 3.47, SEAFLOOR_LAYER_SEED * 2.29));
        double patchField = remap(noise2(x * SEAFLOOR_PATCH_FREQ, z * SEAFLOOR_PATCH_FREQ));
        double patchMask = smoothstep(SEAFLOOR_PATCH_THRESHOLD, 1.0, patchField);
        double patchRidge = remap(noise2(x * SEAFLOOR_PATCH_RIDGE_FREQ, z * SEAFLOOR_PATCH_RIDGE_FREQ));

        double combined = low * SEAFLOOR_WEIGHT_LOW
            + mid * SEAFLOOR_WEIGHT_MID
            + fine * SEAFLOOR_WEIGHT_FINE;
        combined += patchMask * (patchRidge - 0.5) * SEAFLOOR_PATCH_STRENGTH;
        combined = Math.max(0.0, Math.min(1.0, combined));
        return SEAFLOOR_BASE_Y + (int) Math.round(combined * SEAFLOOR_RELIEF);
    }

    private double getSmoothedSeafloorHeight(int x, int z) {
        int center = getSeafloorHeight(x, z);
        int east = getSeafloorHeight(x + 1, z);
        int west = getSeafloorHeight(x - 1, z);
        int south = getSeafloorHeight(x, z + 1);
        int north = getSeafloorHeight(x, z - 1);
        return (center + east + west + south + north) / 5.0;
    }

    private double getIslandMask(int x, int z) {
        double combined = getIslandMaskRaw(x, z);
        return smoothstep(
            ISLAND_MASK_THRESHOLD - ISLAND_MASK_EDGE_WIDTH,
            ISLAND_MASK_THRESHOLD + ISLAND_MASK_EDGE_WIDTH,
            combined);
    }

    public double getIslandMaskRaw(int x, int z) {
        // Cluster field: broad regions that can host island chains.
        double cluster = remap(multiScaleNoise2(x * 0.81, z * 0.81, ISLAND_MASK_LAYER_SEED));
        double clusterSupport = Math.max(0.0, Math.min(1.0, cluster * (1.0 + ISLAND_CLUSTER_STRENGTH)));

        // Shape field: individual island outlines.
        double shapeCoarse = remap(multiScaleNoise2(
            x * ISLAND_SHAPE_FREQ_COARSE,
            z * ISLAND_SHAPE_FREQ_COARSE,
            ISLAND_MASK_LAYER_SEED * 1.17));
        double shapeMedium = remap(multiScaleNoise2(
            x * ISLAND_SHAPE_FREQ_MEDIUM,
            z * ISLAND_SHAPE_FREQ_MEDIUM,
            ISLAND_MASK_LAYER_SEED * 1.91));
        double shapeFine = remap(multiScaleNoise2(
            x * ISLAND_SHAPE_FREQ_FINE,
            z * ISLAND_SHAPE_FREQ_FINE,
            ISLAND_MASK_LAYER_SEED * 2.57));
        double shape = shapeCoarse * ISLAND_SHAPE_WEIGHT_COARSE
            + shapeMedium * ISLAND_SHAPE_WEIGHT_MEDIUM
            + shapeFine * ISLAND_SHAPE_WEIGHT_FINE;

        // Combine cluster and shape, then sharpen edge with narrow smoothstep.
        double microMaskNoise = noise2(
            x * ISLAND_MASK_MICRO_FREQ + ISLAND_HILL_SEED_OFFSET * 0.37,
            z * ISLAND_MASK_MICRO_FREQ + ISLAND_HILL_SEED_OFFSET * 0.37);
        double combined = shape * clusterSupport + microMaskNoise * ISLAND_MASK_MICRO_STRENGTH + ISLAND_LAND_COVERAGE_BIAS;
        combined = Math.max(0.0, Math.min(1.0, combined));
        return combined;
    }

    /**
     * Multi-size 2D layered noise with non-harmonic scales and decorrelated offsets.
     * Output approximately in [-1, 1].
     */
    private double multiScaleNoise2(double x, double z, double offset) {
        double n1 = noise2((x + offset * 0.73) * 0.0011, (z - offset * 0.41) * 0.0013);
        double n2 = noise2((x - offset * 1.11) * 0.0037, (z + offset * 0.67) * 0.0031);
        double n3 = noise2((x + offset * 0.29) * 0.0099, (z + offset * 1.37) * 0.0107);
        double n4 = noise2((x - offset * 1.79) * 0.0263, (z - offset * 0.93) * 0.0239);
        double n5 = noise2((x + offset * 2.07) * 0.0671, (z + offset * 1.53) * 0.0713);
        double ridged = 1.0 - Math.abs(noise2((x - offset * 2.61) * 0.0187, (z + offset * 2.11) * 0.0199));
        ridged = ridged * 2.0 - 1.0;
        return n1 * 0.33 + n2 * 0.24 + n3 * 0.18 + n4 * 0.12 + n5 * 0.08 + ridged * 0.05;
    }

    private int getIslandTop(int x, int z, double islandMask) {
        // Base height derived from mask strength above threshold.
        double maskExcess = Math.max(0.0, islandMask - ISLAND_MASK_THRESHOLD);
        double maskSpan = Math.max(BASE_SEA_EPSILON, 1.0 - ISLAND_MASK_THRESHOLD);
        double maskStrength = Math.max(0.0, Math.min(1.0, (maskExcess / maskSpan) * ISLAND_TOP_MASK_HEIGHT_MULTIPLIER));

        // Polynomial profile based on normalized distance from center:
        // y = 1 - (x/3)^n with x in [0,3] => y = 1 - d^n where d in [0,1]
        double edgeDistance = 1.0 - maskStrength;
        double polynomialProfile = 1.0 - Math.pow(edgeDistance, ISLAND_PROFILE_POWER);
        polynomialProfile = Math.max(0.0, Math.min(1.0, polynomialProfile));

        // Blend polynomial shape with original Perlin-derived mask so islands still follow noise.
        double profiledStrength = maskStrength * (1.0 - ISLAND_PROFILE_BLEND)
            + polynomialProfile * ISLAND_PROFILE_BLEND;

        double baseHeight = ISLAND_TOP_MIN + profiledStrength * (ISLAND_TOP_MAX - ISLAND_TOP_MIN);

        // Hill variation decorrelated from shape field with seed offset.
        double hillCoarse = remap(noise2(
                x * ISLAND_HILL_FREQ_COARSE + ISLAND_HILL_SEED_OFFSET,
                z * ISLAND_HILL_FREQ_COARSE + ISLAND_HILL_SEED_OFFSET));
        double hillFine = remap(noise2(
                x * ISLAND_HILL_FREQ_FINE + ISLAND_HILL_SEED_OFFSET,
                z * ISLAND_HILL_FREQ_FINE + ISLAND_HILL_SEED_OFFSET));
        double hillCombined = hillCoarse * ISLAND_HILL_BLEND_COARSE
            + hillFine * ISLAND_HILL_BLEND_FINE;

        // Small high-frequency detail to avoid overly smooth silhouettes.
        double micro = noise2(
                x * ISLAND_MICRO_FREQ + ISLAND_HILL_SEED_OFFSET * 1.73,
                z * ISLAND_MICRO_FREQ + ISLAND_HILL_SEED_OFFSET * 1.73) * ISLAND_MICRO_AMPLITUDE;

        double topHeight = baseHeight + hillCombined * ISLAND_HILL_AMPLITUDE + micro;
        topHeight = Math.max(ISLAND_TOP_MIN, Math.min(ISLAND_MAX_SURFACE_Y, topHeight));
        return (int) Math.round(topHeight);
    }

    private BlockType getBaseSurfaceBlock(int x, int y, int z) {
        boolean aboveSolid = isBaseSeaSolid(x, y + 1, z);
        boolean topExposed = !aboveSolid;
        
        if (topExposed) {
            // Surface blocks by depth below sea level.
            if (y >= SEA_WATER_LEVEL - SAND_DEPTH) {
                return BlockType.SAND;
            }
            if (y >= SEA_WATER_LEVEL - GRAVEL_DEPTH) {
                return BlockType.GRAVEL;
            }
            if (y >= SEA_WATER_LEVEL - STONE_DEPTH) {
                return BlockType.STONE;
            }
            return BlockType.DARK_STONE;
        }
        
        // One block below a sand surface should also be sand.
        boolean aboveAboveSolid = isBaseSeaSolid(x, y + 2, z);
        boolean yPlusOneSandSurface = !aboveAboveSolid && (y + 1) >= SEA_WATER_LEVEL - SAND_DEPTH;
        if (yPlusOneSandSurface) {
            return BlockType.SAND;
        }
        
        // Interior blocks
        if (y >= SEA_WATER_LEVEL - STONE_DEPTH) {
            return BlockType.STONE;
        }
        return BlockType.DARK_STONE;
    }

    private boolean isCaveVoid(int x, int y, int z) {
        if (y < CAVE_ZONE_BOTTOM || y > CAVE_ZONE_TOP) {
            return false;
        }

        double depthT = (double) (y - CAVE_ZONE_BOTTOM) / (CAVE_ZONE_TOP - CAVE_ZONE_BOTTOM);
        depthT = clamp01(depthT);

        double noiseVal = remap(noise3(
                x * CAVE_NOISE_FREQ,
                y * CAVE_VERTICAL_FREQ,
                z * CAVE_NOISE_FREQ));

        double threshold = CAVE_THRESHOLD + (1.0 - depthT) * CAVE_DEPTH_FALLOFF;
        threshold = clamp01(threshold);

        return noiseVal > threshold;
    }

    private boolean isFractureColumn(int x, int z) {
        double fractureVal = remap(noise2(x * PLATEAU_FRACTURE_FREQ, z * PLATEAU_FRACTURE_FREQ));
        return fractureVal > PLATEAU_FRACTURE_THRESHOLD;
    }
    
    // ============================================================================
    // ZONE: PILLARS
    // ============================================================================
    
    private BlockType getPillarZoneBlock(int x, int y, int z) {
        int seafloorHeight = getSeafloorHeight(x, z);
        int pillarTopY = getPlateauBaseY(x, z);
        return getPillarZoneBlock(x, y, z, seafloorHeight, pillarTopY);
    }

    private BlockType getPillarZoneBlock(int x, int y, int z, int seafloorHeight, int pillarTopY) {
        if (isInsidePillar(x, y, z, seafloorHeight, pillarTopY)) {
            return getPillarSurfaceBlock(x, y, z, seafloorHeight, pillarTopY);
        }
        return BlockType.AIR;
    }

    /**
     * Returns the effective pillar radius at position (x, z, y).
     */
    private double getPillarRadius(int x, int z, int y, int seafloorHeight, int pillarTopY) {
        int pillarBaseY = seafloorHeight;
        double pillarHeight = Math.max(1.0, pillarTopY - pillarBaseY);
        double t = clamp01((double) (y - pillarBaseY) / pillarHeight);

        // Soften the waist to avoid a sharp cone narrowing around y≈128.
        double waistT = 0.58; // slightly higher than default to keep more width lower.
        double baseRadius = PILLAR_BASE_RADIUS;
        double waistRadius = PILLAR_TOP_RADIUS * 0.90; // less aggressive thinning.
        double topRadius = PILLAR_TOP_RADIUS;

        double nomRadius;
        if (t < waistT) {
            double localT = smoothstep(0.0, 1.0, t / Math.max(BASE_SEA_EPSILON, waistT));
            nomRadius = lerp(baseRadius, waistRadius, localT);
        } else {
            double localT = smoothstep(0.0, 1.0,
                (t - waistT) / Math.max(BASE_SEA_EPSILON, 1.0 - waistT));
            nomRadius = lerp(waistRadius, topRadius, localT);
        }

        int rootZoneBottom = seafloorHeight - PILLAR_SEAFLOOR_PENETRATION;
        int rootZoneTop = seafloorHeight + PILLAR_SEAFLOOR_BLEND_HEIGHT;
        if (y < rootZoneTop) {
            // t=0 at deepest root, t=1 at top of blend zone.
            double rootT = clamp01((double) (y - rootZoneBottom) / Math.max(1, rootZoneTop - rootZoneBottom));
            double rootCurve = 1.0 - (rootT * rootT * rootT); // cubic ease-in
            nomRadius *= (1.0 + rootCurve * (PILLAR_SEAFLOOR_FLARE_STRENGTH * 0.62));
        }

        // Avoid extreme skinny taper in pillars (prevents small cone artifacts).
        double pillarMinRadius = PILLAR_TOP_RADIUS * 0.90;
        nomRadius = Math.max(nomRadius, pillarMinRadius);

        // Introduce irregular radius via additional high-frequency noise
        double ragged = remap(noise3(
                x * PILLAR_RAGGEDNESS_FREQ,
                y * PILLAR_RAGGEDNESS_FREQ,
                z * PILLAR_RAGGEDNESS_FREQ));
        double raggedScale = (ragged - 0.5) * 2.0 * PILLAR_RAGGEDNESS_STRENGTH;
        nomRadius *= (1.0 + raggedScale);

        // Occasional local thinning or bulges for unnatural pillar profiles
        if ((remap(noise2(x * 0.015, z * 0.015)) > 0.92) && y % 8 < 4) {
            nomRadius *= 0.85;
        }

        return nomRadius;
    }
    
    private boolean isInsidePillar(int x, int y, int z) {
        int seafloorHeight = getSeafloorHeight(x, z);
        int pillarTopY = getPlateauBaseY(x, z);
        return isInsidePillar(x, y, z, seafloorHeight, pillarTopY);
    }

    private boolean isInsidePillar(int x, int y, int z, int seafloorHeight, int pillarTopY) {
        if (y < 0) {
            return false;
        }
        if (y < seafloorHeight - PILLAR_SEAFLOOR_PENETRATION || y > pillarTopY) {
            return false;
        }

        double dwx = noise2(
            (x + PILLAR_WARP_SEED_A) * PILLAR_DOMAIN_WARP_FREQ,
            (z + PILLAR_WARP_SEED_A) * PILLAR_DOMAIN_WARP_FREQ) * PILLAR_DOMAIN_WARP_STRENGTH;
        double dwz = noise2(
            (x + PILLAR_WARP_SEED_B) * PILLAR_DOMAIN_WARP_FREQ,
            (z + PILLAR_WARP_SEED_B) * PILLAR_DOMAIN_WARP_FREQ) * PILLAR_DOMAIN_WARP_STRENGTH;

        double wx = x + dwx;
        double wz = z + dwz;
        double dist = Math.sqrt(wx * wx + wz * wz);

        double angle = Math.atan2(wz, wx);
        double radialWarp = 0.0;
        double freq = PILLAR_WARP_BASE_FREQ;
        double amp = PILLAR_WARP_MAX_DISPLACEMENT;
        for (int oct = 0; oct < PILLAR_WARP_OCTAVES; oct++) {
            double seed = (PILLAR_WARP_SEED_C + oct * PILLAR_WARP_OCTAVE_SEED_STEP)
                    * PILLAR_WARP_OCTAVE_SEED_SCALE;
            double nx = Math.cos(angle) * freq * dist + seed;
            double nz = Math.sin(angle) * freq * dist + seed;
            radialWarp += noise2(nx, nz) * amp;
            freq *= PILLAR_WARP_LACUNARITY;
            amp *= PILLAR_WARP_PERSISTENCE;
        }

        // Prevent extreme warp-induced pinching (small cone artifacts) by clamping minimum warp.
        double warpScale = Math.max(0.55, 1.0 + radialWarp);
        double effectiveRadius = getPillarRadius(x, z, y, seafloorHeight, pillarTopY) * warpScale;

        int rootBottom = seafloorHeight - PILLAR_SEAFLOOR_PENETRATION;
        int rootTop = seafloorHeight + PILLAR_SEAFLOOR_BLEND_HEIGHT;
        if (y < rootTop) {
            // Keep the pillar core solid near transitioning around seafloor blend to avoid high sharp taper.
            double effectiveRadiusAtTop = Math.max(1.0, getPillarRadius(x, z, rootTop, seafloorHeight, pillarTopY) * 0.65);
            if (dist < effectiveRadiusAtTop) {
                return true;
            }

            double rootT = clamp01((double) (y - rootBottom) / Math.max(1, rootTop - rootBottom));
            double densityAtDist = Math.max(0.0, 1.0 - (dist / Math.max(1.0, effectiveRadius)));
            double dissolveThreshold = PILLAR_SEAFLOOR_DENSITY_THRESHOLD * (1.0 - rootT);
            return densityAtDist > dissolveThreshold;
        }

        double ratio = dist / Math.max(1.0, effectiveRadius);
        if (ratio <= PILLAR_EDGE_INNER_RATIO) {
            return true;
        }
        if (ratio >= PILLAR_EDGE_OUTER_RATIO) {
            return false;
        }

        double edgeMask = 1.0 - smoothstep(PILLAR_EDGE_INNER_RATIO, PILLAR_EDGE_OUTER_RATIO, ratio);
        double surfaceNoise = remap(noise2(
                x * PILLAR_EDGE_SURFACE_NOISE_FREQ + PILLAR_EDGE_SURFACE_NOISE_SEED_X,
                z * PILLAR_EDGE_SURFACE_NOISE_FREQ + PILLAR_EDGE_SURFACE_NOISE_SEED_Z));
        return edgeMask > surfaceNoise;
    }
    
    private BlockType getPillarSurfaceBlock(int x, int y, int z) {
        int seafloorHeight = getSeafloorHeight(x, z);
        int pillarTopY = getPlateauBaseY(x, z);
        return getPillarSurfaceBlock(x, y, z, seafloorHeight, pillarTopY);
    }

    private BlockType getPillarSurfaceBlock(int x, int y, int z, int seafloorHeight, int pillarTopY) {
        boolean topExposed = !isStructureSolidAt(x, y + 1, z);

        if (y < seafloorHeight) {
            // Deep pillar root below the sea bottom: buried dark rock.
            return BlockType.DARK_STONE;
        }

        if (y < seafloorHeight + PILLAR_SEAFLOOR_BLEND_HEIGHT) {
            // Blend zone near seafloor: top shows basalt, interior dark rock.
            return topExposed ? BlockType.BASALT : BlockType.DARK_STONE;
        }

        if (y < PLATEAU_SLAB_BOTTOM - 16) {
            // Lower pillar portions are deep support, not surface land.
            return BlockType.DARK_STONE;
        }

        if (topExposed && y >= PLATEAU_SLAB_BOTTOM - 16) {
            return BlockType.MOSSY_STONE;
        }

        boolean lateralExposed = !isStructureSolidAt(x + 1, y, z)
            || !isStructureSolidAt(x - 1, y, z)
            || !isStructureSolidAt(x, y, z + 1)
            || !isStructureSolidAt(x, y, z - 1);
        if (lateralExposed) {
            double ratio = Math.sqrt((double) x * x + (double) z * z)
                    / Math.max(1.0, getPillarRadius(x, z, y, seafloorHeight, pillarTopY));
            if (ratio > PILLAR_CRACKED_RATIO) {
                return BlockType.CRACKED_STONE;
            }
            return BlockType.CLIFF_STONE;
        }

        double depthNoise = remap(noise2(x * 0.07 + 300.0, z * 0.07 + 300.0));
        int sampleDist = 2 + (int) (depthNoise * 4);
        boolean deepInterior = isStructureSolidAt(x + sampleDist, y, z)
            && isStructureSolidAt(x - sampleDist, y, z)
            && isStructureSolidAt(x, y, z + sampleDist)
            && isStructureSolidAt(x, y, z - sampleDist);
        return deepInterior ? BlockType.DARK_STONE : BlockType.STONE;
    }
    
    // ============================================================================
    // ZONE: STALACTITES
    // ============================================================================
    
    private BlockType getStalactiteBlock(int x, int y, int z) {
        double radialDistance = Math.sqrt((double) x * x + (double) z * z);
        double edgeDensity = getPlateauEdgeDensity(x, z);
        int localBaseY = getPlateauBaseY(x, z);
        int slabBottom = getPlateauSlabBottomHeight(x, z, radialDistance);
        return getStalactiteBlock(x, y, z, radialDistance, localBaseY, edgeDensity, slabBottom);
    }

    private BlockType getStalactiteBlock(
            int x,
            int y,
            int z,
            double radialDistance,
            int localPlateauBase,
            double edgeDensity,
            int slabBottom) {
        if (radialDistance > PLATEAU_DISK_RADIUS + 60.0) {
            return BlockType.AIR;
        }

        if (edgeDensity <= PLATEAU_EDGE_THRESHOLD) {
            return BlockType.AIR;
        }

        if (y >= slabBottom) {
            return BlockType.AIR;
        }

        int dishDepth = slabBottom - localPlateauBase;
        if (dishDepth < STALACTITE_MIN_DISH_DEPTH) {
            return BlockType.AIR;
        }

        int depth = slabBottom - y;
        if (depth > STALACTITE_MAX_LENGTH) {
            return BlockType.AIR;
        }

        double wx = noise2(x * STALACTITE_WARP_FREQ + 11.37, z * STALACTITE_WARP_FREQ + 5.71)
                * STALACTITE_WARP_STRENGTH;
        double wz = noise2(x * STALACTITE_WARP_FREQ + 23.13, z * STALACTITE_WARP_FREQ + 17.93)
                * STALACTITE_WARP_STRENGTH;
        double sx = x + wx;
        double sz = z + wz;

        double cluster = remap(noise2(sx * STALACTITE_CLUSTER_FREQ, sz * STALACTITE_CLUSTER_FREQ));

        double edgeStrength = clamp01((edgeDensity - PLATEAU_EDGE_THRESHOLD)
                / Math.max(BASE_SEA_EPSILON, 1.0 - PLATEAU_EDGE_THRESHOLD));
        double rimBoost = (1.0 - smoothstep(0.0, STALACTITE_RIM_BOOST_FADE, edgeStrength))
                * STALACTITE_RIM_BOOST;
        cluster = Math.min(1.0, cluster + rimBoost);

        if (cluster < STALACTITE_CLUSTER_THRESH) {
            return BlockType.AIR;
        }

        double spike = remap(noise2(sx * STALACTITE_SPIKE_FREQ, sz * STALACTITE_SPIKE_FREQ));
        double ridged = remap(1.0 - Math.abs(noise2(
                sx * STALACTITE_RIDGE_FREQ + 41.11,
                sz * STALACTITE_RIDGE_FREQ + 13.37)));
        double micro = remap(noise2(
                sx * STALACTITE_SPIKE_FREQ * 3.13 + 7.77,
                sz * STALACTITE_SPIKE_FREQ * 3.13 + 19.31));
        double heightField = spike * 0.50 + ridged * 0.35 + micro * 0.15;

        double clusterStrength = (cluster - STALACTITE_CLUSTER_THRESH)
                / Math.max(BASE_SEA_EPSILON, 1.0 - STALACTITE_CLUSTER_THRESH);
        double dishScale = Math.min(1.0, dishDepth / (double) PLATEAU_BOTTOM_MAX_DEPTH);
        int maxLen = Math.max(1, (int) Math.round(
                heightField * clusterStrength * dishScale * STALACTITE_MAX_LENGTH));

        if (depth > maxLen) {
            return BlockType.AIR;
        }

        double tipFrac = (double) depth / (double) Math.max(1, maxLen);
        double requiredStrength = lerp(STALACTITE_CLUSTER_THRESH, 1.0, tipFrac * tipFrac);
        if (cluster < requiredStrength) {
            return BlockType.AIR;
        }

        if (tipFrac < STALACTITE_MOSSY_FRACTION) {
            return BlockType.MOSSY_STONE;
        }
        if (tipFrac < STALACTITE_CALCITE_FRACTION) {
            return BlockType.CALCITE;
        }
        return BlockType.DRIPSTONE;
    }
    
    // ============================================================================
    // ZONE: PLATEAU SLAB AND TERRAIN
    // ============================================================================
    
    private BlockType getPlateauBlock(int x, int y, int z) {
        int localBaseY = getPlateauBaseY(x, z);
        if (y < localBaseY || y > PLATEAU_SLAB_TOP) {
            return BlockType.AIR;
        }

        if (!isPlateauSolid(x, y, z)) {
            return BlockType.AIR;
        }

        return getPlateauSlabBlock(x, y, z);
    }
    
    /**
     * Calculates the bottom surface height of the plateau slab at the given position.
     * Creates a dish-shaped underside with noise-driven ridges and valleys for texture.
     * High at center, tapering down toward edges, with columnar/ridged detail.
     */
    public int getPlateauSlabBottomHeight(int x, int z, double radialDistance) {
        int localBaseY = getPlateauBaseY(x, z);
        double normalizedRadius = clamp01(radialDistance / PLATEAU_BOTTOM_CURVE_RADIUS);
        double parabola = Math.pow(normalizedRadius, PLATEAU_BOTTOM_CURVE_POWER);
        double baseDepth = parabola * PLATEAU_BOTTOM_MAX_DEPTH;

        double ridgeA = remap(noise2(x * 0.027 + 17.31, z * 0.027 + 5.13));
        double ridgeB = remap(noise2(x * 0.058 + 3.71, z * 0.058 + 29.37));
        double valleyA = remap(1.0 - Math.abs(noise2(x * 0.0093 + 43.11, z * 0.0093 + 11.71)));
        double valleyB = remap(1.0 - Math.abs(noise2(x * 0.0178 + 7.97, z * 0.0178 + 63.31)));
        double micro = remap(noise2(x * 0.119 + 2.37, z * 0.119 + 51.13));

        double roughness = ridgeA * 0.38 + ridgeB * 0.24 + valleyA * 0.22 + valleyB * 0.12 + micro * 0.04;
        double edgeInfluence = clamp01(radialDistance / (PLATEAU_BOTTOM_CURVE_RADIUS * 0.45));
        double scaledRoughness = roughness * 16.0 * edgeInfluence;

        double finalDepth = baseDepth + scaledRoughness;

        // Compress bottom drops to avoid strong ceiling cliff between pillar and slab.
        finalDepth *= 0.28; // create generally thinner dish wings while keeping shape

        int rawBottom = localBaseY + (int) Math.round(finalDepth);
        return Math.min(rawBottom, PLATEAU_SLAB_TOP - 4);
    }

    /**
     * Returns slab thickness at (x, z) scaled by local plateau footprint density.
     */
    public int getLocalPlateauThickness(int x, int z, double edgeDensity) {
        double interiorT = (edgeDensity - PLATEAU_EDGE_THRESHOLD)
            / Math.max(BASE_SEA_EPSILON, 1.0 - PLATEAU_EDGE_THRESHOLD);
        interiorT = clamp01(interiorT);

        double wedgeT = smoothstep(0.0, 0.6, interiorT);

        int minThickness = Math.max(4,
            (int) Math.round(PLATEAU_SLAB_THICKNESS * PLATEAU_THICKNESS_RIM_MIN_FRACTION));
        double baseThickness = lerp(minThickness, PLATEAU_SLAB_THICKNESS, wedgeT);

        double noiseRaw = noise2(
            (x + PLATEAU_THICKNESS_NOISE_SEED) * PLATEAU_THICKNESS_NOISE_FREQ,
            (z + PLATEAU_THICKNESS_NOISE_SEED) * PLATEAU_THICKNESS_NOISE_FREQ);
        double noiseScale = interiorT * 0.4;
        double thickness = baseThickness
            + noiseRaw * PLATEAU_THICKNESS_NOISE_AMPLITUDE * noiseScale;

        return Math.max(minThickness,
            Math.min(PLATEAU_SLAB_THICKNESS, (int) Math.round(thickness)));
    }

    private boolean isPlateauSolid(int x, int y, int z) {
        double radialDistance = Math.sqrt((double) x * x + (double) z * z);
        double edgeDensity = getPlateauEdgeDensity(x, z);
        int localThickness = getLocalPlateauThickness(x, z, edgeDensity);
        int slabBottomAtXZ = getPlateauSlabBottomHeight(x, z, radialDistance);
        return isPlateauSolid(x, y, z, radialDistance, edgeDensity, localThickness, slabBottomAtXZ);
    }

    private boolean isPlateauSolid(
            int x,
            int y,
            int z,
            double radialDistance,
            double edgeDensity,
            int localThickness,
            int slabBottomAtXZ) {
        if (isFractureColumn(x, z)) {
            return false;
        }

        if (y < slabBottomAtXZ) {
            return false;
        }
        if (y > PLATEAU_SLAB_TOP) {
            return false;
        }

        if (radialDistance > PLATEAU_DISK_RADIUS) {
            return false;
        }

        if (edgeDensity <= PLATEAU_EDGE_THRESHOLD) {
            return false;
        }

        int localTop = slabBottomAtXZ + localThickness - 1;
        if (y > localTop) {
            return false;
        }

        double slabFraction = (double) (y - slabBottomAtXZ) / (double) Math.max(1, localThickness);
        double fadeFactor;
        if (slabFraction >= PLATEAU_VERTICAL_FADE_START) {
            double t = (slabFraction - PLATEAU_VERTICAL_FADE_START)
                    / Math.max(BASE_SEA_EPSILON, 1.0 - PLATEAU_VERTICAL_FADE_START);
            fadeFactor = lerp(1.0, PLATEAU_TOP_FADE_MIN, clamp01(t));
        } else {
            fadeFactor = 1.0;
        }

        return (edgeDensity * fadeFactor) > PLATEAU_EDGE_THRESHOLD;
    }
    
    private BlockType getPlateauSlabBlock(int x, int y, int z) {
        double radialDistance = Math.sqrt((double) x * x + (double) z * z);
        double edgeDensity = getPlateauEdgeDensity(x, z);
        int localThickness = getLocalPlateauThickness(x, z, edgeDensity);
        int slabBottomAtXZ = getPlateauSlabBottomHeight(x, z, radialDistance);
        return getPlateauSlabBlock(x, y, z, radialDistance, edgeDensity, localThickness, slabBottomAtXZ);
    }

    private BlockType getPlateauSlabBlock(
            int x,
            int y,
            int z,
            double radialDistance,
            double edgeDensity,
            int localThickness,
            int slabBottomAtXZ) {
        boolean topExposed = !isPlateauSolid(x, y + 1, z, radialDistance, edgeDensity, localThickness, slabBottomAtXZ);

        // Only allow green/surface materials near the very top of the plateau slab (for y > slab top - 2)
        // to avoid a second “skyland” visible plateau at mid-slab (y ~= 140).
        if (topExposed && y >= PLATEAU_SLAB_TOP - 2) {
            if (y >= SNOWLINE && y >= PLATEAU_SLAB_TOP - 2) {
                return BlockType.SNOW;
            }
            if (isInsideHotSpring(x, z)) {
                return BlockType.MOSSY_STONE;
            }
            if (isInsideLakeBasin(x, z)) {
                return BlockType.SAND;
            }
            return BlockType.GRASS;
        }

        // Else keep slab interior/exposed undersides as stone variants.

        boolean oneBelowTop = isPlateauSolid(x, y + 1, z, radialDistance, edgeDensity, localThickness, slabBottomAtXZ)
                && !isPlateauSolid(x, y + 2, z, radialDistance, edgeDensity, localThickness, slabBottomAtXZ);
        if (oneBelowTop) {
            return BlockType.DIRT;
        }

        int slabDepthFromBottom = y - slabBottomAtXZ;
        boolean bottomExposed = !isPlateauSolid(x, y - 1, z, radialDistance, edgeDensity, localThickness, slabBottomAtXZ);
        if (bottomExposed && slabDepthFromBottom <= PLATEAU_UNDERSIDE_DEPTH) {
            return BlockType.MOSSY_STONE;
        }

        boolean lateralExposed = !isPlateauSolid(x + 1, y, z)
                || !isPlateauSolid(x - 1, y, z)
                || !isPlateauSolid(x, y, z + 1)
                || !isPlateauSolid(x, y, z - 1);
        if (lateralExposed && slabDepthFromBottom <= PLATEAU_CLIFF_FACE_DEPTH) {
            double thinThreshold = PLATEAU_SLAB_THICKNESS
                    * (PLATEAU_THICKNESS_RIM_MIN_FRACTION + PLATEAU_CRACKED_STONE_THIN_OFFSET);
            if (localThickness <= thinThreshold) {
                return BlockType.CRACKED_STONE;
            }
            return BlockType.CLIFF_STONE;
        }

        // Keep the top plateau supporters as dark stone rather than fancy blocks.
        if (y < PLATEAU_SLAB_TOP - 4) {
            return BlockType.DARK_STONE;
        }

        double rockVariation = remap(noise3(x * 0.04, y * 0.06, z * 0.04));
        if (rockVariation > 0.72) {
            return BlockType.DARK_STONE;
        }
        if (rockVariation < 0.18) {
            return BlockType.CALCITE;
        }
        return BlockType.STONE;
    }

    private int getPlateauSurfaceTop(int x, int z, double edgeDensity) {
        // Disabled—no above-slab terrain.
        return PLATEAU_SLAB_TOP;
    }

    private BlockType getPlateauSurfaceBlock(int x, int y, int z, int surfaceTop) {
        // Disabled—no visible surface above slab.
        return BlockType.AIR;
    }

    public double getPlateauEdgeDensity(int x, int z) {
        // Primary shape field from low/mid/high frequencies.
        double coarse = noise2(x * PLATEAU_SHAPE_FREQ_COARSE, z * PLATEAU_SHAPE_FREQ_COARSE);
        double medium = noise2(x * PLATEAU_SHAPE_FREQ_MEDIUM, z * PLATEAU_SHAPE_FREQ_MEDIUM);
        double fine = noise2(x * PLATEAU_SHAPE_FREQ_FINE, z * PLATEAU_SHAPE_FREQ_FINE);
        double shape = remap(coarse * PLATEAU_SHAPE_AMP_COARSE
            + medium * PLATEAU_SHAPE_AMP_MEDIUM
            + fine * PLATEAU_SHAPE_AMP_FINE);

        double cliffNoise = noise2(
            x * PLATEAU_CLIFF_FREQ + PLATEAU_CLIFF_SEED_OFFSET,
            z * PLATEAU_CLIFF_FREQ + PLATEAU_CLIFF_SEED_OFFSET);
        double cliffVal = remap(cliffNoise);

        double softEdge = smoothstep(
            PLATEAU_EDGE_THRESHOLD - PLATEAU_EDGE_SOFT_BAND,
            PLATEAU_EDGE_THRESHOLD + PLATEAU_EDGE_SOFT_BAND,
            shape);
        double hardEdge = smoothstep(
            PLATEAU_EDGE_THRESHOLD - PLATEAU_EDGE_HARD_BAND,
            PLATEAU_EDGE_THRESHOLD + PLATEAU_EDGE_HARD_BAND,
            shape);
        double sharpness = clamp01((cliffVal - PLATEAU_CLIFF_SHARPNESS_LOW)
            / (PLATEAU_CLIFF_SHARPNESS_HIGH - PLATEAU_CLIFF_SHARPNESS_LOW));
        return lerp(softEdge, hardEdge, sharpness);
    }
    
    // ============================================================================
    // WATER FEATURES: LAKES
    // ============================================================================
    
    private boolean isInsideLakeBasin(int x, int z) {
        double lakeNoise = remap(noise2(x * 0.008 + 500.0, z * 0.008 + 500.0));
        lakeNoise += remap(noise2(x * 0.018 + 500.0, z * 0.018 + 500.0)) * 0.4;
        lakeNoise /= 1.4;

        double elevation = remap(noise2(x * 0.012, z * 0.012)) * 30.0;

        return lakeNoise > LAKE_THRESHOLD
                && elevation < LAKE_ELEVATION_CUTOFF
                && !isInsideHotSpring(x, z);
    }
    
    // ============================================================================
    // WATER FEATURES: HOT SPRINGS
    // ============================================================================
    
    private boolean isInsideHotSpring(int x, int z) {
        int cellX = (int) Math.floor((double) x / HOT_SPRING_GRID_SPACING);
        int cellZ = (int) Math.floor((double) z / HOT_SPRING_GRID_SPACING);

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;

                long hash = ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L) ^ worldSeed;
                long rand = hash;

                rand = rand * 6364136223846793005L + 1442695040888963407L;
                double spawnRoll = ((rand >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
                if (spawnRoll > HOT_SPRING_SPAWN_PROBABILITY) {
                    continue;
                }

                rand = rand * 6364136223846793005L + 1442695040888963407L;
                double jx01 = ((rand >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
                rand = rand * 6364136223846793005L + 1442695040888963407L;
                double jz01 = ((rand >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
                rand = rand * 6364136223846793005L + 1442695040888963407L;
                double r01 = ((rand >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);

                double centerX = cx * HOT_SPRING_GRID_SPACING + jx01 * HOT_SPRING_GRID_SPACING;
                double centerZ = cz * HOT_SPRING_GRID_SPACING + jz01 * HOT_SPRING_GRID_SPACING;
                double springRadius = lerp(HOT_SPRING_MIN_ELEVATION, HOT_SPRING_MAX_ELEVATION, r01);

                double centerElevation = remap(noise2(centerX * 0.012, centerZ * 0.012)) * 30.0;
                if (centerElevation < HOT_SPRING_MIN_ELEVATION || centerElevation > HOT_SPRING_MAX_ELEVATION) {
                    continue;
                }

                double dxWorld = x - centerX;
                double dzWorld = z - centerZ;
                if ((dxWorld * dxWorld + dzWorld * dzWorld) <= (springRadius * springRadius)) {
                    return true;
                }
            }
        }

        return false;
    }
    
    // ============================================================================
    // WATER FEATURES: RIVERS
    // ============================================================================
    
    private boolean isInsideRiverChannel(int x, int z) {
        double wx = noise2(x * 0.007 + 800.0, z * 0.007 + 800.0) * 28.0;
        double wz = noise2(x * 0.007 + 900.0, z * 0.007 + 900.0) * 28.0;
        double sx = x + wx;
        double sz = z + wz;

        double ridge = 1.0 - Math.abs(noise2(sx * 0.005, sz * 0.005));
        double sharpRidge = smoothstep(RIVER_NOISE_TARGET - 0.08, RIVER_NOISE_TARGET + 0.01, ridge);

        if (isInsideHotSpring(x, z) || isInsideLakeBasin(x, z)) {
            return false;
        }

        return sharpRidge > 0.5;
    }
    
    private int getTerrainHeightForRiver(int x, int z) {
        // TODO: Return terrain height for river carving depth calculation
        return PLATEAU_SLAB_TOP;
    }
    
    // ============================================================================
    // NOISE FUNCTIONS
    // ============================================================================
    
    /**
     * Samples 2D gradient noise at the given coordinates.
     */
    private double noise2(double x, double z) {
        return noise.noise2(x, z);
    }
    
    /**
     * Samples 3D gradient noise at the given coordinates.
     */
    private double noise3(double x, double y, double z) {
        return noise.noise3(x, y, z);
    }
    
    /**
     * Samples fractal 2D noise (multiple octaves).
     */
    private double fractal2(double x, double z, int octaves, double persistence, double frequency) {
        return noise.fractal2(x, z, octaves, persistence, frequency);
    }
    
    /**
     * Samples fractal 3D noise (multiple octaves).
     */
    private double fractal3(double x, double y, double z, int octaves, double persistence, double frequency) {
        return noise.fractal3(x, y, z, octaves, persistence, frequency);
    }
    
    /**
     * Helper to remap a noise value from its natural range [-1, 1] to [0, 1].
     */
    private double remap(double value) {
        return (value + 1.0) * 0.5;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double smoothstep(double edge0, double edge1, double x) {
        if (edge1 <= edge0) {
            return x < edge0 ? 0.0 : 1.0;
        }
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

}
