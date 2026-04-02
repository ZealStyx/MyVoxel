package com.zeal.voxel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.zeal.voxel.block.BlockBehaviour;
import com.zeal.voxel.block.BlockBehaviourRegistry;
import com.zeal.voxel.block.BlockDefinition;
import com.zeal.voxel.block.BlockRegistry;
import com.zeal.voxel.block.TextureRegionResolver;
import com.zeal.voxel.physics.BulletWorld;
import com.zeal.voxel.physics.PhysicsBodyFactory;
import com.zeal.voxel.physics.PhysicsBodyManager;
import com.zeal.voxel.physics.constraint.ConstraintFactory;
import com.zeal.voxel.physics.constraint.ConstraintManager;
import com.zeal.voxel.player.Player;
import com.zeal.voxel.render.HUD;
import com.zeal.voxel.render.PhysicsBodyRenderer;
import com.zeal.voxel.render.SelectionRenderer;
import com.zeal.voxel.render.WorldRenderer;
import com.zeal.voxel.render.culling.FrustumCuller;
import com.zeal.voxel.render.culling.OcclusionGraph;
import com.zeal.voxel.render.particle.BlockBreakEmitter;
import com.zeal.voxel.render.particle.ParticleRenderer;
import com.zeal.voxel.render.pbr.PbrMaterialTable;
import com.zeal.voxel.render.post.PostProcessor;
import com.zeal.voxel.render.shader.ShaderManager;
import com.zeal.voxel.render.shadow.CascadedShadowMap;
import com.zeal.voxel.input.GameInputManager;
import com.zeal.voxel.util.Constants;
import com.zeal.voxel.world.ColumnStreamer;
import com.zeal.voxel.world.ProceduralColumnTerrainGenerator;
import com.zeal.voxel.world.WorldGrid;
import com.zeal.voxel.world.WorldGenerator;

import java.util.HashMap;
import java.util.Map;

public class GameScreen extends ScreenAdapter {
    private static final int SPAWN_ABOVE_SURFACE = 4;

    private final PerspectiveCamera camera;
    private final WorldGrid worldGrid;
    private final BulletWorld bulletWorld;
    private final ConstraintManager constraintManager;
    private final ConstraintFactory constraintFactory;
    private final PhysicsBodyManager physicsBodyManager;
    private final PhysicsBodyFactory physicsBodyFactory;
    private final Player player;
    private final ShaderManager shaderManager;

    private final WorldRenderer worldRenderer;
    private final PhysicsBodyRenderer physicsBodyRenderer;
    private final SelectionRenderer selectionRenderer;
    private final HUD hud;
    private final ColumnStreamer columnStreamer;
    private final GameInputManager inputManager;
    private final com.zeal.voxel.render.GhostRenderer ghostRenderer;
    private final FrustumCuller frustumCuller;
    private final OcclusionGraph occlusionGraph;

    // New visual systems
    private final CascadedShadowMap csm;
    private final PbrMaterialTable pbrTable;
    private final PostProcessor postProcessor;
    private final BlockBreakEmitter blockBreakEmitter;
    private final ParticleRenderer particleRenderer;
    private FrameBuffer velocityBuffer;

    public GameScreen(BlockRegistry blockRegistry,
                      TextureRegionResolver textureRegionResolver,
                      BlockBehaviourRegistry behaviourRegistry,
                      PbrMaterialTable pbrTable) {
        camera = new PerspectiveCamera(Constants.CAMERA_FOV, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        int spawnY = WorldGenerator.seaWaterLevel()
            + WorldGenerator.plateauHeightAboveSea()
            + WorldGenerator.plateauSlabThickness()
            + SPAWN_ABOVE_SURFACE;
        camera.position.set(0, spawnY, 10);
        camera.lookAt(0, spawnY - SPAWN_ABOVE_SURFACE, 0);
        camera.near = Constants.CAMERA_NEAR;
        camera.far = Constants.CAMERA_FAR;

        worldGrid = new WorldGrid();
        bulletWorld = new BulletWorld();
        
        // Input system
        inputManager = new GameInputManager();

        // Constraints and Physics systems
        constraintManager = new ConstraintManager(bulletWorld);
        constraintFactory = new ConstraintFactory(bulletWorld);
        physicsBodyManager = new PhysicsBodyManager(bulletWorld, constraintManager);

        blockBreakEmitter = new BlockBreakEmitter();
        frustumCuller = new FrustumCuller();
        occlusionGraph = new OcclusionGraph(blockRegistry);

        Map<Integer, BlockBehaviour> behaviours = new HashMap<>();
        for (BlockDefinition def : blockRegistry.getAll()) {
            if (def.resolvedBehaviour != null) {
                behaviours.put(def.numericId, behaviourRegistry.get(def.resolvedBehaviour.type));
            }
        }

        physicsBodyFactory = new PhysicsBodyFactory(physicsBodyManager, behaviours, blockBreakEmitter);

        player = new Player(camera, worldGrid, physicsBodyFactory, physicsBodyManager,
                           bulletWorld, constraintFactory, constraintManager, inputManager);

        shaderManager = new ShaderManager();
        shaderManager.init();

        csm = new CascadedShadowMap();
        this.pbrTable = pbrTable;
        postProcessor = new PostProcessor(shaderManager);

        ghostRenderer = new com.zeal.voxel.render.GhostRenderer(shaderManager.get(com.zeal.voxel.render.shader.ShaderPrograms.GHOST));

        columnStreamer = new ColumnStreamer(new ProceduralColumnTerrainGenerator(), worldGrid, bulletWorld, occlusionGraph);
        columnStreamer.initialize();

        worldRenderer = new WorldRenderer(
            worldGrid,
            columnStreamer,
            shaderManager,
            blockRegistry,
            textureRegionResolver,
            frustumCuller,
            occlusionGraph);
        physicsBodyRenderer = new PhysicsBodyRenderer(
            physicsBodyManager,
            shaderManager,
            frustumCuller,
            blockRegistry,
            textureRegionResolver);
        selectionRenderer = new SelectionRenderer(shaderManager);
        particleRenderer = new ParticleRenderer(blockBreakEmitter, shaderManager);
        hud = new HUD(player);
        
        // Pass the ghost renderer to the player's latch tool 
        // (It was null in Player's constructor, so we set it here if needed, 
        // or better, we modify Player constructor again, or just use a setter).
        // Let's use a setter for simplicity since player is already created.
        player.getLatch().setGhostRenderer(ghostRenderer);

        // ── CRITICAL: register the capsule controller AFTER BulletWorld is fully
        // constructed (btGhostPairCallback is set). Without this call, the ghost
        // object and charController are never added to the dynamics world.
        // Calling removeAction/addAction (e.g. when pressing V for fly-mode)
        // on an unregistered controller corrupts Bullet's internal state and
        // crashes on the very next stepSimulation call.
        bulletWorld.registerCapsuleController(player.getCapsuleController());

        velocityBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
                false);
    }

