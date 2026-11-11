package com.antigravity.camerareset;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class CameraResetReceiver extends BroadcastReceiver {
    private static final String TAG = "CameraResetReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Broadcast received: " + intent.getAction());
        CameraResetManager.performReset(context, new CameraResetManager.ResetCallback() {
            @Override
            public void onProgress(String message) {
                Log.d(TAG, message);
            }

            @Override
            public void onComplete(boolean success, String summary, String logPath) {
                Log.d(TAG, "Reset complete: " + summary + " -> " + logPath);
                Toast.makeText(context, "Camera Reset xong!\n" + summary, Toast.LENGTH_LONG).show();
            }
        });
    }
}

