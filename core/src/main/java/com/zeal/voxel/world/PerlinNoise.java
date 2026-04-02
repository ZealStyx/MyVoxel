package com.zeal.voxel.world;

/**
 * A simple 2D and 3D Perlin-like noise implementation suitable for terrain generation.
 * Based on the Improved Perlin Noise algorithm.
 */
public class PerlinNoise {
    private static final int PERMUTATION_SIZE = 256;
    private int[] permutation;
    
    public PerlinNoise(long seed) {
        permutation = new int[PERMUTATION_SIZE * 2];
        initPermutationTable(seed);
    }
    
    private void initPermutationTable(long seed) {
        // Initialize with a deterministic pseudo-random permutation based on seed
        java.util.Random rng = new java.util.Random(seed);
        int[] perm = new int[PERMUTATION_SIZE];
        
        for (int i = 0; i < PERMUTATION_SIZE; i++) {
            perm[i] = i;
        }
        
        // Fisher-Yates shuffle
        for (int i = PERMUTATION_SIZE - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int temp = perm[i];
            perm[i] = perm[j];
            perm[j] = temp;
        }
        
        // Duplicate the permutation table
        for (int i = 0; i < PERMUTATION_SIZE * 2; i++) {
            permutation[i] = perm[i % PERMUTATION_SIZE];
        }
    }
    
    /**
     * 2D Perlin noise in the range [-1, 1]
     */
    public double noise2(double x, double z) {
        int xi = (int) Math.floor(x) & 255;
        int zi = (int) Math.floor(z) & 255;
        
        double xf = x - Math.floor(x);
        double zf = z - Math.floor(z);
        
        // Fade curves
        double u = fade(xf);
        double v = fade(zf);
        
        // Hash coordinates of the 4 corners
        int n00 = permutation[permutation[xi] + zi];
        int n10 = permutation[permutation[xi + 1] + zi];
        int n01 = permutation[permutation[xi] + zi + 1];
        int n11 = permutation[permutation[xi + 1] + zi + 1];
        
        // Compute gradients
        double g00 = gradient2D(n00, xf, zf);
        double g10 = gradient2D(n10, xf - 1, zf);
        double g01 = gradient2D(n01, xf, zf - 1);
        double g11 = gradient2D(n11, xf - 1, zf - 1);
        
        // Interpolate
        double x0 = lerp(g00, g10, u);
        double x1 = lerp(g01, g11, u);
        return lerp(x0, x1, v);
    }
    
    /**
     * 3D Perlin noise in the range [-1, 1]
     */
    public double noise3(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;
        
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);
        
        // Fade curves
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);
        
        // Hash coordinates
        int n000 = permutation[permutation[permutation[xi] + yi] + zi];
        int n100 = permutation[permutation[permutation[xi + 1] + yi] + zi];
        int n010 = permutation[permutation[permutation[xi] + yi + 1] + zi];
        int n110 = permutation[permutation[permutation[xi + 1] + yi + 1] + zi];
        int n001 = permutation[permutation[permutation[xi] + yi] + zi + 1];
        int n101 = permutation[permutation[permutation[xi + 1] + yi] + zi + 1];
        int n011 = permutation[permutation[permutation[xi] + yi + 1] + zi + 1];
        int n111 = permutation[permutation[permutation[xi + 1] + yi + 1] + zi + 1];
        
        // Gradients
        double g000 = gradient3D(n000, xf, yf, zf);
        double g100 = gradient3D(n100, xf - 1, yf, zf);
        double g010 = gradient3D(n010, xf, yf - 1, zf);
        double g110 = gradient3D(n110, xf - 1, yf - 1, zf);
        double g001 = gradient3D(n001, xf, yf, zf - 1);
        double g101 = gradient3D(n101, xf - 1, yf, zf - 1);
        double g011 = gradient3D(n011, xf, yf - 1, zf - 1);
        double g111 = gradient3D(n111, xf - 1, yf - 1, zf - 1);
        
        // Interpolate
        double x0y0 = lerp(g000, g100, u);
        double x1y0 = lerp(g010, g110, u);
        double x0y1 = lerp(g001, g101, u);
        double x1y1 = lerp(g011, g111, u);
        
        double y0 = lerp(x0y0, x1y0, v);
        double y1 = lerp(x0y1, x1y1, v);
        
        return lerp(y0, y1, w);
    }
    
    private double fade(double t) {
        // 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
    
    private double gradient2D(int hash, double x, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : z;
        double v = h < 8 ? z : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    private double gradient3D(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    /**
     * Stack multiple octaves of noise with decreasing amplitude for fractal-like terrain.
     * 
     * @param x X coordinate
     * @param z Z coordinate
     * @param octaves Number of octaves to combine
     * @param persistence Amplitude multiplier per octave (typically 0.5)
     * @param frequency Frequency multiplier per octave (typically 2.0)
     * @return Combined noise value in approximately [-1, 1]
     */
    public double fractal2(double x, double z, int octaves, double persistence, double frequency) {
        double result = 0;
        double amplitude = 1;
        double maxAmplitude = 0;
        double freq = 1;
        
        for (int i = 0; i < octaves; i++) {
            result += noise2(x * freq, z * freq) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            freq *= frequency;
        }
        
        return result / maxAmplitude;
    }
    
    /**
     * Stack multiple octaves of 3D noise with decreasing amplitude.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param octaves Number of octaves to combine
     * @param persistence Amplitude multiplier per octave (typically 0.5)
     * @param frequency Frequency multiplier per octave (typically 2.0)
     * @return Combined noise value in approximately [-1, 1]
     */
    public double fractal3(double x, double y, double z, int octaves, double persistence, double frequency) {
        double result = 0;
        double amplitude = 1;
        double maxAmplitude = 0;
        double freq = 1;
        
        for (int i = 0; i < octaves; i++) {
            result += noise3(x * freq, y * freq, z * freq) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            freq *= frequency;
        }
        
        return result / maxAmplitude;
    }
}