    @Override
    public void show() {
        // commit() MUST be the last call in show() — after all initialization
        // including Bullet setup — so nothing can overwrite the processor afterward.
        inputManager.commit();
    }

    @Override
    public void hide() {
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void render(float delta) {
        // Guard: re-assert our input processor if anything replaced it this frame.
        // Logs a warning so the root cause can be investigated. See GameInputManager.
        inputManager.assertActive();

        if (inputManager.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.input.setCursorCatched(false);
        }

        // ── 1. UPDATE GAME LOGIC ──
        columnStreamer.update(camera.position);

        // Sync modified chunks with physics
        // TODO: Update for column-based system
        /*
        if (!worldGrid.getModifiedChunks().isEmpty()) {
            for (com.badlogic.gdx.math.GridPoint3 gp : worldGrid.getModifiedChunks()) {
                com.zeal.voxel.world.Chunk chunk = worldGrid.getChunk(gp);
                if (chunk != null) {
                    chunkStreamer.updateChunkPhysics(new com.zeal.voxel.world.ChunkPosition(gp.x, gp.z), chunk);
                }
            }
            worldGrid.clearModifiedChunks();
        }
        */

        // CORRECT ORDER — ENFORCED FOR CAPSULE CONTROLLER
        // 1. Update player input and controllers
        player.update(delta);
        physicsBodyManager.update(delta);
        
        // 2. Step physics simulation
        bulletWorld.update(delta);
        
        // 3. Read back camera positions correctly after Bullet stepped
        // ONLY sync if we aren't in fly mode, otherwise we fight with the camera's free movement.
        if (!player.isFlyMode()) {
            com.badlogic.gdx.math.Vector3 pos = player.getCapsuleController().getPosition();
            // Shield against invalid Bullet coordinates to prevent camera teleports.
            if (pos != null
                    && Float.isFinite(pos.x) && Float.isFinite(pos.y) && Float.isFinite(pos.z)
                    && Math.abs(pos.x) <= 100000f
                    && Math.abs(pos.y) <= 100000f
                    && Math.abs(pos.z) <= 100000f) {
                camera.position.set(pos);
                camera.update();
            }
        }

        blockBreakEmitter.update(delta);

        // ── 2. MAIN SCENE PASS (no shadows, no post-processing) ──
        Gdx.gl.glClearColor(0.2f, 0.4f, 0.6f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        worldRenderer.render(camera, csm, pbrTable);
        physicsBodyRenderer.render(camera, csm, pbrTable);
        particleRenderer.render(camera, csm, pbrTable);

        // ── 3. OVERLAYS (drawn directly to screen) ──
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        selectionRenderer.render(camera, player);
        ghostRenderer.render(camera);
        hud.render();
        
        // ── 4. FINISH FRAME ──
        // Clear the "just pressed" buffers so they only stay true for one frame.
        inputManager.update();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        postProcessor.resize(width, height);
        if (velocityBuffer != null)
            velocityBuffer.dispose();
        velocityBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Math.max(width, 1), Math.max(height, 1), false);
    }

    @Override
    public void dispose() {
        // OPTIMIZED: Dispose chunk streamer first so static chunk rigid bodies are released deterministically.
        columnStreamer.dispose();
        player.dispose();
        worldRenderer.dispose();
        physicsBodyRenderer.dispose();
        selectionRenderer.dispose();
        particleRenderer.dispose();
        hud.dispose();
        constraintManager.dispose();
        physicsBodyManager.dispose();
        bulletWorld.dispose();
        csm.dispose();
        occlusionGraph.dispose();
        ghostRenderer.dispose();
        postProcessor.dispose();
        shaderManager.dispose();
        if (velocityBuffer != null)
            velocityBuffer.dispose();
    }
}
