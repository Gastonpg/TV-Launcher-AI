package com.tvbox.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

public class AgentService extends Service {
    public static final String ACTION_START = "com.tvbox.agent.START";
    private static final int PORT = 8765;
    private volatile boolean running = false;
    private ServerSocket server;
    private Thread worker;
    private String token;

    @Override public void onCreate() {
        super.onCreate();
        createToken();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("tvbox_agent", "TV Box Agent", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void createToken() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.US, "%02x", x & 0xff));
        token = s.toString();
        getSharedPreferences("agent", MODE_PRIVATE).edit().putString("token", token).apply();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "tvbox_agent")
                : new Notification.Builder(this);
        nb.setContentTitle("TV Box Agent activo")
          .setContentText("Diagnóstico local de solo lectura · puerto " + PORT)
          .setSmallIcon(android.R.drawable.stat_sys_upload_done)
          .setOngoing(true);
        startForeground(1, nb.build());
        if (!running) startServer();
        return START_STICKY;
    }

    private void startServer() {
        running = true;
        worker = new Thread(() -> {
            try {
                server = new ServerSocket(PORT, 8, java.net.InetAddress.getByName("0.0.0.0"));
                while (running) {
                    Socket client = server.accept();
                    Thread t = new Thread(() -> handle(client));
                    t.start();
                }
            } catch (Exception e) {
                running = false;
                stopSelf();
            }
        }, "tvbox-agent-server");
        worker.start();
    }

    private void handle(Socket socket) {
        try (Socket c = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = c.getOutputStream()) {
            c.setSoTimeout(5000);
            String first = in.readLine();
            if (first == null) return;
            String auth = "";
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int k = line.indexOf(':');
                if (k > 0 && line.substring(0, k).trim().equalsIgnoreCase("Authorization"))
                    auth = line.substring(k + 1).trim();
            }
            String[] parts = first.split(" ");
            String path = parts.length > 1 ? parts[1].split("\\?")[0] : "/";
            int code = 200;
            JSONObject body;
            if (!"GET".equals(parts.length > 0 ? parts[0] : "")) {
                code = 405; body = error("Only GET is supported");
            } else if (!("Bearer " + token).equals(auth)) {
                code = 401; body = error("Unauthorized");
            } else {
                body = route(path);
                if (body == null) { code = 404; body = error("Not found"); }
            }
            byte[] bytes = body.toString(2).getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 401 ? " Unauthorized" : code == 404 ? " Not Found" : " Method Not Allowed") +
                    "\r\nContent-Type: application/json; charset=utf-8\r\nConnection: close\r\nContent-Length: " + bytes.length + "\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8)); out.write(bytes); out.flush();
        } catch (Exception ignored) {}
    }

    private JSONObject route(String path) throws Exception {
        JSONObject j = new JSONObject();
        if ("/v1/health".equals(path)) {
            j.put("ok", true); j.put("service", "TV Box Agent"); j.put("version", "1.0"); j.put("port", PORT);
            return j;
        }
        if ("/v1/device".equals(path)) {
            j.put("manufacturer", Build.MANUFACTURER);
            j.put("brand", Build.BRAND);
            j.put("model", Build.MODEL);
            j.put("device", Build.DEVICE);
            j.put("board", Build.BOARD);
            j.put("android_release", Build.VERSION.RELEASE);
            j.put("sdk", Build.VERSION.SDK_INT);
            j.put("fingerprint", Build.FINGERPRINT);
            j.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
            j.put("secure_android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            return j;
        }
        if ("/v1/launchers".equals(path)) {
            JSONArray a = new JSONArray();
            Intent q = new Intent(Intent.ACTION_MAIN); q.addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(q, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                JSONObject x = new JSONObject();
                x.put("package", ri.activityInfo.packageName);
                x.put("activity", ri.activityInfo.name);
                x.put("label", String.valueOf(ri.loadLabel(getPackageManager())));
                a.put(x);
            }
            j.put("home_candidates", a);
            return j;
        }
        if ("/v1/packages".equals(path)) {
            JSONArray a = new JSONArray();
            for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                JSONObject x = new JSONObject();
                x.put("package", ai.packageName);
                x.put("label", String.valueOf(ai.loadLabel(getPackageManager())));
                x.put("system", (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                a.put(x);
            }
            j.put("count", a.length()); j.put("packages", a);
            return j;
        }
        if ("/v1/diagnostics".equals(path)) {
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            ((android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            android.os.StatFs fs = new android.os.StatFs(android.os.Environment.getDataDirectory().getAbsolutePath());
            j.put("memory_total_bytes", mi.totalMem); j.put("memory_available_bytes", mi.availMem);
            j.put("data_total_bytes", fs.getTotalBytes()); j.put("data_available_bytes", fs.getAvailableBytes());
            j.put("low_memory", mi.lowMemory); j.put("agent_version", "1.0");
            return j;
        }
        return null;
    }

    private JSONObject error(String s) {
        JSONObject j = new JSONObject();
        try { j.put("error", s); } catch (Exception ignored) {}
        return j;
    }

    @Override public void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
