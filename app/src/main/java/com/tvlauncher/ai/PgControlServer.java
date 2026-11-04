package com.tvlauncher.ai;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fi.iki.elonen.NanoHTTPD;

/** Token-protected local API. No arbitrary shell, ADB, or root execution. */
public final class PgControlServer extends NanoHTTPD {
    private static final int PORT = 8765;
    private static final String PREFS = "pg_control";
    private static volatile PgControlServer instance;
    private final Context context;

    private PgControlServer(Context context) throws IOException {
        super("0.0.0.0", PORT);
        this.context = context.getApplicationContext();
        start(SOCKET_READ_TIMEOUT, false);
    }

    public static synchronized void ensureStarted(Context context) {
        if (instance != null && instance.isAlive()) return;
        try { instance = new PgControlServer(context); }
        catch (Exception ignored) { instance = null; }
    }

    public static String getPairingToken(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = p.getString("pairing_token", "");
        if (token.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            p.edit().putString("pairing_token", token).apply();
        }
        return token;
    }

    @Override public Response serve(IHTTPSession session) {
        try {
            String path = session.getUri();
            if ("GET".equals(session.getMethod().name()) && "/v1/health".equals(path)) {
                JSONObject out = new JSONObject();
                out.put("ok", true); out.put("service", "PG Control Agent"); out.put("protocol", 1);
                return json(Response.Status.OK, out);
            }
            if (!authorized(session)) return json(Response.Status.UNAUTHORIZED, error("unauthorized"));
            if ("GET".equals(session.getMethod().name())) {
                if ("/v1/device".equals(path)) return json(Response.Status.OK, deviceInfo());
                if ("/v1/packages".equals(path)) return json(Response.Status.OK, packages());
                if ("/v1/launcher".equals(path)) return json(Response.Status.OK, launcherInfo());
                return json(Response.Status.NOT_FOUND, error("not_found"));
            }
            if ("POST".equals(session.getMethod().name()) && "/v1/launcher/app-config".equals(path)) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                if (body == null) return json(Response.Status.BAD_REQUEST, error("missing_body"));
                JSONObject input = new JSONObject(body);
                String pkg = input.optString("packageName", "").trim();
                if (pkg.isEmpty() || pkg.length() > 200) return json(Response.Status.BAD_REQUEST, error("invalid_package"));
                try { context.getPackageManager().getApplicationInfo(pkg, 0); }
                catch (Exception e) { return json(Response.Status.NOT_FOUND, error("package_not_installed")); }
                JSONObject config = new JSONObject();
                if (input.has("label")) config.put("label", input.optString("label").trim());
                if (input.has("cardWidthDp")) config.put("cardWidthDp", clamp(input.optInt("cardWidthDp", 128), 80, 320));
                if (input.has("cardHeightDp")) config.put("cardHeightDp", clamp(input.optInt("cardHeightDp", 66), 48, 220));
                context.getSharedPreferences("pg_control_apps", Context.MODE_PRIVATE)
                    .edit().putString(pkg, config.toString()).apply();
                JSONObject result = new JSONObject();
                result.put("ok", true); result.put("packageName", pkg); result.put("config", config);
                return json(Response.Status.OK, result);
            }
            return json(Response.Status.NOT_FOUND, error("not_found"));
        } catch (Exception e) {
            return json(Response.Status.BAD_REQUEST, error("bad_request"));
        }
    }

    private boolean authorized(IHTTPSession session) {
        String supplied = session.getHeaders().get("authorization");
        if (supplied == null) supplied = session.getHeaders().get("Authorization");
        String expected = "Bearer " + getPairingToken(context);
        return supplied != null && constantTimeEquals(expected, supplied.trim());
    }

    private JSONObject deviceInfo() throws Exception {
        JSONObject out = new JSONObject();
        out.put("model", android.os.Build.MODEL);
        out.put("manufacturer", android.os.Build.MANUFACTURER);
        out.put("androidRelease", android.os.Build.VERSION.RELEASE);
        out.put("sdkInt", android.os.Build.VERSION.SDK_INT);
        out.put("device", android.os.Build.DEVICE);
        out.put("protocol", 1);
        return out;
    }

    private JSONObject packages() throws Exception {
        JSONArray list = new JSONArray();
        PackageManager pm = context.getPackageManager();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null) continue;
            JSONObject item = new JSONObject();
            item.put("packageName", info.packageName);
            item.put("label", String.valueOf(pm.getApplicationLabel(app)));
            item.put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            item.put("enabled", app.enabled);
            item.put("versionName", info.versionName == null ? "" : info.versionName);
            list.put(item);
        }
        JSONObject out = new JSONObject(); out.put("apps", list); out.put("count", list.length());
        return out;
    }

    private JSONObject launcherInfo() throws Exception {
        JSONObject out = new JSONObject();
        out.put("packageName", context.getPackageName());
        out.put("name", "TV Launcher PG");
        out.put("protocol", 1);
        JSONObject settings = new JSONObject();
        android.content.SharedPreferences p = context.getSharedPreferences("tvpg", Context.MODE_PRIVATE);
        settings.put("hasCustomWallpaper", !p.getString("wallpaper_uri", "").isEmpty());
        settings.put("hasTmdbKey", !p.getString("tmdb_key", "").isEmpty());
        out.put("settings", settings);
        return out;
    }

    private JSONObject error(String message) throws Exception {
        JSONObject out = new JSONObject(); out.put("ok", false); out.put("error", message); return out;
    }

    private Response json(Response.Status status, JSONObject data) {
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", data.toString());
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}