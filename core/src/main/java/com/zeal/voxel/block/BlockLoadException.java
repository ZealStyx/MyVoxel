package com.zeal.voxel.block;

public class BlockLoadException extends RuntimeException {
    public BlockLoadException(String message) {
        super(message);
    }

    public BlockLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
