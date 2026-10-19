package io.github.virresh.matvt.helper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import io.github.virresh.matvt.R;
import io.github.virresh.matvt.services.MouseEventService;

public class KeyEventHandler {
    private static final long BOSS_KEY_DETECT_HOLD_MS = 1200L;

    private final AppPreferences appPreferences;
    private final Context context;
    private final MouseEventService mouseEventService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int candidateKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private boolean dialogShownForCurrentPress;
    private boolean dialogShowing;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (candidateKeyCode != KeyEvent.KEYCODE_UNKNOWN && !dialogShownForCurrentPress) {
                dialogShownForCurrentPress = true;
                showChangeBossKeyDialog(candidateKeyCode);
            }
        }
    };

    public KeyEventHandler(MouseEventService service) {
        this.context = service;
        this.mouseEventService = service;
        this.appPreferences = new AppPreferences(context);
    }

    public void handleKeyEvent(KeyEvent event) {
        if (event == null) return;

        // Boss-key reassignment is only allowed while the explicit Detect screen is open.
        // Otherwise holding the configured boss key would repeatedly summon the dialog
        // and interfere with mouse dragging on the launcher.
        if (!KeyDetection.isDetectionActive()) {
            mainHandler.removeCallbacks(longPressRunnable);
            candidateKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            dialogShownForCurrentPress = false;
            return;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // Start one timer per physical press. Repeated ACTION_DOWN events must not
            // create multiple dialogs while the user continues holding the key.
            if (candidateKeyCode == KeyEvent.KEYCODE_UNKNOWN || candidateKeyCode != event.getKeyCode()) {
                mainHandler.removeCallbacks(longPressRunnable);
                candidateKeyCode = event.getKeyCode();
                dialogShownForCurrentPress = false;
                mainHandler.postDelayed(longPressRunnable, BOSS_KEY_DETECT_HOLD_MS);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            mainHandler.removeCallbacks(longPressRunnable);
            candidateKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            dialogShownForCurrentPress = false;
        }
    }

    private void showChangeBossKeyDialog(final int keyCode) {
        if (dialogShowing || mouseEventService == null) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (dialogShowing) return;
                dialogShowing = true;

                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Wolf_Alert_Disp);
                builder.setTitle(R.string.confirm_changes_title);
                builder.setMessage(context.getString(R.string.confirm_set_boss_key_message, keyCode));
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        appPreferences.setBossKeyDisabled(false);
                        appPreferences.setOverrideStatus(true);
                        appPreferences.setBossKeyValue(keyCode);
                        mouseEventService.updatePreferences();
                        Toast.makeText(context, context.getString(R.string.new_boss_key_toast, keyCode), Toast.LENGTH_SHORT).show();
                        dialogInterface.dismiss();
                    }
                });
                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alert = builder.create();
                alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialogShowing = false;
                    }
                });

                if (alert.getWindow() != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                    } else {
                        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    }
                }

                try {
                    alert.show();
                } catch (WindowManager.BadTokenException | SecurityException exception) {
                    dialogShowing = false;
                    android.util.Log.e("MATVT_SERVICE", "Unable to show boss-key detection dialog", exception);
                }
            }
        });
    }
}
