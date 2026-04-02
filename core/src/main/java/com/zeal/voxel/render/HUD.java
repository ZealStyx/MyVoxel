package com.zeal.voxel.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.zeal.voxel.physics.PhysicsBody;
import com.zeal.voxel.player.Player;
import com.zeal.voxel.player.PlayerConstants;
import com.zeal.voxel.player.SelectionMode;
import com.zeal.voxel.player.VoxelSelection;
import com.zeal.voxel.util.Constants;

public class HUD {
    private final Player player;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final ShapeRenderer shapeRenderer;

    public HUD(Player player) {
        this.player = player;
        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();
    }

    public void render() {
        int width  = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();

        // Crosshair
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        float cx = width / 2f;
        float cy = height / 2f;
        float s = Constants.CROSSHAIR_SIZE;
        float t = Constants.CROSSHAIR_THICKNESS;
        shapeRenderer.rect(cx - s / 2, cy - t / 2, s, t);
        shapeRenderer.rect(cx - t / 2, cy - s / 2, t, s);
        shapeRenderer.end();

        batch.begin();
        font.setColor(Color.WHITE);

        int topY = height - 20;

        // ── Active mode indicator (top-left) ──
        com.zeal.voxel.player.Player.InteractionState istate = player.getInteractionState();
        String mainMode = istate == com.zeal.voxel.player.Player.InteractionState.BUILDING ? "[ BUILDING MODE ]" : "[ SELECTION MODE ]";
        font.setColor(istate == com.zeal.voxel.player.Player.InteractionState.BUILDING ? Color.ORANGE : Color.GOLD);
        font.draw(batch, mainMode + " (Tab toggle)", 20, topY);
        font.setColor(Color.WHITE);
        topY -= 20;

        SelectionMode mode = player.getSelectionMode();
        if (istate == com.zeal.voxel.player.Player.InteractionState.SELECTING) {
            String modeLabel = mode == SelectionMode.AABB ? "  > BOX SELECT (K toggle)" : "  > FILL SELECT (K toggle)";
            font.draw(batch, modeLabel, 20, topY);
            topY -= 20;
        }

        // ── Latch Mode & Interaction Hints (bottom-left) ──
        int hintY = 120;
        com.zeal.voxel.player.Latch latch = player.getLatch();
        com.zeal.voxel.player.LatchState lstate = latch.getState();
        com.zeal.voxel.player.PlacementMode pMode = latch.getPlacementMode();
        
        if (lstate == com.zeal.voxel.player.LatchState.HOLDING || lstate == com.zeal.voxel.player.LatchState.PLACING) {
            String modeName = (lstate == com.zeal.voxel.player.LatchState.PLACING) ? 
                (pMode == com.zeal.voxel.player.PlacementMode.ASSEMBLE ? "[ ASSEMBLE MODE ]" : "[ DROP MODE ]") : "[ LATCHED ]";
            
            Color modeColor = (pMode == com.zeal.voxel.player.PlacementMode.ASSEMBLE) ? Color.ORANGE : Color.CYAN;
            font.setColor(modeColor);
            font.draw(batch, modeName + "  [R] rotate  scroll adjust reach", 20, hintY);
            
            String subHint = "[V] placement mode  right-click execute  [Q/Esc] cancel";
            if (pMode == com.zeal.voxel.player.PlacementMode.ASSEMBLE) {
                subHint = "red ghost = blocked (will burst)  " + subHint;
            }
            font.draw(batch, subHint, 20, hintY - 20);
            font.setColor(Color.WHITE);
        } else if (lstate == com.zeal.voxel.player.LatchState.TARGETING && latch.getLastHit() != null) {
            boolean isBody = latch.getLastHit().body instanceof PhysicsBody;
            font.setColor(isBody ? Color.CYAN : Color.GOLD);
            font.draw(batch, isBody ? "Right-click to latch physics body" : "Right-click to assemble structure", 20, hintY);
            
            if (!isBody) {
                com.zeal.voxel.player.FloodFillSelector ffs = player.getSelectionTool().getFloodFillSelector();
                if (ffs.isCapped()) {
                    font.setColor(Color.YELLOW);
                    font.draw(batch, "Structure too large - use Box Select [TAB] first", 20, hintY - 20);
                }
            }
            font.setColor(Color.WHITE);
        } else {
            font.draw(batch, "Right-click blocks to assemble  [TAB] Select mode", 20, hintY);
        }

        // ── Player Mode & Block Selection ──
        String playerMode = player.isFlyMode() ? "[ FLYING ]" : "[ WALKING ]";
        font.setColor(player.isFlyMode() ? Color.CYAN : Color.GREEN);
        font.draw(batch, playerMode + " (V toggle)", 20, topY);
        font.setColor(Color.WHITE);
        topY -= 20;

        String blockName = com.zeal.voxel.block.BlockType.fromId(player.getSelectedBlockType()).name();
        font.draw(batch, "Material: " + blockName + " (1/2/3 select)", 20, topY);
        topY -= 20;

        // ── Selection state hints ──
        VoxelSelection sel = player.getCurrentSelection();
        if (mode == SelectionMode.AABB) {
            if (player.getSelectionTool().getAabbSelector().getCornerA() != null
                    && player.getSelectionTool().getAabbSelector().getState() == com.zeal.voxel.player.AabbSelector.State.CORNER_A) {
                font.draw(batch, "Right-click to set second corner", 20, topY);
                topY -= 20;
            }
        }

        if (!sel.isEmpty()) {
            int count = sel.getSelectedBlocks().size();
            font.draw(batch, count + " blocks selected  —  [G] detach  [Esc] clear", 20, topY);
            topY -= 20;
        }

        // ── Flood fill cap warning ──
        if (mode == SelectionMode.FLOOD_FILL && player.getSelectionTool().getFloodFillSelector().isCapped()) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "⚠ Capped at 1024 — switch to Box Select [TAB]", 20, topY);
            font.setColor(Color.WHITE);
            topY -= 20;
        }

        // ── Focused physics body info ──
        PhysicsBody body = player.getFocusedBody();
        if (body != null) {
            float speed = body.getLinearVelocity().len();
            int voxels = body.getVoxels().size();
            font.draw(batch, "Focused Body:", 20, topY);       topY -= 20;
            font.draw(batch, "Voxels: " + voxels, 20, topY);  topY -= 20;
            font.draw(batch, "Speed: " + String.format("%.2f", speed) + " m/s", 20, topY); topY -= 20;
            font.draw(batch, "[R] Reattach  [RMB] Push  [F] Activate", 20, topY);
        } else if (sel.isEmpty()) {
            font.draw(batch, "[LMB] Break  [RMB] Place  [Tab] Mode", 20, topY);
        }

        // ── Debug Overlays (top-right) ──
        com.badlogic.gdx.math.Vector3 pos = player.getCapsuleController().getPosition();
        com.badlogic.gdx.math.Vector3 vel = player.getCapsuleController().getLinearVelocity();
        float feetY = pos.y - PlayerConstants.EYE_HEIGHT;
        String debugText = String.format(
            "Pos: %.1f, %.1f, %.1f\nFeetY: %.1f\nVel: %.1f m/s",
            pos.x, pos.y, pos.z, feetY, vel.len());
        
        font.setColor(Color.LIME);
        float debugX = width - 180;
        font.draw(batch, "DEBUG HUD", debugX, height - 20);
        font.setColor(Color.WHITE);
        font.draw(batch, debugText, debugX, height - 40);
        
        renderInputOverlay(width, height);

        batch.end();
    }

    private void renderInputOverlay(int width, int height) {
        float x = width - 280;
        float y = 140;
        float size = 30;
        float gap = 5;

        font.setColor(Color.LIME);
        font.draw(batch, "INPUT OVERLAY", x, y + 25);

        // Movement cluster
        drawKey("W", Input.Keys.W, x + size + gap, y);
        drawKey("A", Input.Keys.A, x, y - size - gap);
        drawKey("S", Input.Keys.S, x + size + gap, y - size - gap);
        drawKey("D", Input.Keys.D, x + (size + gap) * 2, y - size - gap);

        // Action cluster
        drawKey("Space", Input.Keys.SPACE, x + (size + gap) * 4, y);
        drawKey("Shift", Input.Keys.SHIFT_LEFT, x + (size + gap) * 4, y - size - gap);

        // Utils
        float utilY = y - (size + gap) * 2.5f;
        drawKey("V", Input.Keys.V, x, utilY);
        drawKey("M", Input.Keys.M, x + size + gap, utilY);
        drawKey("Tab", Input.Keys.TAB, x + (size + gap) * 2.5f, utilY);
        drawKey("F", Input.Keys.F, x + (size + gap) * 4.5f, utilY);
        
        // Rotation
        float rotY = utilY - (size + gap);
        drawKey("Q", Input.Keys.Q, x, rotY);
        drawKey("R", Input.Keys.R, x + size + gap, rotY);
        drawKey("E", Input.Keys.E, x + (size + gap) * 2, rotY);

        // Arrows
        float arrX = x + (size + gap) * 4;
        drawKey("^", Input.Keys.UP, arrX + size + gap, rotY + size + gap);
        drawKey("<", Input.Keys.LEFT, arrX, rotY);
        drawKey("v", Input.Keys.DOWN, arrX + size + gap, rotY);
        drawKey(">", Input.Keys.RIGHT, arrX + (size + gap) * 2, rotY);
    }

    private void drawKey(String label, int key, float x, float y) {
        boolean pressed = Gdx.input.isKeyPressed(key);
        font.setColor(pressed ? Color.GOLD : Color.GRAY);
        font.draw(batch, "[" + label + "]", x, y);
        font.setColor(Color.WHITE);
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
        shapeRenderer.dispose();
    }
}
