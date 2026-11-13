package io.github.beboweb.shizukuinputtester;

import android.app.Application;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class DebugApp extends Application {
    private static final String TAG = "ShizukuInputTester";

    @Override public void onCreate() {
        super.onCreate();
        Log.i(TAG, "APP_START application onCreate");
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringWriter buffer = new StringWriter();
            error.printStackTrace(new PrintWriter(buffer));
            String report = "FATAL_THREAD=" + thread.getName() + "\n" + buffer;
            Log.e(TAG, report);
            try {
                File file = new File(getFilesDir(), "crash-last.txt");
                FileWriter writer = new FileWriter(file, false);
                writer.write(report);
                writer.close();
                Log.e(TAG, "CRASH_REPORT_SAVED=" + file.getAbsolutePath());
            } catch (Throwable writeError) {
                Log.e(TAG, "Could not save crash report", writeError);
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}