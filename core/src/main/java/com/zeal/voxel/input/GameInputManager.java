package com.zeal.voxel.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.IntSet;
import java.util.BitSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Single owner of {@link Gdx#input} processor registration.
 *
 * <p><b>Rules:</b>
 * <ol>
 *   <li>{@code Gdx.input.setInputProcessor()} is called ONLY inside {@link #commit()}.
 *       Do not call it anywhere else in the codebase.</li>
 *   <li>{@link #commit()} must be the <em>last</em> call in {@code GameScreen.show()},
 *       after all Bullet initialization is complete.</li>
 *   <li>{@link #assertActive()} is called as the <em>first</em> line of
 *       {@code GameScreen.render()} every frame. It catches and corrects any
 *       external replacement of the processor (e.g. from gdx-bullet internals).</li>
 * </ol>
 *
 * <p><b>Input architecture for this project — POLLING ONLY:</b><br>
 * All gameplay input uses {@code Gdx.input.isKeyPressed()}, {@code isKeyJustPressed()},
 * {@code isButtonJustPressed()}, {@code getDeltaX()}, {@code getDeltaY()}.
 * No InputProcessor subclasses exist for gameplay. This is intentional and must not
 * be changed. Polling cannot be stolen by Bullet or any library;
 * event-driven processors can. See the polling reference in the design docs.
 */
public class GameInputManager implements InputProcessor {

    private static final String TAG = "GameInputManager";

    private final InputMultiplexer multiplexer;
    private final List<InputProcessor> processors = new ArrayList<>();
    
    // Buffers for "just pressed" state
    private final IntSet keysJustPressed = new IntSet();
    private final IntSet buttonsJustPressed = new IntSet();
    
    // Tracking for currently held keys
    private final BitSet keysPressed = new BitSet(256);

    public GameInputManager() {
        multiplexer = new InputMultiplexer();
        // We add ourselves as the LAST processor in the chain so we only 
        // catch events that weren't consumed by UI/Dialogs.
        multiplexer.addProcessor(this);
    }

    /**
     * Registers an InputProcessor with the multiplexer.
     * Use this only for UI/text-field processors that are temporarily active
     * (e.g. a naming dialog). All gameplay input must use polling.
     */
    public void addProcessor(InputProcessor p) {
        processors.add(p);
        // Ensure we stay at the end of the chain by removing/re-adding ourselves
        multiplexer.removeProcessor(this);
        multiplexer.addProcessor(p);
        multiplexer.addProcessor(this);
    }

    /**
     * Removes a previously registered processor.
     */
    public void removeProcessor(InputProcessor p) {
        processors.remove(p);
        multiplexer.removeProcessor(p);
    }

    /**
     * Clears the "just pressed" buffers. MUST be called at the very end 
     * of GameScreen.render() every frame.
     */
    public void update() {
        keysJustPressed.clear();
        buttonsJustPressed.clear();
    }

    public boolean isKeyJustPressed(int keycode) {
        return keysJustPressed.contains(keycode);
    }

    public boolean isButtonJustPressed(int button) {
        return buttonsJustPressed.contains(button);
    }

    /**
     * Returns true if the key is currently held down.
     */
    public boolean isKeyPressed(int keycode) {
        if (keycode < 0 || keycode >= 256) return false;
        return keysPressed.get(keycode);
    }

    /**
     * Activates this manager as the sole owner of the LibGDX input processor.
     *
     * <p><b>Call this exactly once, as the last line of {@code GameScreen.show()}</b>,
     * after all game systems and Bullet physics are fully initialized. Nothing
     * that touches Bullet may run after this call — if something does, move it before this.</p>
     */
    public void commit() {
        Gdx.input.setInputProcessor(multiplexer);
        Gdx.input.setCursorCatched(true);
        Gdx.app.log(TAG, "Input processor committed. Cursor catched.");
    }

    /**
     * Guards against external processor replacement. Call this as the
     * <b>first line of {@code GameScreen.render()}</b> every frame.
     *
     * <p>This is a safety net to detect regressions during development,
     * not a substitute for calling {@link #commit()} last in show(). If it
     * fires, investigate what ran after commit() and move it before.</p>
     */
    public void assertActive() {
        if (Gdx.input.getInputProcessor() != multiplexer) {
            Gdx.app.log(TAG,
                "WARNING: InputProcessor was replaced externally (now=" +
                Gdx.input.getInputProcessor() + "). Re-asserting multiplexer.");
            Gdx.input.setInputProcessor(multiplexer);
        }
    }

    /**
     * Returns the underlying multiplexer for inspection/debugging.
     */
    public InputMultiplexer getMultiplexer() {
        return multiplexer;
    }

    // --- InputProcessor implementation ---

    @Override
    public boolean keyDown(int keycode) {
        keysJustPressed.add(keycode);
        if (keycode >= 0 && keycode < 256) {
            keysPressed.set(keycode);
        }
        return false; // Don't consume so others can still see it if needed
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode >= 0 && keycode < 256) {
            keysPressed.clear(keycode);
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) { return false; }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        buttonsJustPressed.add(button);
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }

    @Override
    public boolean mouseMoved(int screenX, int screenY) { return false; }

    @Override
    public boolean scrolled(float amountX, float amountY) { return false; }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
}
