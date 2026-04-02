package com.zeal.voxel.player;

public enum LatchState {
    IDLE,          // tool in hand, nothing targeted or held
    TARGETING,     // raycast hitting something, ready to act
    HOLDING,       // actively holding a PhysicsBody
    PLACING        // holding + placement mode selected, previewing
}
