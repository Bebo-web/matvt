package io.github.beboweb.shizukuinputtester;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.pm.PackageManager;
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

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, result) -> {
                if (requestCode != REQUEST_CODE) return;
                if (result == PackageManager.PERMISSION_GRANTED) {
                    bindInputService();
                } else {
                    status.setText("Shizuku permission denied. Grant permission and retry.");
                }
            };

    private final Shizuku.OnBinderReceivedListener binderListener =
            () -> runOnUiThread(() -> status.setText("Shizuku binder connected. Tap Connect."));

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IInputService.Stub.asInterface(binder);
            try {
                status.setText("Connected to Shell service: " + service.getIdentity());
            } catch (Throwable error) {
                status.setText("Service connected, identity check failed: " + error);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            status.setText("Shell service disconnected.");
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        status = new TextView(this);
        status.setText("Tester opened. Shizuku connection has not been checked yet.");
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
                .daemon(false)
                .processNameSuffix("input")
                .debuggable(false)
                .version(1);

        connect.setOnClickListener(view -> connectToShizuku());
        touch.setOnClickListener(view -> inject(InputDevice.SOURCE_TOUCHSCREEN));
        mouse.setOnClickListener(view -> inject(InputDevice.SOURCE_MOUSE));

        // Register listeners only after the UI exists; a Shizuku-side problem
        // must be shown on screen rather than closing the tester at launch.
        try {
            Shizuku.addBinderReceivedListenerSticky(binderListener);
            Shizuku.addRequestPermissionResultListener(permissionListener);
            listenersRegistered = true;
        } catch (Throwable error) {
            status.setText("UI opened, but Shizuku initialization failed: " + error);
            output.setText(android.util.Log.getStackTraceString(error));
        }
    }

    private void connectToShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                status.setText("Shizuku is not reachable. Start Shizuku, then retry.");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE);
                status.setText("Waiting for Shizuku permission...");
                return;
            }
            bindInputService();
        } catch (Throwable error) {
            status.setText("Shizuku connection error: " + error);
            output.setText(android.util.Log.getStackTraceString(error));
        }
    }

    private void bindInputService() {
        try {
            Shizuku.bindUserService(serviceArgs, connection);
            status.setText("Starting Shell UserService...");
        } catch (Throwable error) {
            status.setText("Could not start Shell service: " + error);
            output.setText(android.util.Log.getStackTraceString(error));
        }
    }

    private void inject(int source) {
        if (service == null) {
            output.setText("Connect to Shizuku first.");
            return;
        }
        try {
            int px = Integer.parseInt(x.getText().toString().trim());
            int py = Integer.parseInt(y.getText().toString().trim());
            output.setText(service.injectTap(px, py, source));
        } catch (Throwable error) {
            output.setText("Injection error: " + error + "\\n"
                    + android.util.Log.getStackTraceString(error));
        }
    }

    @Override
    protected void onDestroy() {
        if (listenersRegistered) {
            try {
                Shizuku.removeBinderReceivedListener(binderListener);
                Shizuku.removeRequestPermissionResultListener(permissionListener);
            } catch (Throwable ignored) {
            }
        }
        if (service != null && serviceArgs != null) {
            try {
                Shizuku.unbindUserService(serviceArgs, connection, true);
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }
}
