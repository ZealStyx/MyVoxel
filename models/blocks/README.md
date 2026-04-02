# Blockbench Export Guide

This folder is for source model files edited in Blockbench.
Runtime JSON model files consumed by the game must be copied to `assets/models/blocks/`.

## Recommended workflow

1. Open your Blockbench project from this folder.
2. Keep each block model aligned to a 16x16x16 grid.
3. Use axis-aligned cubes for stable culling and meshing.
4. Assign texture variables that map to runtime atlas paths.
5. Export as Bedrock-style geometry JSON compatible with this runtime loader.
6. Copy exported JSON to `assets/models/blocks/`.
7. Update block definition `model` path in `assets/blocks/*.json`.

## Runtime expectations

- `textureSize` must be `[w, h]`.
- `textures` must map variable names to texture atlas paths.
- `elements` must include `from`, `to`, and `faces`.
- Every face needs `uv` and `texture` (for example `#side`).

## Texture path conventions

- Use forward slashes.
- Prefer `textures/blocks/<name>` without `.png`.
- The engine accepts with or without `.png`, but consistency is recommended.
