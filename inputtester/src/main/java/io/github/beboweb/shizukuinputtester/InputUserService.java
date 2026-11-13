package io.github.beboweb.shizukuinputtester;

import android.os.Process;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.util.Log;
import java.lang.reflect.Method;

public final class InputUserService extends IInputService.Stub {
    @Override
    public void destroy() {
        // No persistent resources to release in this proof-of-concept service.
    }

    @Override
    public String getIdentity() {
        return "pid=" + Process.myPid() + " uid=" + Process.myUid();
    }

    @Override
    public String injectTap(int x, int y, int source) {
        MotionEvent down = null;
        MotionEvent up = null;
        try {
            long downTime = SystemClock.uptimeMillis();
            down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, (float) x, (float) y, 0);
            down.setSource(source);
            up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, (float) x, (float) y, 0);
            up.setSource(source);

            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Object inputManager = inputManagerClass.getDeclaredMethod("getInstance").invoke(null);
            Method inject = inputManagerClass.getDeclaredMethod("injectInputEvent", android.view.InputEvent.class, int.class);
            inject.setAccessible(true);
            boolean downAccepted = (Boolean) inject.invoke(inputManager, down, 0);
            boolean upAccepted = (Boolean) inject.invoke(inputManager, up, 0);
            return "DOWN=" + downAccepted + " UP=" + upAccepted + " source=" + source;
        } catch (Throwable error) {
            Log.e("ShizukuInputTest", "inject failed", error);
            return "ERROR: " + Log.getStackTraceString(error);
        } finally {
            if (down != null) down.recycle();
            if (up != null) up.recycle();
        }
    }
}