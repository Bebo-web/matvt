package io.github.beboweb.shizukuinputtester;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * Shizuku UserService entry point. This is an AIDL Binder, not an Android Service.
 */
public final class InputUserService extends IInputService.Stub {
    private static final String TAG = "ShizukuInputTest";

    /** Shizuku v13+ prefers this constructor. The supplied Context is limited. */
    public InputUserService(Context context) {
        Log.i(TAG, "USER_SERVICE_CONTEXT_CONSTRUCTOR context="
                + (context == null ? "null" : context.getClass().getName())
                + " pid=" + Process.myPid()
                + " uid=" + Process.myUid()
                + " is64Bit=" + Process.is64Bit());
    }

    /** Fallback for older Shizuku versions. */
    public InputUserService() {
        Log.i(TAG, "USER_SERVICE_DEFAULT_CONSTRUCTOR pid=" + Process.myPid()
                + " uid=" + Process.myUid()
                + " is64Bit=" + Process.is64Bit());
    }

    @Override
    public void destroy() {
        Log.i(TAG, "USER_SERVICE_DESTROY pid=" + Process.myPid());
        System.exit(0);
    }

    @Override
    public String getIdentity() {
        String identity = "pid=" + Process.myPid()
                + " uid=" + Process.myUid()
                + " is64Bit=" + Process.is64Bit();
        Log.i(TAG, "GET_IDENTITY " + identity);
        return identity;
    }

    @Override
    public String injectTap(int x, int y, int source) {
        MotionEvent down = null;
        MotionEvent up = null;
        try {
            Log.i(TAG, "INJECT_BEGIN x=" + x + " y=" + y + " source=" + source);
            long downTime = SystemClock.uptimeMillis();
            down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                    (float) x, (float) y, 0);
            down.setSource(source);
            up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP,
                    (float) x, (float) y, 0);
            up.setSource(source);

            Class<?> cls = Class.forName("android.hardware.input.InputManager");
            Object manager = cls.getDeclaredMethod("getInstance").invoke(null);
            Method inject = cls.getDeclaredMethod(
                    "injectInputEvent", InputEvent.class, int.class);
            inject.setAccessible(true);
            boolean downOk = (Boolean) inject.invoke(manager, down, 0);
            boolean upOk = (Boolean) inject.invoke(manager, up, 0);
            String result = "DOWN=" + downOk + " UP=" + upOk
                    + " source=" + source + " uid=" + Process.myUid();
            Log.i(TAG, "INJECT_RESULT " + result);
            return result;
        } catch (Throwable error) {
            Log.e(TAG, "InputManager injection failed", error);
            return "ERROR: " + Log.getStackTraceString(error);
        } finally {
            if (down != null) down.recycle();
            if (up != null) up.recycle();
        }
    }
}