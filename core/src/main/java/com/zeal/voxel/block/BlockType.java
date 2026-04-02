package com.zeal.voxel.block;

/** Enum of block IDs and properties. */
public enum BlockType {
    AIR(0, 0f),
    STONE(1, 1.0f),
    WOOD(2, 0.6f),
    IRON(3, 3.0f),
    THRUSTER(4, 1.5f),
    GYROSCOPE(5, 2.0f),
    GRASS(6, 1.0f),
    DIRT(7, 1.2f),
    WATER(16, 0f),
    HOT_SPRING_WATER(17, 0f),
    DARK_STONE(18, 1.1f),
    CLIFF_STONE(19, 1.0f),
    MOSSY_STONE(20, 1.0f),
    GRAVEL(21, 0.8f),
    SAND(22, 0.7f),
    SNOW(23, 0.3f),
    CALCITE(24, 0.95f),
    DRIPSTONE(25, 1.05f),
    BASALT(26, 1.2f),
    CRACKED_STONE(27, 0.9f);

    private final int id;
    private final float mass;

    BlockType(int id, float mass) {
        this.id = id;
        this.mass = mass;
    }

    public int getId() {
        return id;
    }

    public float getMass() {
        return mass;
    }

    public static BlockType fromId(int id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return AIR;
    }
}
