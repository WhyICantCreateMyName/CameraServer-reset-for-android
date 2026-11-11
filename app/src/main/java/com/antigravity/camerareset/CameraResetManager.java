package com.antigravity.camerareset;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class CameraResetManager {
    private static final String TAG = "CameraResetManager";
    public static final String LOG_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CameraLogs";

    public interface ResetCallback {
        void onProgress(String message);
        void onComplete(boolean success, String summary, String logPath);
    }

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasShizukuPermission() {
        try {
            if (!isShizukuAvailable()) {
                Log.d(TAG, "Shizuku not available (pingBinder = false)");
                return false;
            }
            boolean granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Shizuku checkSelfPermission: " + (granted ? "GRANTED" : "DENIED"));
            return granted;
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku checkSelfPermission exception: " + t.getMessage());
            return false;
        }
    }

    public static String executeShell(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = null;
            if (hasShizukuPermission()) {
                try {
                    java.lang.reflect.Method method = Shizuku.class.getDeclaredMethod(
                            "newProcess", String[].class, String[].class, String.class);
                    method.setAccessible(true);
                    process = (Process) method.invoke(null, new String[]{"sh", "-c", command}, null, null);
                } catch (Throwable t) {
                    Log.w(TAG, "Shizuku newProcess reflection failed: " + t.getMessage());
                }
            }
            if (process == null) {
                process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Shell exec error: " + e.getMessage());
            output.append("Error executing command: ").append(e.getMessage()).append("\n");
        }
        return output.toString();
    }

    public static void performReset(Context context, ResetCallback callback) {
        new Thread(() -> {
            SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SimpleDateFormat sdfFile = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            Date now = new Date();
            String timestamp = sdfTime.format(now);
            String fileDate = sdfFile.format(now);

            callback.onProgress("Đang kiểm tra tiến trình chiếm Camera Server...");

            // 1. Lấy thông tin dumpsys media.camera & media.camera.worker
            String cameraDump = executeShell("dumpsys media.camera");
            String workerDump = executeShell("dumpsys media.camera.worker");

            // 2. Phân tích tiến trình đang chiếm hook
            List<String> activeClients = extractActiveClients(cameraDump);
            List<String> recentEvents = extractRecentEvents(cameraDump, 20);

            StringBuilder summaryBuilder = new StringBuilder();
            if (!activeClients.isEmpty()) {
                summaryBuilder.append("Phát hiện tiến trình đang giữ Camera: ").append(String.join(", ", activeClients));
            } else {
                summaryBuilder.append("Không thấy client active. Có thể HAL camera driver bị treo/rò rỉ.");
            }

            // 3. Tiến hành giải phóng và reset
            callback.onProgress("Đang giải phóng camera hook...");

            // 3.1 Force stop các tiến trình đang chiếm camera nếu có Shizuku
            List<String> killedApps = new ArrayList<>();
            for (String clientInfo : activeClients) {
                String pkg = extractPackageName(clientInfo);
                if (pkg != null && !pkg.equals(context.getPackageName())) {
                    executeShell("am force-stop " + pkg);
                    killedApps.add(pkg);
                }
            }

            // 3.2 Thử reset cameraserver qua shell
            executeShell("cmd media.camera set-camera-mute 1");
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            executeShell("cmd media.camera set-camera-mute 0");

            // 3.3 Force stop Samsung Camera nếu nó đang chạy nền bị kẹt
            executeShell("am force-stop com.sec.android.app.camera");

            // 3.4 Thử kill cameraserver & samsung provider (nếu quyền shell/root cho phép)
            executeShell("pkill -f cameraserver");
            executeShell("pkill -f camera.provider");

            // 3.5 Native Camera2 Eviction Pulse (mô phỏng cuộc gọi video để buộc giải phóng HAL)
            callback.onProgress("Đang thực hiện xung giải phóng Camera HAL (Video call pulse)...");
            pulseCameraHardware(context);

            // 4. Ghi log ra thư mục bộ nhớ trong (/sdcard/CameraLogs)
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            File detailLog = new File(logDir, "camera_reset_" + fileDate + ".log");
            File historyLog = new File(logDir, "history.txt");

            StringBuilder logContent = new StringBuilder();
            logContent.append("=========================================\n");
            logContent.append("CAMERA RESET & HOOK INSPECTION LOG\n");
            logContent.append("Thời gian: ").append(timestamp).append("\n");
            logContent.append("Thiết bị: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            logContent.append("One UI / OS: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
            logContent.append("Trạng thái Shizuku: ").append(hasShizukuPermission() ? "Đã cấp quyền" : "Không khả dụng").append("\n");
            logContent.append("=========================================\n\n");

            logContent.append("[1. TIẾN TRÌNH CHIẾM HOOK CAMERA]:\n");
            if (activeClients.isEmpty()) {
                logContent.append("Không có app nào đang chiếm Active Camera Clients trực tiếp.\n");
            } else {
                for (String c : activeClients) {
                    logContent.append("- ").append(c).append("\n");
                }
            }
            if (!killedApps.isEmpty()) {
                logContent.append("\n-> Đã buộc dừng (force-stop): ").append(String.join(", ", killedApps)).append("\n");
            }
            logContent.append("\n");

            logContent.append("[2. NHẬT KÝ SỰ KIỆN CAMERA GẦN NHẤT (Camera Service Events)]:\n");
            for (String event : recentEvents) {
                logContent.append(event).append("\n");
            }
            logContent.append("\n");

            logContent.append("[3. CHI TIẾT DUMPSYS MEDIA.CAMERA WORKER]:\n");
            logContent.append(workerDump.length() > 3000 ? workerDump.substring(0, 3000) + "\n...[truncated]" : workerDump);
            logContent.append("\n\n");

            logContent.append("[4. CHI TIẾT DUMPSYS MEDIA.CAMERA]:\n");
            logContent.append(cameraDump.length() > 5000 ? cameraDump.substring(0, 5000) + "\n...[truncated]" : cameraDump);

            // Ghi file chi tiết
            writeFile(detailLog, logContent.toString());

            // Ghi file lịch sử ngắn
            String historyLine = String.format("[%s] Reset triggered | Active: %s | Killed: %s\n",
                    timestamp,
                    activeClients.isEmpty() ? "None" : String.join(";", activeClients),
                    killedApps.isEmpty() ? "None" : String.join(";", killedApps));
            appendFile(historyLog, historyLine);

            String finalSummary = summaryBuilder.toString();
            callback.onComplete(true, finalSummary, detailLog.getAbsolutePath());

        }).start();
    }

    private static void pulseCameraHardware(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) return;

        CountDownLatch latch = new CountDownLatch(1);
        HandlerThread thread = new HandlerThread("CameraPulseThread");
        thread.start();
        Handler handler = new Handler(thread.getLooper());

        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length > 0) {
                String targetCameraId = cameraIds[0];
                if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(targetCameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            Log.d(TAG, "Camera pulse: Camera opened successfully. Closing to release...");
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException ignored) {}
                            camera.close();
                            latch.countDown();
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            Log.d(TAG, "Camera pulse: Disconnected");
                            camera.close();
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            Log.e(TAG, "Camera pulse: Error " + error);
                            camera.close();
                            latch.countDown();
                        }
                    }, handler);

                    latch.await(1500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (CameraAccessException | SecurityException | InterruptedException e) {
            Log.e(TAG, "Pulse camera failed: " + e.getMessage());
        } finally {
            thread.quitSafely();
        }
    }

    private static List<String> extractActiveClients(String dump) {
        List<String> clients = new ArrayList<>();
        if (dump == null) return clients;

        int activeIdx = dump.indexOf("Active Camera Clients:");
        if (activeIdx != -1) {
            int endIdx = dump.indexOf("Allowed user IDs:", activeIdx);
            if (endIdx == -1) endIdx = dump.indexOf("==", activeIdx);
            if (endIdx != -1) {
                String section = dump.substring(activeIdx, endIdx);
                String[] lines = section.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equals("Active Camera Clients:") && !line.equals("[]")) {
                        clients.add(line);
                    }
                }
            }
        }
        return clients;
    }

    private static List<String> extractRecentEvents(String dump, int maxCount) {
        List<String> events = new ArrayList<>();
        if (dump == null) return events;

        int startIdx = dump.indexOf("== Camera service events log (most recent at top): ==");
        if (startIdx != -1) {
            int endIdx = dump.indexOf("== Camera service events log", startIdx + 50);
            if (endIdx == -1) endIdx = dump.indexOf("Camera device", startIdx);
            if (endIdx == -1) endIdx = dump.length();

            String section = dump.substring(startIdx, endIdx);
            String[] lines = section.split("\n");
            int count = 0;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("1") || line.startsWith("2") || line.contains("CONNECT") || line.contains("DISCONNECT") || line.contains("REJECT")) {
                    events.add(line);
                    count++;
                    if (count >= maxCount) break;
                }
            }
        }
        return events;
    }

    private static String extractPackageName(String text) {
        Pattern pattern = Pattern.compile("package\\s+([a-zA-Z0-9._]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Pattern patternClient = Pattern.compile("Client\\[.*?,([a-zA-Z0-9._]+),.*?\\]");
        Matcher matcherClient = patternClient.matcher(text);
        if (matcherClient.find()) {
            return matcherClient.group(1);
        }
        return null;
    }

    private static void writeFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(content);
        } catch (Exception e) {
            Log.e(TAG, "Write file failed directly, using shell fallback: " + e.getMessage());
            // Fallback writing via shell in case of scoped storage restrictions
            executeShell("cat << 'EOF' > \"" + file.getAbsolutePath() + "\"\n" + content + "\nEOF");
        }
    }

    private static void appendFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(content);
        } catch (Exception e) {
            executeShell("echo \"" + content.replace("\"", "\\\"").trim() + "\" >> \"" + file.getAbsolutePath() + "\"");
        }
    }
}
