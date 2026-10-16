package com.tvlauncher.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int white = Color.WHITE;
    private final int muted = Color.rgb(204, 213, 230);
    private final int blue = Color.rgb(130, 184, 255);
    private final int cardColor = Color.argb(215, 24, 33, 51);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ImageView heroImage;
    private TextView heroTitle, heroDescription, statusText;
    private LinearLayout appRail;
    private int fallbackIndex = 0;
    private boolean useTmdb = false;
    private String tmdbKey = "";
    private final SharedPreferences prefs() { return getSharedPreferences("tvpg", MODE_PRIVATE); }

    // Online cinematic backdrops used until the user configures a TMDB key.
    private final String[] fallbackImages = new String[] {
        "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?auto=format&fit=crop&w=1920&q=85",
        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1920&q=85",
        "https://images.unsplash.com/photo-1478720568477-152d9b164e26?auto=format&fit=crop&w=1920&q=85",
        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=1920&q=85",
        "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1920&q=85",
        "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?auto=format&fit=crop&w=1920&q=85"
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        tmdbKey = prefs().getString("tmdb_key", "").trim();
        buildUi();
        loadApps();
        refreshFeatured();
        handler.postDelayed(new Runnable() {
            @Override public void run() { refreshFeatured(); handler.postDelayed(this, 6 * 60 * 60 * 1000L); }
        }, 6 * 60 * 60 * 1000L);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 13, 21));
        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { Color.rgb(8, 11, 18), Color.argb(220, 8, 11, 18), Color.argb(85, 8, 11, 18), Color.argb(25, 8, 11, 18) });
        shade.setBackground(gradient);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { Color.TRANSPARENT, Color.argb(180, 8, 11, 18), Color.rgb(8, 11, 18) }));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, dp(390), Gravity.BOTTOM);
        root.addView(bottomShade, bottomParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(42), dp(24), dp(42), dp(20));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -1);
        root.addView(content, contentParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("PG", 19, white, true);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff82b8ff, 0xff9c8cff});
        logoBg.setCornerRadius(dp(12)); logo.setBackground(logoBg);
        header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(42)));
        TextView brand = text("TV LAUNCHER PG", 17, white, true);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-2, -2); brandParams.leftMargin = dp(13);
        header.addView(brand, brandParams);
        View spacer = new View(this); header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        Button settings = button("⚙  Configurar", false);
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(-2, dp(44)));
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        View flex = new View(this); content.addView(flex, new LinearLayout.LayoutParams(1, 0, 0.45f));
        TextView eyebrow = text("DESTACADO PARA VOS", 13, blue, true);
        content.addView(eyebrow);
        heroTitle = text("Tu próxima película", 34, white, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = dp(8);
        content.addView(heroTitle, titleParams);
        heroDescription = text("Fondos cinematográficos que se actualizan por internet.", 15, muted, false);
        heroDescription.setMaxLines(3);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(dp(570), -2); descParams.topMargin = dp(9);
        content.addView(heroDescription, descParams);

        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button refresh = button("↻  Cambiar destacado", true);
        refresh.setOnClickListener(v -> refreshFeatured());
        actions.addView(refresh, new LinearLayout.LayoutParams(-2, dp(50)));
        Button configure = button("Configurar catálogo", false);
        LinearLayout.LayoutParams configParams = new LinearLayout.LayoutParams(-2, dp(50)); configParams.leftMargin = dp(12);
        configure.setOnClickListener(v -> showSettings()); actions.addView(configure, configParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(58)); actionParams.topMargin = dp(14);
        content.addView(actions, actionParams);

        View gap = new View(this); content.addView(gap, new LinearLayout.LayoutParams(1, 0, 0.35f));
        TextView appsLabel = text("TUS APLICACIONES", 13, muted, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2); labelParams.bottomMargin = dp(9);
        content.addView(appsLabel, labelParams);
        HorizontalScrollView railScroll = new HorizontalScrollView(this);
        railScroll.setHorizontalScrollBarEnabled(false); railScroll.setClipToPadding(false);
        appRail = new LinearLayout(this); appRail.setOrientation(LinearLayout.HORIZONTAL);
        railScroll.addView(appRail, new HorizontalScrollView.LayoutParams(-2, -1));
        content.addView(railScroll, new LinearLayout.LayoutParams(-1, dp(106)));
        statusText = text("Fondos online · Android 9 · ARM 32 bits", 11, Color.rgb(160, 173, 195), false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2); statusParams.topMargin = dp(7);
        content.addView(statusText, statusParams);
        setContentView(root);
        refresh.setFocusable(true);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.rgb(10, 17, 29) : white); b.setPadding(dp(17), 0, dp(17), 0); b.setFocusable(true);
        GradientDrawable normal = new GradientDrawable(); normal.setCornerRadius(dp(13)); normal.setColor(primary ? 0xff82b8ff : 0xb8293854);
        GradientDrawable focused = new GradientDrawable(); focused.setCornerRadius(dp(13)); focused.setColor(primary ? 0xffb2d4ff : 0xff354b70); focused.setStroke(dp(2), 0xffdceaff);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[]{}, normal); b.setBackground(states);
        return b;
    }

    private void loadApps() {
        appRail.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        Collections.sort(apps, new Comparator<android.content.pm.ResolveInfo>() {
            @Override public int compare(android.content.pm.ResolveInfo a, android.content.pm.ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });
        for (android.content.pm.ResolveInfo info : apps) {
            if (info.activityInfo.packageName.equals(getPackageName())) continue;
            LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
            tile.setFocusable(true); tile.setClickable(true); tile.setPadding(dp(8), dp(7), dp(8), dp(7));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(cardColor); bg.setCornerRadius(dp(15)); tile.setBackground(bg);
            ImageView icon = new ImageView(this);
            try { Drawable d = info.loadIcon(pm); icon.setImageDrawable(d); } catch (Exception ignored) { }
            tile.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView name = text(String.valueOf(info.loadLabel(pm)), 11, white, true); name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(dp(94), -2); nameParams.topMargin = dp(4); tile.addView(name, nameParams);
            tile.setOnFocusChangeListener((v, focused) -> { v.setScaleX(focused ? 1.06f : 1f); v.setScaleY(focused ? 1.06f : 1f); v.setElevation(focused ? dp(8) : 0); });
            final String pkg = info.activityInfo.packageName; final String cls = info.activityInfo.name;
            tile.setOnClickListener(v -> {
                try { Intent launch = new Intent(Intent.ACTION_MAIN); launch.setClassName(pkg, cls); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); }
                catch (Exception e) { try { startActivity(pm.getLaunchIntentForPackage(pkg)); } catch (Exception ignored) { } }
            });
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(112), dp(100)); tileParams.rightMargin = dp(10); appRail.addView(tile, tileParams);
        }
        if (appRail.getChildCount() > 0) appRail.getChildAt(0).setNextFocusUpId(View.NO_ID);
    }

    private void refreshFeatured() {
        tmdbKey = prefs().getString("tmdb_key", "").trim();
        if (!tmdbKey.isEmpty()) {
            statusText.setText("Conectando al catálogo de películas y series…");
            executor.execute(() -> {
                try {
                    String encoded = URLEncoder.encode("es-ES", "UTF-8");
                    JSONObject json = getJson("https://api.themoviedb.org/3/trending/all/day?api_key=" + URLEncoder.encode(tmdbKey, "UTF-8") + "&language=" + encoded);
                    JSONArray results = json.optJSONArray("results");
                    if (results == null || results.length() == 0) throw new Exception("No hay títulos disponibles");
                    List<JSONObject> candidates = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.optJSONObject(i);
                        if (item != null && !item.optString("backdrop_path", "").isEmpty()) candidates.add(item);
                    }
                    if (candidates.isEmpty()) throw new Exception("No hay imágenes disponibles");
                    JSONObject item = candidates.get((int)(System.currentTimeMillis() % candidates.size()));
                    String title = item.optString("title", item.optString("name", "Contenido destacado"));
                    String overview = item.optString("overview", "Una selección de películas y series populares.");
                    String imageUrl = "https://image.tmdb.org/t/p/w1280" + item.optString("backdrop_path");
                    Bitmap bmp = getBitmap(imageUrl);
                    handler.post(() -> { if (bmp != null) heroImage.setImageBitmap(bmp); heroTitle.setText(title); heroDescription.setText(overview.isEmpty() ? "Descubrí algo nuevo para ver." : overview); statusText.setText("Catálogo online TMDB · se actualiza automáticamente"); });
                } catch (Exception e) { showFallback("No se pudo cargar TMDB; usando fondos online alternativos."); }
            });
        } else {
            showFallback("Fondos online · para catálogo de estrenos, configurá una clave gratuita de TMDB.");
        }
    }

    private void showFallback(String message) {
        final int index = fallbackIndex++ % fallbackImages.length;
        executor.execute(() -> {
            try {
                Bitmap bmp = getBitmap(fallbackImages[index]);
                handler.post(() -> { if (bmp != null) heroImage.setImageBitmap(bmp); heroTitle.setText(new String[]{"Una noche de cine", "Tu próxima aventura", "Historias para descubrir", "El espectáculo empieza acá", "Tu pantalla, tu mundo", "Un universo por explorar"}[index]); heroDescription.setText("Fondos cinematográficos online que cambian cuando elegís otro destacado."); statusText.setText(message); });
            } catch (Exception ignored) { handler.post(() -> statusText.setText("Sin conexión con las imágenes. Revisá internet y probá de nuevo.")); }
        });
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(9000); connection.setReadTimeout(9000); connection.setRequestMethod("GET");
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    private Bitmap getBitmap(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(12000); connection.setRequestProperty("User-Agent", "TVLauncherPG/1.1");
        try (InputStream in = connection.getInputStream()) { return BitmapFactory.decodeStream(in); }
        finally { connection.disconnect(); }
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView info = text("Con una clave gratuita de TMDB, TV Launcher PG puede mostrar películas y series populares con sus fondos reales. Si todavía no tenés clave, dejá el campo vacío para usar fondos cinematográficos online alternativos.", 14, white, false);
        box.addView(info);
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Clave API de TMDB (opcional)"); input.setText(tmdbKey); input.setTextColor(white); input.setHintTextColor(muted); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52)); p.topMargin = dp(12); box.addView(input, p);
        new AlertDialog.Builder(this).setTitle("Configurar catálogo online").setView(box)
            .setPositiveButton("Guardar", (d, w) -> { String value = input.getText().toString().trim(); prefs().edit().putString("tmdb_key", value).apply(); tmdbKey = value; refreshFeatured(); })
            .setNegativeButton("Cancelar", null).show();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSettings(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy(); }
}
