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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
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
    private ImageView heroImage, profileAvatar;
    private TextView heroTitle, heroDescription, statusText;
    private LinearLayout appRail, movieRail, seriesRail;
    private String tmdbKey = "";
    private int cardAnimationStyle = 0;
    private String selectedPackage = "";
    private String selectedAppName = "";
    private static final int REQUEST_PROFILE_IMAGE = 7402;
    private String profileImageUri = "";
    private final List<JSONObject> trending = new ArrayList<>();
    private int backdropToken = 0;

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
    cardAnimationStyle = prefs().getInt("card_animation_style", 0);
    profileImageUri = prefs().getString("profile_image_uri", "");
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
    handler.postDelayed(weatherLoop, 800);
    handler.postDelayed(new Runnable() {
        @Override public void run() {
            if (trending.isEmpty()) showFallback("Configurá TMDB para cargar pósteres de películas.");
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

        // Avatar circular a la izquierda: permite elegir una imagen como perfil de sesión.
        profileAvatar = new ImageView(this);
        profileAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profileAvatar.setContentDescription("Agregar imagen de perfil");
        profileAvatar.setFocusable(true);
        profileAvatar.setClickable(true);
        profileAvatar.setBackground(profileAvatarBackground());
        profileAvatar.setClipToOutline(true);
        profileAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        profileAvatar.setColorFilter(0xfff0f3fa);
        if (!profileImageUri.isEmpty()) {
            try {
                profileAvatar.setImageURI(Uri.parse(profileImageUri));
                profileAvatar.clearColorFilter();
            } catch (Exception ignored) { }
        }
        profileAvatar.setOnClickListener(v -> chooseProfileImage());
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        avatarLp.rightMargin = dp(12);
        nav.addView(profileAvatar, 0, avatarLp);

        nav.addView(navScroll, new LinearLayout.LayoutParams(0, dp(54), 1f));

        // Solo la tuerca; la marca queda al final, inmediatamente a su derecha.
        Button settings = button("⚙", false);
        settings.setTextSize(20);
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setElevation(0f);
        settings.setMinWidth(0);
        settings.setMinimumWidth(0);
        settings.setContentDescription("Personalizar TV Launcher PG");
        settings.setOnClickListener(v -> showPersonalizationMenu());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        settingsLp.leftMargin = dp(4);
        nav.addView(settings, settingsLp);

        TextView logo = text("TV LAUNCHER PG", 13, white, true);
        logo.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        logo.setTag("pg_logo");
        logo.setSingleLine(true);
        logo.setEllipsize(TextUtils.TruncateAt.END);
        logo.setPadding(dp(12), 0, 0, 0);
        logo.setFocusable(true);
        logo.setClickable(true);
        logo.setContentDescription("Abrir ajustes del dispositivo");
        logo.setOnClickListener(v -> openSystemSettings());
        logo.setOnFocusChangeListener((v, focused) -> {
            logo.setTextColor(focused ? 0xffdceaff : white);
            logo.animate().cancel();
            logo.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                    .setDuration(focused ? 150 : 110).start();
        });
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(126), dp(44));
        logoLp.leftMargin = dp(4);
        nav.addView(logo, logoLp);

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

        // La zona promocional bajo las categorías queda oculta: el catálogo sube y gana protagonismo.
        // Conservamos las vistas de texto en memoria porque las funciones de TMDB actualizan el título
        // y la descripción al navegar por el contenido.
        featured.setVisibility(View.GONE);
        screen.addView(featured, new LinearLayout.LayoutParams(-1, 0));

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
        // Tipografía sans-serif nativa de Android TV (Roboto): clara y legible a distancia.
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
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

            // App tiles: el icono se adapta a todo el rectángulo, como una ficha de Google TV.
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
            normal.setColor(0xff777e89);
            normal.setCornerRadius(dp(5));
            normal.setStroke(dp(1), 0xff858c97);

            GradientDrawable focused = new GradientDrawable();
            focused.setColor(0xffe8edf5);
            focused.setCornerRadius(dp(5));
            focused.setStroke(dp(2), blue);

            StateListDrawable faceStates = new StateListDrawable();
            faceStates.addState(new int[]{android.R.attr.state_focused}, focused);
            faceStates.addState(new int[]{}, normal);
            tileFace.setBackground(faceStates);
            tileFace.setClipToOutline(true);

            // Google TV usa banners panorámicos cuando la app los declara. Priorizarlos
            // permite llenar toda la tarjeta con arte original, sin estirar el icono cuadrado.
            Drawable appIcon = null;
            Drawable appBanner = null;
            try { appIcon = info.loadIcon(pm); } catch (Exception ignored) { }
            try { appBanner = info.loadBanner(pm); } catch (Exception ignored) { }
            if (appBanner == null) {
                try {
                    if (info.activityInfo.banner != 0) {
                        appBanner = info.activityInfo.loadBanner(pm);
                    }
                } catch (Exception ignored) { }
            }

            if (appBanner != null) {
                ImageView banner = new ImageView(this);
                banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
                banner.setImageDrawable(appBanner);
                tileFace.addView(banner, new FrameLayout.LayoutParams(-1, -1));
            } else if (appIcon != null) {
                // Fallback para apps que no publican banner de TV: fondo ampliado del icono,
                // oscurecido para crear una placa panorámica, y logo original sin deformar.
                ImageView backdrop = new ImageView(this);
                backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                backdrop.setImageDrawable(appIcon);
                backdrop.setAlpha(0.42f);
                tileFace.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

                View tint = new View(this);
                GradientDrawable tintGradient = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0x22202d43, 0x99202d43});
                tint.setBackground(tintGradient);
                tileFace.addView(tint, new FrameLayout.LayoutParams(-1, -1));

                ImageView icon = new ImageView(this);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                icon.setPadding(dp(2), dp(2), dp(2), dp(2));
                icon.setImageDrawable(appIcon);
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                        dp(68), dp(60), Gravity.CENTER);
                tileFace.addView(icon, iconParams);
            }
            tile.addView(tileFace, new LinearLayout.LayoutParams(dp(124), dp(64)));

            TextView label = text(name, 12, white, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(124), dp(24));
            labelParams.topMargin = dp(5);
            tile.addView(label, labelParams);

            tile.setOnFocusChangeListener((v, hasFocus) -> {
                animateSelectableCard(v, hasFocus);
                label.setTextColor(hasFocus ? 0xffffffff : 0xffd5d9e2);
                if (hasFocus) selectApp(pkg, name);
            });
            tile.setOnClickListener(v -> launchApp(pkg, cls));

            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(140), dp(110));
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
    String path = "";
    synchronized (trending) {
        if (!trending.isEmpty()) {
            path = jsonStr(trending.get(Math.abs(pkg.hashCode()) % trending.size()), "backdrop_path");
        }
    }
    setBackdropDebounced(path);
}

    private void loadBackdropForApp(String pkg) {
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
                    if (o != null && !jsonStr(o, "poster_path").isEmpty()) trending.add(o);
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
                    showRandomFeaturedPoster();
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
        showRandomFeaturedPoster();
    }

    private void showFallback(String message) {
        statusTextIfReady(message);
        if (!trending.isEmpty()) {
            showRandomFeaturedPoster();
        } else {
            statusTextIfReady(message + " No hay pósteres disponibles hasta configurar TMDB.");
        }
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

    private void statusTextIfReady(String value) {
        if (statusText != null) statusText.setText(value);
    }

    private void showPersonalizationMenu() {
    final String[] options = new String[] {
        "Administrar y deshabilitar aplicaciones",
        "Configurar catálogo y API de TMDB",
        "Animaciones de selección",
        "Establecer como launcher predeterminado"
    };
    final String[] descriptions = new String[] {
        "Consultar apps instaladas e intentar deshabilitarlas",
        "Personalizá las recomendaciones y tu clave API",
        "Elegí cómo se mueven las tarjetas al enfocarlas",
        "Elegí TV Launcher PG como pantalla de inicio"
    };

    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(24), dp(22), dp(24), dp(16));
    panel.setBackground(roundRect(0xff151a23, 0xff343c4a, 18));

    TextView heading = text("Personalizar", 22, 0xfff5f7fb, true);
    panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

    TextView subtitle = text("TV LAUNCHER PG", 11, 0xffaebbd0, true);
    subtitle.setLetterSpacing(0.08f);
    LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
    subtitleLp.topMargin = dp(4);
    subtitleLp.bottomMargin = dp(16);
    panel.addView(subtitle, subtitleLp);

    final AlertDialog dialog = new AlertDialog.Builder(this).create();
    for (int i = 0; i < options.length; i++) {
        final int index = i;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(menuRowBackground());

        TextView title = text(options[i], 14, 0xfff1f4fa, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = text(descriptions[i], 11, 0xffaab5c7, false);
        detail.setMaxLines(2);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(3);
        row.addView(detail, detailLp);

        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (index == 0) showAppManager();
            else if (index == 1) showCatalogSettings();
            else if (index == 2) showCardAnimationSettings();
            else openHomeSettings();
        });
        row.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(menuRowBackgroundFocused(focused));
            title.setTextColor(focused ? 0xffffffff : 0xfff1f4fa);
            detail.setTextColor(focused ? 0xffdbe6f7 : 0xffaab5c7);
        });
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(62));
        rowLp.bottomMargin = dp(4);
        panel.addView(row, rowLp);
    }

    TextView close = text("CERRAR", 12, 0xffd5deed, true);
    close.setGravity(Gravity.CENTER);
    close.setFocusable(true);
    close.setClickable(true);
    close.setPadding(dp(18), dp(8), dp(18), dp(8));
    close.setBackground(menuRowBackground());
    close.setOnFocusChangeListener((v, focused) -> v.setBackground(menuRowBackgroundFocused(focused)));
    close.setOnClickListener(v -> dialog.dismiss());
    LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-2, dp(38));
    closeLp.gravity = Gravity.RIGHT;
    closeLp.topMargin = dp(7);
    panel.addView(close, closeLp);

    dialog.setView(panel);
    dialog.setOnShowListener(d -> {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(dp(430), -2);
        }
        panel.getChildAt(2).requestFocus();
    });
    dialog.show();
}

