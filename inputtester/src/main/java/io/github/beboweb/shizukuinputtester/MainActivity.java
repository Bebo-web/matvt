package io.github.beboweb.shizukuinputtester;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.view.InputDevice;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 71;
    private TextView status;
    private TextView output;
    private EditText x;
    private EditText y;
    private IInputService service;
    private Shizuku.UserServiceArgs serviceArgs;
    private boolean listenersRegistered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable bindTimeout;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, result) -> {
                if (requestCode != REQUEST_CODE) return;
                if (result == 0) bindInputService();
                else status.setText("Shizuku permission denied. Grant permission and retry.");
            };

    private final Shizuku.OnBinderReceivedListener binderListener =
            () -> runOnUiThread(() -> status.setText("Shizuku binder connected. Tap Connect."));

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (bindTimeout != null) handler.removeCallbacks(bindTimeout);
            service = IInputService.Stub.asInterface(binder);
            try {
                String identity = service.getIdentity();
                status.setText("CONNECTED: " + identity);
                appendLog("SERVICE_CONNECTED " + identity);
            } catch (Throwable error) {
                status.setText("Binder connected; identity failed: " + error);
                appendLog("IDENTITY_ERROR " + android.util.Log.getStackTraceString(error));
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (bindTimeout != null) handler.removeCallbacks(bindTimeout);
            service = null;
            status.setText("UserService disconnected.");
            appendLog("SERVICE_DISCONNECTED");
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        status = new TextView(this);
        status.setText("Tester opened. Start Shizuku, then connect.");
        layout.addView(status);

        x = new EditText(this);
        x.setHint("X screen pixels");
        x.setInputType(2);
        x.setText("500");
        layout.addView(x);

        y = new EditText(this);
        y.setHint("Y screen pixels");
        y.setInputType(2);
        y.setText("500");
        layout.addView(y);

        Button connect = new Button(this);
        connect.setText("Connect to Shizuku");
        layout.addView(connect);
        Button touch = new Button(this);
        touch.setText("Inject TOUCH tap");
        layout.addView(touch);
        Button mouse = new Button(this);
        mouse.setText("Inject MOUSE tap");
        layout.addView(mouse);

        output = new TextView(this);
        output.setTextIsSelectable(true);
        layout.addView(output);
        TextView note = new TextView(this);
        note.setText("Coordinates are physical screen pixels. Test on a harmless target.");
        layout.addView(note);
        setContentView(layout);

        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), InputUserService.class.getName()))
                .daemon(false).tag("shizuku-input-tester").processNameSuffix("input").debuggable(true).version(2);

        connect.setOnClickListener(v -> connectToShizuku());
        touch.setOnClickListener(v -> inject(InputDevice.SOURCE_TOUCHSCREEN));
        mouse.setOnClickListener(v -> inject(InputDevice.SOURCE_MOUSE));

        try {
            Shizuku.addBinderReceivedListenerSticky(binderListener);
            Shizuku.addRequestPermissionResultListener(permissionListener);
            listenersRegistered = true;
            appendLog("APP_READY package=" + getPackageName()
                    + " appUid=" + android.os.Process.myUid()
                    + " shizukuBinder=" + Shizuku.pingBinder());
        } catch (Throwable error) {
            status.setText("UI ready, Shizuku init error: " + error);
            appendLog("INIT_ERROR " + android.util.Log.getStackTraceString(error));
        }
    }

    private void connectToShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                status.setText("Shizuku binder unavailable. Start Shizuku first.");
                appendLog("PING_BINDER_FALSE");
                return;
            }
            if (Shizuku.checkSelfPermission() != 0) {
                status.setText("Requesting Shizuku permission...");
                Shizuku.requestPermission(REQUEST_CODE);
                return;
            }
            appendLog("PERMISSION_GRANTED; requesting UserService");
            bindInputService();
        } catch (Throwable error) {
            status.setText("Shizuku connection error: " + error);
            appendLog("CONNECT_ERROR " + android.util.Log.getStackTraceString(error));
        }
    }

    private void bindInputService() {
        try {
            status.setText("Starting Shizuku UserService (30-second diagnostic timeout)...");
            appendLog("BIND_REQUEST apiVersion=" + Shizuku.getVersion() + " shizukuUid=" + Shizuku.getUid() + " appUid=" + android.os.Process.myUid());
            if (bindTimeout != null) handler.removeCallbacks(bindTimeout);
            bindTimeout = () -> {
                if (service == null) {
                    status.setText("TIMEOUT: UserService Binder callback not received within 30 seconds.");
                    appendLog("BIND_TIMEOUT 30000ms; inspect Shizuku server logcat for UserService start/attach failure");
                    try { Shizuku.unbindUserService(serviceArgs, connection, true); }
                    catch (Throwable error) { appendLog("UNBIND_ERROR " + error); }
                }
            };
            handler.postDelayed(bindTimeout, 30000);
            Shizuku.bindUserService(serviceArgs, connection);
        } catch (Throwable error) {
            if (bindTimeout != null) handler.removeCallbacks(bindTimeout);
            status.setText("Could not start UserService: " + error);
            appendLog("BIND_EXCEPTION " + android.util.Log.getStackTraceString(error));
        }
    }

    private void appendLog(String line) {
        String entry = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())
                + " " + line;
        if (output != null) output.append(entry + "\n");
        android.util.Log.i("ShizukuInputTester", entry);
        try (java.io.FileOutputStream stream =
                     openFileOutput("tester-log.txt", MODE_APPEND)) {
            stream.write((entry + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    private void inject(int source) {
        if (service == null) {
            appendLog("INJECT_REJECTED service_not_connected");
            return;
        }
        try {
            int px = Integer.parseInt(x.getText().toString().trim());
            int py = Integer.parseInt(y.getText().toString().trim());
            String result = service.injectTap(px, py, source);
            output.append(result + "\n");
            appendLog("INJECT_RESULT " + result);
        } catch (Throwable error) {
            appendLog("INJECT_ERROR " + android.util.Log.getStackTraceString(error));
        }
    }

    @Override protected void onDestroy() {
        if (bindTimeout != null) handler.removeCallbacks(bindTimeout);
        if (listenersRegistered) {
            try {
                Shizuku.removeBinderReceivedListener(binderListener);
                Shizuku.removeRequestPermissionResultListener(permissionListener);
            } catch (Throwable ignored) { }
        }
        if (service != null && serviceArgs != null) {
            try { Shizuku.unbindUserService(serviceArgs, connection, true); }
            catch (Throwable ignored) { }
        }
        super.onDestroy();
    }
}
