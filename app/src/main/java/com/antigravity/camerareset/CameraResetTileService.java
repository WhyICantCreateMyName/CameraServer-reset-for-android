package com.antigravity.camerareset;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class CameraResetTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState(Tile.STATE_INACTIVE, "Reset Camera", "Nhấn để giải phóng");
    }

    @Override
    public void onClick() {
        super.onClick();
        updateTileState(Tile.STATE_ACTIVE, "Đang xử lý...", "Đang reset camera");
        vibrate(80);

        CameraResetManager.performReset(this, new CameraResetManager.ResetCallback() {
            @Override
            public void onProgress(String message) {
                // Background progress
            }

            @Override
            public void onComplete(boolean success, String summary, String logPath) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateTileState(Tile.STATE_INACTIVE, "Reset Camera", "Sẵn sàng");
                    vibrate(150);

                    String msg = "Đã reset Camera! Log: /sdcard/CameraLogs/";
                    if (summary != null && !summary.isEmpty()) {
                        msg = summary + "\n" + msg;
                    }
                    Toast.makeText(CameraResetTileService.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateTileState(int state, String label, String subtitle) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(state);
            tile.setLabel(label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(subtitle);
            }
            tile.updateTile();
        }
    }

    private void vibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }
    }
}