private GradientDrawable roundRect(int fill, int stroke, float radiusDp) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(fill);
    shape.setCornerRadius(dp(radiusDp));
    if (stroke != 0) shape.setStroke(dp(1), stroke);
    return shape;
}

private Drawable menuRowBackground() {
    return roundRect(0x00151a23, 0x00343c4a, 10);
}

private Drawable menuRowBackgroundFocused(boolean focused) {
    if (focused) return roundRect(0xff29384d, 0xff9fc5ff, 10);
    return menuRowBackground();
}

    private Drawable buttonGlassBackground(boolean primary) {
        // Sin efecto liquid-glass: botones sólidos y limpios, al estilo de una interfaz de TV.
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(20));
        normal.setColor(primary ? 0xfff1f3f7 : 0x00000000);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(20));
        focused.setColor(primary ? 0xffffffff : 0xff263244);
        focused.setStroke(dp(2), primary ? 0xffffffff : 0xffdce6f7);
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
            view.setBackgroundColor(Color.TRANSPARENT);
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

    private GradientDrawable profileAvatarBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x663a465b);
        d.setStroke(dp(1), 0x99e5eaf4);
        return d;
    }

    private void chooseProfileImage() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(pick, REQUEST_PROFILE_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el selector de imágenes", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppManager() {
        PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> installed;
        try {
            installed = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setTitle("Administrador de aplicaciones")
                .setMessage("El firmware no permite consultar las aplicaciones instaladas.")
                .setPositiveButton("Abrir ajustes de aplicaciones", (d, w) -> openAppsSettings())
                .setNegativeButton("Cerrar", null).show();
            return;
        }

        Collections.sort(installed, new Comparator<android.content.pm.ApplicationInfo>() {
            @Override public int compare(android.content.pm.ApplicationInfo a, android.content.pm.ApplicationInfo b) {
                String an = String.valueOf(a.loadLabel(pm));
                String bn = String.valueOf(b.loadLabel(pm));
                return an.compareToIgnoreCase(bn);
            }
        });

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView explanation = text(
            "Elegí una app para intentar deshabilitarla. Android puede bloquear esta acción en aplicaciones del sistema; si la bloquea, abriremos su ficha de ajustes para comprobar las opciones disponibles. No deshabilites componentes esenciales.",
            12, muted, false);
        explanation.setPadding(dp(4), dp(4), dp(4), dp(12));
        list.addView(explanation);

        int added = 0;
        for (android.content.pm.ApplicationInfo info : installed) {
            final String pkg = info.packageName;
            if (pkg == null || pkg.equals(getPackageName())) continue;
            final String appName;
            try { appName = String.valueOf(info.loadLabel(pm)); }
            catch (Exception e) { continue; }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(5), dp(4), dp(5));
            row.setBackground(roundRect(0x44202d43, 0x335f6d82, 8));

            ImageView icon = new ImageView(this);
            try { icon.setImageDrawable(info.loadIcon(pm)); } catch (Exception ignored) { }
            row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(appName, 12, white, true);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);
            TextView packageName = text(pkg, 9, muted, false);
            packageName.setMaxLines(1);
            packageName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            labels.addView(packageName);
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            labelsLp.leftMargin = dp(8);
            row.addView(labels, labelsLp);

            boolean enabled = info.enabled;
            Button disable = button(enabled ? "Deshabilitar" : "Habilitar", false);
            disable.setTextSize(10);
            disable.setPadding(dp(5), 0, dp(5), 0);
            disable.setOnClickListener(v -> {
                if (enabled) confirmDisableApp(pkg, appName);
                else confirmEnableApp(pkg, appName);
            });
            row.addView(disable, new LinearLayout.LayoutParams(-2, dp(36)));

            Button infoButton = button("Info", false);
            infoButton.setTextSize(10);
            infoButton.setPadding(dp(5), 0, dp(5), 0);
            infoButton.setOnClickListener(v -> openAppInfo(pkg));
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-2, dp(36));
            infoLp.leftMargin = dp(4);
            row.addView(infoButton, infoLp);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(52));
            rowLp.bottomMargin = dp(5);
            list.addView(row, rowLp);
            added++;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
            .setTitle("Administrador de aplicaciones (" + added + ")")
            .setView(scroll)
            .setPositiveButton("Ajustes de aplicaciones", (d, w) -> openAppsSettings())
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void confirmDisableApp(String packageName, String appName) {
        new AlertDialog.Builder(this)
            .setTitle("Deshabilitar " + appName + "?")
            .setMessage("Se intentará deshabilitar el paquete " + packageName + ". Si es el launcher original, podrías necesitar restaurarlo desde Ajustes o ADB. TV Launcher PG no se deshabilitará a sí mismo.")
            .setPositiveButton("Intentar", (d, w) -> {
                try {
                    getPackageManager().setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                        0);
                    Toast.makeText(this, appName + " deshabilitada.", Toast.LENGTH_LONG).show();
                    showAppManager();
                } catch (SecurityException denied) {
                    new AlertDialog.Builder(this)
                        .setTitle("Android bloqueó la operación")
                        .setMessage("El sistema no concedió a TV Launcher PG permiso para deshabilitar esta aplicación. Agregar un permiso al manifiesto no alcanza si el firmware lo reserva al sistema. Podés abrir la ficha de la app para ver si Ajustes ofrece la opción, o usar ADB/Launcher Manager como alternativa.")
                        .setPositiveButton("Abrir información", (dialog, which) -> openAppInfo(packageName))
                        .setNeutralButton("Ajustes de apps", (dialog, which) -> openAppsSettings())
                        .setNegativeButton("Cerrar", null)
                        .show();
                } catch (Exception error) {
                    Toast.makeText(this, "No se pudo deshabilitarla: " + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void confirmEnableApp(String packageName, String appName) {
        new AlertDialog.Builder(this)
            .setTitle("Habilitar " + appName + "?")
            .setMessage("Se intentará volver a habilitar el paquete " + packageName + ".")
            .setPositiveButton("Habilitar", (d, w) -> {
                try {
                    getPackageManager().setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0);
                    Toast.makeText(this, appName + " habilitada.", Toast.LENGTH_LONG).show();
                    showAppManager();
                } catch (SecurityException denied) {
                    new AlertDialog.Builder(this)
                        .setTitle("Android bloqueó la operación")
                        .setMessage("El sistema no concedió a TV Launcher PG permiso para habilitar esta aplicación. Abrí su ficha de información o usá ADB/Launcher Manager.")
                        .setPositiveButton("Abrir información", (dialog, which) -> openAppInfo(packageName))
                        .setNegativeButton("Cerrar", null)
                        .show();
                } catch (Exception error) {
                    Toast.makeText(this, "No se pudo habilitarla: " + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la información de esa aplicación.", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppsSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            openSystemSettings();
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
    label.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
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
        animateSelectableCard(v, focused);
    }

    // Animación compartida por las apps y el carrusel de destacados.
    private void animateSelectableCard(View v, boolean focused) {
        if (v == null) return;
        v.animate().cancel();

        float scale = 1f;
        float lift = 0f;
        float shiftX = 0f;
        float elevation = focused ? dp(2) : 0f;
        long duration = focused ? 210 : 150;
        android.view.animation.Interpolator interpolator = new DecelerateInterpolator();

        switch (cardAnimationStyle) {
            case 1: // Elevación pura: la tarjeta sube, pero no cambia de tamaño.
                scale = 1f;
                lift = focused ? -dp(10) : 0f;
                elevation = focused ? dp(12) : 0f;
                duration = focused ? 240 : 170;
                interpolator = new AccelerateDecelerateInterpolator();
                break;
            case 2: // Zoom con rebote claramente visible.
                scale = focused ? 1.12f : 1f;
                lift = focused ? -dp(3) : 0f;
                elevation = focused ? dp(14) : 0f;
                duration = focused ? 340 : 190;
                interpolator = new OvershootInterpolator(1.35f);
                break;
            case 3: // Deslizamiento lateral: casi no amplía la tarjeta.
                scale = focused ? 1.015f : 1f;
                shiftX = focused ? dp(9) : 0f;
                lift = 0f;
                elevation = focused ? dp(5) : 0f;
                duration = focused ? 230 : 160;
                interpolator = new AccelerateDecelerateInterpolator();
                break;
            default: // Google TV: zoom moderado con elevación suave.
                scale = focused ? 1.055f : 1f;
                lift = focused ? -dp(3) : 0f;
                elevation = focused ? dp(8) : 0f;
                duration = focused ? 190 : 145;
                interpolator = new DecelerateInterpolator(1.7f);
                break;
        }

        v.animate()
                .scaleX(scale).scaleY(scale)
                .translationY(lift).translationX(shiftX)
                .translationZ(elevation)
                .setInterpolator(interpolator)
                .setDuration(duration)
                .start();
    }

    private void showCardAnimationSettings() {
        final String[] names = {
            "Google TV · Zoom suave",
            "Elevación · Sube sin agrandarse",
            "Rebote · Zoom dinámico",
            "Deslizamiento · Movimiento lateral"
        };
        final String[] descriptions = {
            "Aumenta un poco de tamaño y se eleva suavemente",
            "Sube bastante, sin cambiar el tamaño de la tarjeta",
            "Zoom más grande con un rebote al llegar al foco",
            "Se desplaza hacia un lado sin agrandarse casi nada"
        };
        final String[] entries = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            entries[i] = names[i] + "\\n" + descriptions[i];
        }
        new AlertDialog.Builder(this)
            .setTitle("Animaciones de selección")
            .setSingleChoiceItems(names, cardAnimationStyle, (dialog, which) -> {
                cardAnimationStyle = which;
                prefs().edit().putInt("card_animation_style", which).apply();
                dialog.dismiss();
                // Reproducir la nueva animación enseguida para que se note el cambio.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    View focusedView = getWindow().getDecorView().findFocus();
                    if (focusedView != null) animateSelectableCard(focusedView, true);
                }, 180);
                Toast.makeText(this, "Animación guardada; movete entre tarjetas para verla", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cerrar", null)
            .show();
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
    if (backdropPath == null || backdropPath.isEmpty()) return;
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

    private void showRandomFeaturedPoster() {
        if (heroImage == null) return;
        final JSONObject item;
        synchronized (trending) {
            if (trending.isEmpty()) {
                statusTextIfReady("Configurá TMDB para mostrar pósteres de películas.");
                return;
            }
            List<JSONObject> available = new ArrayList<>();
            for (JSONObject candidate : trending) {
                if (!jsonStr(candidate, "poster_path").isEmpty()) available.add(candidate);
            }
            if (available.isEmpty()) return;
            item = available.get((int) (System.currentTimeMillis() % available.size()));
        }
        final String posterPath = jsonStr(item, "poster_path");
        final int token = ++backdropToken;
        executor.execute(() -> {
            try {
                Bitmap bmp = getBitmapCached("https://image.tmdb.org/t/p/w780" + posterPath);
                handler.post(() -> {
                    if (bmp != null && token == backdropToken) {
                        applyHeroBitmap(bmp);
                        heroTitle.setText(mediaTitle(item));
                        heroDescription.setText(jsonStr(item, "overview"));
                    }
                });
            } catch (Exception ignored) { }
        });
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
            animateSelectableCard(v, focused);
            if (focused) {
                GradientDrawable focusedSurface = roundRect(0xff263f68, blue, 12);
                focusedSurface.setStroke(dp(2), blue);
                card.setBackground(focusedSurface);
                card.setElevation(dp(10));
                setHeroDebounced(title,
                    overview.isEmpty() ? "Explorá películas y series de tu catálogo." : overview,
                    jsonStr(item, "poster_path"));
            } else {
                card.setBackground(mediaCardBackground());
                card.setElevation(0);
                handler.postDelayed(() -> {
                    View focusedView = getCurrentFocus();
                    boolean stillInFeatured = false;
                    while (focusedView != null) {
                        if (focusedView == forYouRail) { stillInFeatured = true; break; }
                        android.view.ViewParent parent = focusedView.getParent();
                        focusedView = parent instanceof View ? (View) parent : null;
                    }
                    if (!stillInFeatured) showRandomFeaturedPoster();
                }, 260);
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
