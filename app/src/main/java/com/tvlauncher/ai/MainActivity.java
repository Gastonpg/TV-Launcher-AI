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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
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
import android.util.LruCache;
import android.widget.TextClock;

public class MainActivity extends Activity {
    private final int white = Color.WHITE;
    private final int muted = Color.rgb(204, 213, 230);
    private int blue = Color.rgb(130, 184, 255);
    private final int cardColor = Color.argb(225, 24, 33, 51);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private ImageView heroImage;
    private TextView heroTitle, heroDescription, statusText;
    private LinearLayout appRail, movieRail, seriesRail;
    private String tmdbKey = "";
    private String selectedPackage = "";
    private String selectedAppName = "";
    private static final int REQUEST_BACKGROUND_IMAGE = 7401;
    private String customWallpaperUri = "";
    private final List<JSONObject> trending = new ArrayList<>();
    private int backdropToken = 0;
    private int fallbackIndex = 0;
    private final String[] fallbackImages = new String[] {
        "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?auto=format&fit=crop&w=1280&q=75",
        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1280&q=75",
        "https://images.unsplash.com/photo-1478720568477-152d9b164e26?auto=format&fit=crop&w=1280&q=75",
        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=1280&q=75",
        "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1280&q=75",
        "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?auto=format&fit=crop&w=1280&q=75"
    };

