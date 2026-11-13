package io.github.beboweb.shizukuinputtester;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.util.Log;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Shizuku UserService process. The framework binds this class and receives
 * the AIDL binder returned by onBind().
 */
public final class InputUserService extends Shizuku.UserService {
    private final IInputService.Stub binder = new IInputService.Stub() {
        @Override public void destroy() { stopSelf(); }

        @Override public String getIdentity() {
            return "pid=" + Process.myPid() + " uid=" + Process.myUid();
        }

        @Override public String injectTap(int x, int y, int source) {
            MotionEvent down = null;
            MotionEvent up = null;
            try {
                long downTime = SystemClock.uptimeMillis();
                down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                        (float) x, (float) y, 0);
                down.setSource(source);
                up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP,
                        (float) x, (float) y, 0);
                up.setSource(source);

                Class<?> cls = Class.forName("android.hardware.input.InputManager");
                Object manager = cls.getDeclaredMethod("getInstance").invoke(null);
                Method inject = cls.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
                inject.setAccessible(true);
                boolean downOk = (Boolean) inject.invoke(manager, down, 0);
                boolean upOk = (Boolean) inject.invoke(manager, up, 0);
                return "DOWN=" + downOk + " UP=" + upOk + " source=" + source
                        + " uid=" + Process.myUid();
            } catch (Throwable error) {
                Log.e("ShizukuInputTest", "InputManager injection failed", error);
                return "ERROR: " + Log.getStackTraceString(error);
            } finally {
                if (down != null) down.recycle();
                if (up != null) up.recycle();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.i("ShizukuInputTest", "UserService onBind pid=" + Process.myPid()
                + " uid=" + Process.myUid());
        return binder;
    }
}
