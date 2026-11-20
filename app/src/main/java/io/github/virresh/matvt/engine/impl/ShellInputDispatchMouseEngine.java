package io.github.virresh.matvt.engine.impl;

import android.graphics.PointF;
import android.view.KeyEvent;

import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.view.MouseCursorView;
import io.github.virresh.matvt.view.OverlayView;

/**
 * Root-shell input engine. It shares BaseEngine's persistent authenticated
 * root session for both taps and swipes; it does not depend on local ADB/TCP.
 */
public class ShellInputDispatchMouseEngine extends BaseEngine {

    public ShellInputDispatchMouseEngine(AppPreferences appPreferences, OverlayView ov,
                                         MouseCursorView mCursorView) {
        super(appPreferences, ov, mCursorView);
    }

    @Override
    protected int scroll(KeyEvent ke) {
        if (mService == null || !scrollCodeMap.containsKey(ke.getKeyCode())) return 0;

        PointF start = mPointerControl.getPointerScreenLocation();
        int direction = scrollCodeMap.get(ke.getKeyCode());

        // DPAD_DOWN maps to an upward swipe (the usual "next video" gesture).
        // DPAD_UP maps to a downward swipe.
        int distance = 220 + Math.min(momentumStack, 180);
        PointF end = new PointF(
                start.x + distance * PointerControl.dirX[direction],
                start.y + distance * PointerControl.dirY[direction]);

        int duration = Math.max(80, Math.min(420, 360 - scrollSpeed * 35));
        dispatchRootShellSwipe(start, end, duration);
        momentumStack = Math.min(momentumStack + 12, 180);
        return 1;
    }

    @Override
    protected int click(KeyEvent ke) {
        if (mService != null) dispatchConfiguredClick();
        return 0;
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