    private ScrollView pageScroll;
private LinearLayout forYouRail;
private TextView appsTitle, forYouTitle, moviesTitle, seriesTitle;
private TextView weatherText, regionText;
private String weatherCity = "Buenos Aires";
private volatile double weatherLat = Double.NaN, weatherLon = Double.NaN;
private Runnable pendingHero;
private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
    @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
};
private final Runnable weatherLoop = new Runnable() {
    @Override public void run() { loadWeather(); handler.postDelayed(this, 30 * 60 * 1000L); }
};

    private SharedPreferences prefs() { return getSharedPreferences("tvpg", MODE_PRIVATE); }

    @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    tmdbKey = prefs().getString("tmdb_key", "").trim();
    customWallpaperUri = prefs().getString("wallpaper_uri", "");
    weatherCity = prefs().getString("weather_city", "Buenos Aires");
    try {
        String savedLat = prefs().getString("weather_lat", "");
        String savedLon = prefs().getString("weather_lon", "");
        if (!savedLat.isEmpty() && !savedLon.isEmpty()) {
            weatherLat = Double.parseDouble(savedLat);
            weatherLon = Double.parseDouble(savedLon);
        }
    } catch (Exception ignored) { }
    buildUi();
    loadApps();
    refreshCatalog();
    if (!applyCustomWallpaper()) showFallback("Preparando TV Launcher PG…");
    handler.postDelayed(weatherLoop, 800);
    handler.postDelayed(new Runnable() {
        @Override public void run() {
            if (trending.isEmpty()) showFallback("Fondos online · configurá TMDB para cargar catálogos reales.");
            handler.postDelayed(this, 60 * 1000L);
        }
    }, 60 * 1000L);
}

    private void buildUi() {
        // Pantalla reconstruida desde cero siguiendo la composición de Google TV clásico:
        // navegación en una sola franja, fondo cinematográfico a pantalla completa,
        // información destacada a la izquierda y carruseles en la mitad inferior.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff080b12);

        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackgroundColor(0xff101725);
        root.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));

        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;

        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE9080B12, 0x55080B12, 0x00080B12}));
        root.addView(topShade, new FrameLayout.LayoutParams(-1, dp(150), Gravity.TOP));

        View leftShade = new View(this);
        leftShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE8080B12, 0xA6080B12, 0x38080B12, 0x00080B12}));
        root.addView(leftShade, new FrameLayout.LayoutParams(sw * 8 / 10, sh * 7 / 10, Gravity.LEFT | Gravity.TOP));

        View lowerShade = new View(this);
        lowerShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00080B12, 0xA9080B12, 0xF7080B12}));
        root.addView(lowerShade, new FrameLayout.LayoutParams(-1, sh * 5 / 10, Gravity.BOTTOM));

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(42), dp(12), dp(42), dp(4));
        screen.setClipChildren(false);
        screen.setClipToPadding(false);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));

        // ETAPA 1: cabecera. No modificar carruseles ni el área de fondo en esta etapa.
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setClipChildren(false);
        nav.setClipToPadding(false);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setFillViewport(false);
        navScroll.setClipChildren(false);
        navScroll.setClipToPadding(false);
        navScroll.setFocusable(false);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setClipChildren(false);
        tabs.setClipToPadding(false);

        String[] tabNames = {"Para vos", "Películas", "Series", "Apps", "Biblioteca"};
        final List<TextView> tabViews = new ArrayList<>();
        final int[] selectedTab = new int[]{0};
        final int selectedText = 0xff17191e;
        final int idleText = 0xffe5e8ef;
        for (int i = 0; i < tabNames.length; i++) {
            final int index = i;
            TextView tab = text(tabNames[i], 14, i == 0 ? selectedText : idleText, i == 0);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(15), 0, dp(15), 0);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setMinWidth(dp(62));
            tab.setTag("google_tv_nav_tab");
            tab.setBackground(googleTvNavBackground(i == 0));
            tab.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    tab.setTextColor(selectedText);
                    tab.setBackground(googleTvNavBackground(true));
                    tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                } else if (tabViews.indexOf(tab) != selectedTab[0]) {
                    tab.setTextColor(idleText);
                    tab.setBackground(googleTvNavBackground(false));
                    tab.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                }
            });
            tab.setOnClickListener(v -> {
                selectedTab[0] = index;
                for (int k = 0; k < tabViews.size(); k++) {
                    TextView item = tabViews.get(k);
                    boolean active = k == index;
                    item.setBackground(googleTvNavBackground(active));
                    item.setTextColor(active ? selectedText : idleText);
                    item.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
                }
                if (index == 0) pageScroll.post(() -> pageScroll.smoothScrollTo(0, 0));
                else if (index == 1) scrollToView(moviesTitle);
                else if (index == 2) scrollToView(seriesTitle);
                else if (index == 3) scrollToView(appsTitle);
                else scrollToView(forYouTitle);
            });
            tabViews.add(tab);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, dp(38));
            tp.rightMargin = dp(5);
            tabs.addView(tab, tp);
        }
        navScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
        nav.addView(navScroll, new LinearLayout.LayoutParams(0, dp(54), 1f));

        // Se conserva el widget translúcido pedido; la marca y el engranaje quedan juntos al extremo derecho.
        View weather = buildWeatherWidget();
        weather.setPadding(dp(10), dp(3), dp(11), dp(3));
        LinearLayout.LayoutParams weatherLp = new LinearLayout.LayoutParams(-2, dp(42));
        weatherLp.rightMargin = dp(8);
        nav.addView(weather, weatherLp);

        TextView logo = text("TV LAUNCHER PG", 13, white, true);
        logo.setGravity(Gravity.CENTER);
        logo.setTag("pg_logo");
        logo.setPadding(dp(10), 0, dp(10), 0);
        nav.addView(logo, new LinearLayout.LayoutParams(-2, dp(44)));

        Button settings = button("⚙", false);
        settings.setTextSize(20);
        settings.setContentDescription("Ajustes del dispositivo");
        settings.setOnClickListener(v -> openSystemSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(44), dp(42));
        settingsLp.leftMargin = dp(2);
        nav.addView(settings, settingsLp);

        screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(54)));

        // Área de contenido destacado: el fondo cinematográfico cambia al mover el foco por TMDB.
        LinearLayout featured = new LinearLayout(this);
        featured.setOrientation(LinearLayout.VERTICAL);
        featured.setGravity(Gravity.CENTER_VERTICAL);
        featured.setPadding(dp(4), 0, 0, dp(2));

        TextView featuredLabel = text("RECOMENDADO", 12, blue, true);
        featuredLabel.setLetterSpacing(0.08f);
        featured.addView(featuredLabel, new LinearLayout.LayoutParams(-1, -2));

        heroTitle = text("Descubrí qué mirar", 32, white, true);
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams htp = new LinearLayout.LayoutParams(dp(650), -2);
        htp.topMargin = dp(7);
        featured.addView(heroTitle, htp);

        heroDescription = text("Películas y series populares, en una sola pantalla.", 14, 0xffe0e5ef, false);
        heroDescription.setMaxLines(2);
        heroDescription.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hdp = new LinearLayout.LayoutParams(dp(540), -2);
        hdp.topMargin = dp(5);
        featured.addView(heroDescription, hdp);

        LinearLayout featureActions = new LinearLayout(this);
        featureActions.setGravity(Gravity.CENTER_VERTICAL);
        Button change = button("↻ Cambiar destacado", true);
        change.setOnClickListener(v -> changeFeatured());
        featureActions.addView(change, new LinearLayout.LayoutParams(-2, dp(40)));
        Button details = button("Más información", false);
        details.setOnClickListener(v -> showHeroInfo());
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-2, dp(40));
        detailLp.leftMargin = dp(8);
        featureActions.addView(details, detailLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(9);
        featured.addView(featureActions, actionsLp);

        screen.addView(featured, new LinearLayout.LayoutParams(-1, dp(205)));

        pageScroll = new ScrollView(this);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setClipChildren(false);
        pageScroll.setClipToPadding(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        pageScroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        screen.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // El primer carrusel siempre es el catálogo cinematográfico; apps debajo, como se pidió.
        forYouTitle = addSectionHeader(rows, "DESTACADOS PARA VOS");
        forYouRail = addRail(rows, 170);
        appsTitle = addSectionHeader(rows, "TUS APLICACIONES");
        appRail = addRail(rows, 116);
        moviesTitle = addSectionHeader(rows, "PELÍCULAS POPULARES");
        movieRail = addRail(rows, 210);
        seriesTitle = addSectionHeader(rows, "SERIES POPULARES");
        seriesRail = addRail(rows, 210);

        statusText = text("TV Launcher PG  ·  Catálogo TMDB", 10, 0xffaeb8c9, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(7);
        statusLp.leftMargin = dp(6);
        rows.addView(statusText, statusLp);

        setContentView(root);
    }


    private Drawable googleTvNavBackground(boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? 0xfff1f3f7 : Color.TRANSPARENT);
        bg.setCornerRadius(dp(22));
        if (selected) bg.setStroke(dp(1), 0xfff1f3f7);
        return bg;
    }

    private void addSectionTitle(LinearLayout content, String title) {
        TextView label = text(title, 13, muted, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(22); lp.bottomMargin = dp(9); content.addView(label, lp);
    }

    private HorizontalScrollView railScroll() {
        HorizontalScrollView h = new HorizontalScrollView(this); h.setHorizontalScrollBarEnabled(false); h.setClipToPadding(false); h.setFocusable(false); return h;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.rgb(10,17,29) : white); b.setPadding(dp(13),0,dp(13),0); b.setFocusable(true);
        b.setTag(primary ? "primary_button" : "secondary_button");
        b.setBackground(buttonGlassBackground(primary)); return b;
    }

    private void loadApps() {
        appRail.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        Collections.sort(apps, new Comparator<android.content.pm.ResolveInfo>() {
            @Override public int compare(android.content.pm.ResolveInfo a, android.content.pm.ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        for (android.content.pm.ResolveInfo info : apps) {
            if (info.activityInfo.packageName.equals(getPackageName())) continue;
            final String pkg = info.activityInfo.packageName;
            final String cls = info.activityInfo.name;
            final String name = String.valueOf(info.loadLabel(pm));

            // App tiles: horizontal rounded rectangles, not circular medallions.
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setClipChildren(false);
            tile.setClipToPadding(false);
            tile.setPadding(dp(4), dp(4), dp(4), dp(3));

            FrameLayout tileFace = new FrameLayout(this);
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(0xAA202633);
            normal.setCornerRadius(dp(12));
            normal.setStroke(dp(1), 0x33ffffff);

            GradientDrawable focused = new GradientDrawable();
            focused.setColor(0xF0E8EDF5);
            focused.setCornerRadius(dp(12));
            focused.setStroke(dp(2), blue);

            StateListDrawable faceStates = new StateListDrawable();
            faceStates.addState(new int[]{android.R.attr.state_focused}, focused);
            faceStates.addState(new int[]{}, normal);
            tileFace.setBackground(faceStates);
            tileFace.setClipToOutline(true);

            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try { icon.setImageDrawable(info.loadIcon(pm)); } catch (Exception ignored) { }
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
            tileFace.addView(icon, iconParams);
            tile.addView(tileFace, new LinearLayout.LayoutParams(dp(112), dp(70)));

            TextView label = text(name, 12, white, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(112), dp(24));
            labelParams.topMargin = dp(5);
            tile.addView(label, labelParams);

            tile.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().cancel();
                v.animate().scaleX(hasFocus ? 1.06f : 1f).scaleY(hasFocus ? 1.06f : 1f)
                        .translationY(hasFocus ? -dp(3) : 0f)
                        .setDuration(hasFocus ? 140 : 100).start();
                label.setTextColor(hasFocus ? 0xffffffff : 0xffd5d9e2);
                if (hasFocus) selectApp(pkg, name);
            });
            tile.setOnClickListener(v -> launchApp(pkg, cls));

            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(120), dp(108));
            item.rightMargin = dp(8);
            appRail.addView(tile, item);
        }
        if (apps.isEmpty()) statusText.setText("No se encontraron aplicaciones iniciables.");
    }

    private void selectApp(String pkg, String name) {
    selectedPackage = pkg;
    selectedAppName = name;
    heroTitle.setText(name);
    heroDescription.setText("Seleccionaste " + name + ". Explorá las recomendaciones o presioná OK para abrir la aplicación.");
    if (hasCustomWallpaper()) return;
    String path = "";
    synchronized (trending) {
        if (!trending.isEmpty()) {
            path = jsonStr(trending.get(Math.abs(pkg.hashCode()) % trending.size()), "backdrop_path");
        }
    }
    setBackdropDebounced(path);
}

    private void loadBackdropForApp(String pkg) {
        if (hasCustomWallpaper()) return;
        int index = Math.abs(pkg.hashCode()) % trending.size();
        JSONObject item = trending.get(index);
        String path = item.optString("backdrop_path", "");
        if (path.isEmpty()) { showFallback("Recomendaciones para " + selectedAppName); return; }
        final int token = ++backdropToken;
        final String title = item.optString("title", item.optString("name", "Contenido destacado"));
        executor.execute(() -> {
            try {
                Bitmap bmp = getBitmap("https://image.tmdb.org/t/p/w1280" + path);
                handler.post(() -> { if (bmp != null && token == backdropToken) applyHeroBitmap(bmp); });
            } catch (Exception ignored) { }
        });
    }

    private void refreshCatalog() {
    tmdbKey = prefs().getString("tmdb_key", "").trim();
    if (tmdbKey.isEmpty()) {
        statusText.setText("Catálogos online: configurá tu clave gratuita de TMDB.");
        forYouRail.removeAllViews();
        movieRail.removeAllViews();
        seriesRail.removeAllViews();
        addCatalogPlaceholder(forYouRail, "Configurá TMDB para ver recomendaciones");
        addCatalogPlaceholder(movieRail, "Configurá TMDB para ver películas");
        addCatalogPlaceholder(seriesRail, "Configurá TMDB para ver series");
        return;
    }
    statusText.setText("Cargando catálogos de películas y series…");
    executor.execute(() -> {
        try {
            JSONObject trendJson = getJson("https://api.themoviedb.org/3/trending/all/day?api_key=" + enc(tmdbKey) + "&language=es-ES");
            JSONArray t = trendJson.optJSONArray("results");
            synchronized (trending) {
                trending.clear();
                if (t != null) for (int i = 0; i < t.length(); i++) {
                    JSONObject o = t.optJSONObject(i);
                    if (o != null && !jsonStr(o, "backdrop_path").isEmpty()) trending.add(o);
                }
            }
            JSONObject movies = getJson("https://api.themoviedb.org/3/movie/popular?api_key=" + enc(tmdbKey) + "&language=es-ES&page=1");
            JSONObject shows = getJson("https://api.themoviedb.org/3/tv/popular?api_key=" + enc(tmdbKey) + "&language=es-ES&page=1");
            JSONArray ma = movies.optJSONArray("results"), sa = shows.optJSONArray("results");
            handler.post(() -> {
                forYouRail.removeAllViews();
                movieRail.removeAllViews();
                seriesRail.removeAllViews();
                fillForYou();
                addMediaCards(movieRail, ma, false, 300);
                addMediaCards(seriesRail, sa, true, 900);
                statusText.setText("Catálogos online · TMDB · los enlaces de reproducción dependen de cada aplicación.");
                if (selectedAppName.isEmpty()) {
                    JSONObject first = null;
                    synchronized (trending) { if (!trending.isEmpty()) first = trending.get(0); }
                    if (first != null) setHeroDebounced(mediaTitle(first), jsonStr(first, "overview"), jsonStr(first, "backdrop_path"));
                }
            });
        } catch (Exception e) {
            handler.post(() -> {
                statusText.setText("No se pudo cargar TMDB. Revisá la clave y la conexión.");
                addCatalogPlaceholder(forYouRail, "No se pudo cargar el catálogo");
                addCatalogPlaceholder(movieRail, "No se pudo cargar el catálogo");
                addCatalogPlaceholder(seriesRail, "No se pudo cargar el catálogo");
            });
        }
    });
}

    private String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private void addCatalogPlaceholder(LinearLayout rail, String message) {
        if (rail.getChildCount() > 0) return;
        TextView placeholder = text(message, 13, muted, false); placeholder.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = glassDrawable(0x77202d43, 0x66ffffff, 12); placeholder.setBackground(bg); placeholder.setPadding(dp(16),0,dp(16),0);
        rail.addView(placeholder, new LinearLayout.LayoutParams(dp(280),dp(155)));
    }

    private void addMediaCards(LinearLayout rail, JSONArray array, boolean tv) {
    addMediaCards(rail, array, tv, 0);
}

    private void addMediaCards(LinearLayout rail, JSONArray array, boolean tv, long delayMs) {
        if (array == null || array.length() == 0) {
            addCatalogPlaceholder(rail, "No hay títulos disponibles");
            return;
        }
        int limit = Math.min(array.length(), 12);
        int shown = 0;
        for (int i = 0; i < limit; i++) {
            final JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String imagePath = jsonStr(item, "backdrop_path");
            if (imagePath.isEmpty()) imagePath = jsonStr(item, "poster_path");
            if (imagePath.isEmpty()) continue;
            final String imageUrl = "https://image.tmdb.org/t/p/w500" + imagePath;
            final String title = mediaTitle(item);
            final String overview = jsonStr(item, "overview");
            final String backdrop = jsonStr(item, "backdrop_path");

            FrameLayout card = new FrameLayout(this);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(mediaCardBackground());
            card.setClipToOutline(true);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xff202632);
            card.addView(image, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x18000000, 0xe6000000}));
            card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            TextView label = text(title, 13, Color.WHITE, true);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setShadowLayer(3f, 0f, 1f, Color.BLACK);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            labelParams.leftMargin = dp(12);
            labelParams.rightMargin = dp(10);
            labelParams.bottomMargin = dp(10);
            card.addView(label, labelParams);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(238), dp(134));
            cardParams.rightMargin = dp(12);
            rail.addView(card, cardParams);
            loadImageInto(image, imageUrl, delayMs + shown * 65L);
            shown++;
            card.setOnFocusChangeListener((v, focused) -> {
                animateCardFocus(v, focused);
                if (focused) setHeroDebounced(title,
                        overview.isEmpty() ? "Explorá este título en tus aplicaciones instaladas." : overview,
                        backdrop.isEmpty() ? jsonStr(item, "poster_path") : backdrop);
            });
            card.setOnClickListener(v -> showMediaDetails(title, overview, tv));
        }
        if (shown == 0) addCatalogPlaceholder(rail, "No hay títulos disponibles");
    }

    private void showMediaDetails(String title, String overview, boolean tv) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title).setMessage((tv?"Serie":"Película") + "\n\n" + (overview.isEmpty()?"Sin descripción disponible.":overview));
        if (!selectedPackage.isEmpty()) builder.setPositiveButton("Abrir " + selectedAppName, (d,w) -> launchApp(selectedPackage, null));
        builder.setNeutralButton("Buscar en YouTube", (d,w) -> {
            try { String q=URLEncoder.encode(title,"UTF-8"); Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query="+q)); startActivity(i); }
            catch(Exception e){ Toast.makeText(this,"No se pudo abrir la búsqueda.",Toast.LENGTH_SHORT).show(); }
        });
        builder.setNegativeButton("Cerrar",null).show();
    }

    private void launchApp(String pkg, String cls) {
        try {
            Intent launch;
            if (cls != null) { launch = new Intent(Intent.ACTION_MAIN); launch.setClassName(pkg,cls); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }
            else launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) { Toast.makeText(this,"No se pudo abrir esa aplicación.",Toast.LENGTH_SHORT).show(); return; }
            startActivity(launch);
        } catch(Exception e) { Toast.makeText(this,"El sistema no permitió abrir la aplicación.",Toast.LENGTH_SHORT).show(); }
    }

    private void changeFeatured() {
        if (hasCustomWallpaper()) {
            if (!trending.isEmpty()) {
                int index = (int)(System.currentTimeMillis() % trending.size());
                JSONObject item = trending.get(index);
                heroTitle.setText(item.optString("title", item.optString("name", "Contenido destacado")));
                heroDescription.setText(item.optString("overview", "Una recomendación para descubrir."));
            } else {
                fallbackIndex++;
                heroTitle.setText(new String[]{"Una noche de cine", "Tu próxima aventura", "Historias para descubrir", "El espectáculo empieza acá", "Tu pantalla, tu mundo", "Un universo por explorar"}[fallbackIndex % fallbackImages.length]);
                heroDescription.setText("Fondo propio activado · elegí fondos automáticos desde Personalizar.");
            }
            statusText.setText("Fondo personalizado fijo");
            return;
        }
        if (!trending.isEmpty()) {
            int index = (int)(System.currentTimeMillis() % trending.size()); JSONObject item = trending.get(index);
            heroTitle.setText(item.optString("title",item.optString("name","Contenido destacado")));
            heroDescription.setText(item.optString("overview","Una recomendación para descubrir."));
            String path=item.optString("backdrop_path","");
            if(!path.isEmpty()) { final int token=++backdropToken; executor.execute(() -> { try { Bitmap bmp=getBitmap("https://image.tmdb.org/t/p/w1280"+path); handler.post(() -> {if(bmp!=null && token==backdropToken) applyHeroBitmap(bmp);}); }catch(Exception ignored){} }); }
        } else showFallback("Fondos cinematográficos online · configurá TMDB para catálogos reales.");
    }

    private void showFallback(String message) {
        if (applyCustomWallpaper()) { statusText.setText("Fondo personalizado fijo"); return; }
        final int index = fallbackIndex++ % fallbackImages.length; final int token=++backdropToken;
        executor.execute(() -> { try { Bitmap bmp=getBitmap(fallbackImages[index]); handler.post(() -> { if(bmp!=null && token==backdropToken) applyHeroBitmap(bmp); if(selectedAppName.isEmpty()){ heroTitle.setText(new String[]{"Una noche de cine","Tu próxima aventura","Historias para descubrir","El espectáculo empieza acá","Tu pantalla, tu mundo","Un universo por explorar"}[index]); heroDescription.setText("Fondos cinematográficos online que cambian al elegir otro destacado."); } statusText.setText(message); }); } catch(Exception ignored) { handler.post(() -> statusText.setText("Sin conexión con las imágenes. Revisá internet.")); } });
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(urlText).openConnection(); connection.setConnectTimeout(9000); connection.setReadTimeout(9000); connection.setRequestMethod("GET");
        try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))>0) out.write(buffer,0,n); return new JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8)); }
        finally { connection.disconnect(); }
    }

    private Bitmap getBitmap(String urlText) throws Exception {
        if (urlText == null || urlText.isEmpty()) return null;
        byte[] data;
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "TVLauncherPG/2.0");
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > 6 * 1024 * 1024) throw new java.io.IOException("Imagen demasiado grande");
                out.write(buffer, 0, n);
            }
            data = out.toByteArray();
        } finally {
            connection.disconnect();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private boolean hasCustomWallpaper() {
        return customWallpaperUri != null && !customWallpaperUri.trim().isEmpty();
    }

    private boolean applyCustomWallpaper() {
        if (!hasCustomWallpaper() || heroImage == null) return false;
        try {
            Bitmap bmp = decodeUriScaled(Uri.parse(customWallpaperUri));
            if (bmp != null) {
                heroImage.setImageBitmap(bmp);
                updateAccentFromBitmap(bmp);
                statusTextIfReady("Fondo personalizado fijo");
                return true;
            }
        } catch (Exception ignored) { }
        customWallpaperUri = "";
        prefs().edit().remove("wallpaper_uri").apply();
        return false;
    }

    private Bitmap decodeUriScaled(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private void statusTextIfReady(String value) {
        if (statusText != null) statusText.setText(value);
    }

    private void showPersonalizationMenu() {
    String[] options = new String[] {
        "Elegir imagen de la TV Box",
        "Usar fondos cinematográficos automáticos",
        "Configurar catálogo de películas y series (TMDB)",
        "Establecer como launcher predeterminado",
        "Región del clima y reloj"
    };
    new AlertDialog.Builder(this)
        .setTitle("Personalizar TV Launcher PG")
        .setItems(options, (dialog, which) -> {
            if (which == 0) chooseWallpaper();
            else if (which == 1) useAutomaticWallpapers();
            else if (which == 2) showCatalogSettings();
            else if (which == 3) openHomeSettings();
            else showWeatherCityDialog();
        })
        .setNegativeButton("Cerrar", null)
        .show();
}

    private Drawable buttonGlassBackground(boolean primary) {
        GradientDrawable normal = glassDrawable(primary ? blendColor(blue, 0xff152236, 0.58f) : 0x66232f45, 0x66ffffff, 13);
        GradientDrawable focused = glassDrawable(blendColor(blue, 0xff17283e, 0.28f), 0xffdceaff, 13);
        focused.setStroke(dp(2), blue);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void applyHeroBitmap(Bitmap bitmap) {
        if (bitmap == null || heroImage == null) return;
        heroImage.setImageBitmap(bitmap);
        updateAccentFromBitmap(bitmap);
    }

    // Extract a vivid but soft accent from the current wallpaper; no manual palette needed.
    private void updateAccentFromBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        try {
            Bitmap small = Bitmap.createScaledBitmap(bitmap, 32, 32, true);
            int[] pixels = new int[32 * 32];
            small.getPixels(pixels, 0, 32, 0, 0, 32, 32);
            float bestScore = -1f, chosenHue = 210f;
            for (int pixel : pixels) {
                float[] hsv = new float[3];
                Color.colorToHSV(pixel, hsv);
                float saturation = hsv[1], value = hsv[2];
                if (value < 0.24f || value > 0.96f || saturation < 0.18f) continue;
                float score = saturation * (1f - Math.abs(value - 0.62f));
                if (score > bestScore) { bestScore = score; chosenHue = hsv[0]; }
            }
            if (small != bitmap) small.recycle();
            float[] accent = new float[]{chosenHue, bestScore < 0 ? 0.28f : 0.52f, 0.98f};
            int extracted = Color.HSVToColor(accent);
            if (Math.abs(Color.red(blue) - Color.red(extracted))
                    + Math.abs(Color.green(blue) - Color.green(extracted))
                    + Math.abs(Color.blue(blue) - Color.blue(extracted)) < 60) return;
            blue = extracted;
            refreshAccentViews(getWindow().getDecorView());
        } catch (Exception ignored) { }
    }

    private void refreshAccentViews(View view) {
        Object tag = view.getTag();
        if ("accent_label".equals(tag) && view instanceof TextView) ((TextView) view).setTextColor(blue);
        else if ("pg_logo".equals(tag) && view instanceof TextView) {
            GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff17233a, blendColor(blue, Color.BLACK, 0.55f)});
            logoBg.setCornerRadius(dp(13)); logoBg.setStroke(dp(2), blue); view.setBackground(logoBg);
        } else if ("primary_button".equals(tag) && view instanceof Button) ((Button) view).setBackground(buttonGlassBackground(true));
        else if ("secondary_button".equals(tag) && view instanceof Button) ((Button) view).setBackground(buttonGlassBackground(false));
        else if ("accent_tab".equals(tag) && view instanceof TextView) view.setBackground(tabBackground());
        else if ("app_card".equals(tag)) {
            GradientDrawable normalCard = glassDrawable(0x55202d43, 0x66ffffff, 15);
            GradientDrawable focusedCard = glassDrawable(blendColor(blue, 0xff20334c, 0.48f), 0xffd9ebff, 15);
            focusedCard.setStroke(dp(2), blue);
            StateListDrawable states = new StateListDrawable(); states.addState(new int[]{android.R.attr.state_focused}, focusedCard); states.addState(new int[]{}, normalCard);
            view.setBackground(states);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) refreshAccentViews(group.getChildAt(i));
        }
    }

    // Translucent gradient + fine light border: lightweight Liquid Glass styling for Android 9.
    // It intentionally avoids real-time backdrop blur to keep older TV Boxes responsive.
    private GradientDrawable glassDrawable(int tint, int stroke, float radiusDp) {
        int top = blendWithAlpha(Color.WHITE, tint, 0.16f, 0x35);
        int middle = tint;
        int bottom = blendWithAlpha(Color.BLACK, tint, 0.08f, 0x58);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, middle, bottom});
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int blendWithAlpha(int overlay, int base, float ratio, int alpha) {
        int rgb = blendColor(overlay, base, ratio);
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00ffffff);
    }

    private int blendColor(int foreground, int background, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int red = Math.round(Color.red(foreground) * (1f-r) + Color.red(background) * r);
        int green = Math.round(Color.green(foreground) * (1f-r) + Color.green(background) * r);
        int blueChannel = Math.round(Color.blue(foreground) * (1f-r) + Color.blue(background) * r);
        return Color.rgb(red, green, blueChannel);
    }

    private void chooseWallpaper() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_BACKGROUND_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "Este sistema no permite elegir imágenes desde el almacenamiento.", Toast.LENGTH_LONG).show();
        }
    }

    private void useAutomaticWallpapers() {
        customWallpaperUri = "";
        prefs().edit().remove("wallpaper_uri").apply();
        heroImage.setImageDrawable(null);
        changeFeatured();
        Toast.makeText(this, "Fondos cinematográficos automáticos activados", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_BACKGROUND_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) { }
        customWallpaperUri = uri.toString();
        prefs().edit().putString("wallpaper_uri", customWallpaperUri).apply();
        if (applyCustomWallpaper()) {
            heroTitle.setText("Tu pantalla, tu mundo");
            heroDescription.setText("Fondo personalizado activado. Volvé a Personalizar para usar fondos automáticos.");
            Toast.makeText(this, "Fondo personalizado guardado", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No se pudo abrir esa imagen. Probá con otra.", Toast.LENGTH_LONG).show();
        }
    }

    private void openHomeSettings() {
        // Some Android TV Box firmwares do not implement ACTION_HOME_SETTINGS.
        // Show useful alternatives instead of silently doing nothing.
        new AlertDialog.Builder(this)
            .setTitle("Establecer launcher predeterminado")
            .setMessage("Android debe permitir elegir la aplicación de inicio. En algunas TV Box esta pantalla está oculta por el fabricante. Probá estas opciones; si ninguna muestra el selector, revisaremos una alternativa mediante ADB.")
            .setPositiveButton("Aplicaciones predeterminadas", (dialog, which) -> launchSettingsAction(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                "Buscá Aplicación de inicio o Home app."))
            .setNeutralButton("Configuración de inicio", (dialog, which) -> launchSettingsAction(Settings.ACTION_HOME_SETTINGS,
                "Buscá TV Launcher PG en la lista de aplicaciones de inicio."))
            .setNegativeButton("Ajustes generales", (dialog, which) -> launchSettingsAction(Settings.ACTION_SETTINGS,
                "Entrá en Aplicaciones y buscá la aplicación de inicio."))
            .show();
    }

    private void launchSettingsAction(String action, String guidance) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(this, guidance, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ignored) { }
        Toast.makeText(this, "Esta pantalla no está disponible en el firmware. Probá otra opción o avisame y revisamos ADB.", Toast.LENGTH_LONG).show();
    }

    private void showCatalogSettings() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),0);
        TextView info=text("La clave gratuita de TMDB permite mostrar portadas, títulos y sinopsis de películas y series. No reproduce el contenido ni extrae automáticamente los catálogos privados de todas las apps.",14,white,false); box.addView(info);
        EditText input=new EditText(this); input.setSingleLine(true); input.setHint("Clave API de TMDB"); input.setText(tmdbKey); input.setTextColor(white); input.setHintTextColor(muted); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52)); p.topMargin=dp(12); box.addView(input,p);
        new AlertDialog.Builder(this).setTitle("Catálogos y recomendaciones").setView(box)
            .setPositiveButton("Guardar y cargar",(d,w)->{tmdbKey=input.getText().toString().trim(); prefs().edit().putString("tmdb_key",tmdbKey).apply(); movieRail.removeAllViews(); seriesRail.removeAllViews(); refreshCatalog(); if(!tmdbKey.isEmpty()) Toast.makeText(this,"Cargando catálogo…",Toast.LENGTH_SHORT).show();})
            .setNeutralButton("Cómo conseguir la clave",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.themoviedb.org/settings/api")));}catch(Exception ignored){}})
            .setNegativeButton("Cerrar",null).show();
    }

    private void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch(Exception e) { try { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); } catch(Exception ignored) { Toast.makeText(this,"Este firmware no permite abrir los ajustes.",Toast.LENGTH_LONG).show(); } }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_MENU || keyCode==KeyEvent.KEYCODE_SETTINGS) { showCatalogSettings(); return true; }
        return super.onKeyDown(keyCode,event);
    }

    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy(); }

    private TextView addSectionHeader(LinearLayout parent, String title) {
    TextView label = text(title, 13, muted, true);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(14);
    lp.leftMargin = dp(6);
    parent.addView(label, lp);
    return label;
}

    private LinearLayout addRail(LinearLayout parent, int heightDp) {
    HorizontalScrollView scroll = railScroll();
    scroll.setClipChildren(false);
    scroll.setClipToPadding(false);
    LinearLayout rail = new LinearLayout(this);
    rail.setOrientation(LinearLayout.HORIZONTAL);
    rail.setClipChildren(false);
    rail.setClipToPadding(false);
    rail.setPadding(dp(6), dp(8), dp(40), dp(8));
    scroll.addView(rail, new HorizontalScrollView.LayoutParams(-2, -1));
    parent.addView(scroll, new LinearLayout.LayoutParams(-1, dp(heightDp)));
    return rail;
}

    private TextView makeTab(String label, View.OnClickListener click) {
    final TextView t = text(label, 14, white, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), 0, dp(16), 0);
    t.setFocusable(true);
    t.setClickable(true);
    t.setTag("accent_tab");
    t.setBackground(tabBackground());
    t.setOnClickListener(click);
    t.setOnFocusChangeListener((v, focused) -> t.setTextColor(focused ? blue : white));
    return t;
}

    private Drawable tabBackground() {
    GradientDrawable focused = glassDrawable(blendColor(blue, 0xff17283e, 0.28f), 0xffdceaff, 18);
    focused.setStroke(dp(2), blue);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_focused}, focused);
    states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    return states;
}

    private Drawable mediaCardBackground() {
    GradientDrawable normal = glassDrawable(0x88202d43, 0x66ffffff, 10);
    GradientDrawable focused = glassDrawable(blendColor(blue, 0xff263f68, 0.35f), 0xffe4f1ff, 10);
    focused.setStroke(dp(2), blue);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_focused}, focused);
    states.addState(new int[]{}, normal);
    return states;
}

    private void animateCardFocus(View v, boolean focused) {
    v.animate().cancel();
    v.animate().scaleX(focused ? 1.07f : 1f).scaleY(focused ? 1.07f : 1f)
            .setDuration(focused ? 130 : 100).start();
}

    private void scrollToView(final View target) {
    if (pageScroll == null || target == null) return;
    pageScroll.post(() -> pageScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(8))));
}

    private void showHeroInfo() {
    new AlertDialog.Builder(this)
        .setTitle(heroTitle.getText())
        .setMessage(heroDescription.getText())
        .setNegativeButton("Cerrar", null)
        .show();
}

    private void setHeroDebounced(String title, String description, String backdropPath) {
    heroTitle.setText(title);
    heroDescription.setText(description);
    setBackdropDebounced(backdropPath);
}

    private void setBackdropDebounced(final String backdropPath) {
    if (hasCustomWallpaper() || backdropPath == null || backdropPath.isEmpty()) return;
    if (pendingHero != null) handler.removeCallbacks(pendingHero);
    final int token = ++backdropToken;
    pendingHero = () -> executor.execute(() -> {
        try {
            final Bitmap bmp = getBitmapCached("https://image.tmdb.org/t/p/w1280" + backdropPath);
            handler.post(() -> { if (bmp != null && token == backdropToken) applyHeroBitmap(bmp); });
        } catch (Exception ignored) { }
    });
    handler.postDelayed(pendingHero, 220);
}

    private Bitmap getBitmapCached(String url) throws Exception {
    Bitmap hit = imageCache.get(url);
    if (hit != null && !hit.isRecycled()) return hit;
    Bitmap bmp = getBitmap(url);
    if (bmp != null) imageCache.put(url, bmp);
    return bmp;
}

    private void loadImageInto(final ImageView target, final String url, long delayMs) {
    target.setTag(url);
    handler.postDelayed(() -> executor.execute(() -> {
        try {
            final Bitmap bmp = getBitmapCached(url);
            handler.post(() -> { if (bmp != null && url.equals(target.getTag())) target.setImageBitmap(bmp); });
        } catch (Exception ignored) { }
    }), delayMs);
}

    private String jsonStr(JSONObject o, String key) {
    return (o == null || o.isNull(key)) ? "" : o.optString(key, "");
}

    private String mediaTitle(JSONObject item) {
    String t = jsonStr(item, "title");
    if (t.isEmpty()) t = jsonStr(item, "name");
    return t.isEmpty() ? "Sin título" : t;
}

    private void fillForYou() {
    forYouRail.removeAllViews();
    List<JSONObject> copy;
    synchronized (trending) { copy = new ArrayList<>(trending); }
    if (copy.isEmpty()) { addCatalogPlaceholder(forYouRail, "Sin recomendaciones por ahora"); return; }
    addLandscapeCards(forYouRail, copy);
}

    private void addLandscapeCards(LinearLayout rail, List<JSONObject> items) {
    int shown = 0;
    for (int i = 0; i < items.size() && shown < 10; i++) {
        final JSONObject item = items.get(i);
        final String backdrop = jsonStr(item, "backdrop_path");
        if (backdrop.isEmpty()) continue;
        final String title = mediaTitle(item);
        final String overview = jsonStr(item, "overview");
        final boolean tv = "tv".equals(jsonStr(item, "media_type"));

        // Tarjeta cinematográfica ancha: el arte ocupa toda la superficie.
        // El marco exterior deja espacio para la ampliación del foco del mando.
        FrameLayout outer = new FrameLayout(this);
        outer.setFocusable(true);
        outer.setClickable(true);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);

        FrameLayout card = new FrameLayout(this);
        card.setBackground(mediaCardBackground());
        card.setClipToOutline(true);
        outer.addView(card, new FrameLayout.LayoutParams(dp(252), dp(142), Gravity.CENTER));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xff202633);
        card.addView(image, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x08000000, 0xD9000000}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView label = text(title, 13, white, true);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        label.setPadding(dp(12), 0, dp(10), dp(10));
        card.addView(label, new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM));

        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(dp(260), dp(150));
        outerParams.rightMargin = dp(12);
        rail.addView(outer, outerParams);
        loadImageInto(image, "https://image.tmdb.org/t/p/w780" + backdrop, shown * 70L);

        outer.setOnFocusChangeListener((v, focused) -> {
            v.animate().cancel();
            v.animate().scaleX(focused ? 1.055f : 1f).scaleY(focused ? 1.055f : 1f)
                    .translationZ(focused ? dp(8) : 0).setDuration(focused ? 150 : 110).start();
            if (focused) {
                GradientDrawable focusedSurface = glassDrawable(0xB82A3C59, 0xFFF0F6FF, 12);
                focusedSurface.setStroke(dp(2), blue);
                card.setBackground(focusedSurface);
                card.setElevation(dp(10));
                setHeroDebounced(title,
                    overview.isEmpty() ? "Explorá películas y series de tu catálogo." : overview, backdrop);
            } else {
                card.setBackground(mediaCardBackground());
                card.setElevation(0);
            }
        });
        outer.setOnClickListener(v -> showMediaDetails(title, overview, tv));
        shown++;
    }
    if (shown == 0) addCatalogPlaceholder(rail, "Esperando contenido del catálogo");
}

    private View buildWeatherWidget() {
    LinearLayout widget = new LinearLayout(this);
    widget.setOrientation(LinearLayout.HORIZONTAL);
    widget.setGravity(Gravity.CENTER_VERTICAL);
    widget.setPadding(dp(14), dp(5), dp(16), dp(5));
    widget.setFocusable(true);
    widget.setClickable(true);
    GradientDrawable normal = glassDrawable(0x66202d43, 0x55ffffff, 16);
    GradientDrawable focused = glassDrawable(blendColor(blue, 0xff17283e, 0.28f), 0xffdceaff, 16);
    focused.setStroke(dp(2), blue);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_focused}, focused);
    states.addState(new int[]{}, normal);
    widget.setBackground(states);
    widget.setOnClickListener(v -> showWeatherCityDialog());

    TextClock clock = new TextClock(this);
    clock.setFormat24Hour("HH:mm");
    clock.setFormat12Hour("HH:mm");
    clock.setTextSize(24);
    clock.setTextColor(white);
    clock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    widget.addView(clock, new LinearLayout.LayoutParams(-2, -2));

    LinearLayout info = new LinearLayout(this);
    info.setOrientation(LinearLayout.VERTICAL);
    weatherText = text("Cargando clima…", 13, white, true);
    regionText = text(weatherCity, 11, muted, false);
    info.addView(weatherText);
    info.addView(regionText);
    LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-2, -2);
    infoParams.leftMargin = dp(12);
    widget.addView(info, infoParams);
    return widget;
}

    private void loadWeather() {
    final String city = weatherCity;
    executor.execute(() -> {
        try {
            if (Double.isNaN(weatherLat) || Double.isNaN(weatherLon)) {
                JSONObject geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + enc(city) + "&count=1&language=es&format=json");
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    handler.post(() -> applyWeather(null, null, "Ciudad no encontrada"));
                    return;
                }
                JSONObject first = results.getJSONObject(0);
                weatherLat = first.getDouble("latitude");
                weatherLon = first.getDouble("longitude");
                prefs().edit().putString("weather_lat", String.valueOf(weatherLat))
                        .putString("weather_lon", String.valueOf(weatherLon)).apply();
            }
            JSONObject w = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + weatherLat
                    + "&longitude=" + weatherLon + "&current=temperature_2m,weather_code&timezone=auto");
            JSONObject current = w.getJSONObject("current");
            final int temp = (int) Math.round(current.getDouble("temperature_2m"));
            final int code = current.optInt("weather_code", -1);
            handler.post(() -> applyWeather(temp + "°C", weatherDescription(code), city));
        } catch (Exception e) {
            handler.post(() -> applyWeather(null, null, null));
        }
    });
}

    private void applyWeather(String temp, String description, String region) {
    if (weatherText == null || regionText == null) return;
    if (temp == null) {
        CharSequence current = weatherText.getText();
        if (current == null || current.toString().startsWith("Cargando")) weatherText.setText("Sin datos del clima");
        if (region != null) regionText.setText(region);
        return;
    }
    weatherText.setText(temp + " · " + description);
    regionText.setText(region);
}

    private String weatherDescription(int code) {
    if (code == 0) return "Despejado";
    if (code == 1 || code == 2) return "Poco nublado";
    if (code == 3) return "Nublado";
    if (code == 45 || code == 48) return "Niebla";
    if (code >= 51 && code <= 57) return "Llovizna";
    if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Lluvia";
    if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Nieve";
    if (code >= 95) return "Tormenta";
    return "Variable";
}

    private void showWeatherCityDialog() {
    final EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setHint("Ciudad (ej: Buenos Aires)");
    input.setText(weatherCity);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setTextColor(white);
    input.setHintTextColor(muted);
    LinearLayout box = new LinearLayout(this);
    box.setPadding(dp(20), dp(8), dp(20), 0);
    box.addView(input, new LinearLayout.LayoutParams(-1, -2));
    new AlertDialog.Builder(this)
        .setTitle("Región del clima")
        .setView(box)
        .setPositiveButton("Guardar", (d, w) -> {
            String city = input.getText().toString().trim();
            if (city.isEmpty()) return;
            weatherCity = city;
            weatherLat = Double.NaN;
            weatherLon = Double.NaN;
            prefs().edit().putString("weather_city", city).remove("weather_lat").remove("weather_lon").apply();
            regionText.setText(city);
            weatherText.setText("Cargando clima…");
            loadWeather();
        })
        .setNegativeButton("Cancelar", null)
        .show();
}
}
