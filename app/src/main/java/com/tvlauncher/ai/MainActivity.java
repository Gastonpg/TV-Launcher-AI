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

    private SharedPreferences prefs() { return getSharedPreferences("tvpg", MODE_PRIVATE); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        tmdbKey = prefs().getString("tmdb_key", "").trim();
        customWallpaperUri = prefs().getString("wallpaper_uri", "");
        buildUi();
        loadApps();
        refreshCatalog();
        if (!applyCustomWallpaper()) showFallback("Preparando TV Launcher PG…");
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (trending.isEmpty()) showFallback("Fondos online · configurá TMDB para cargar catálogos reales.");
                handler.postDelayed(this, 60 * 1000L);
            }
        }, 60 * 1000L);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 13, 21));
        heroImage = new ImageView(this); heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        ScrollView page = new ScrollView(this); page.setFillViewport(false); page.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(38), dp(22), dp(38), dp(24));
        page.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("PG", 21, white, true); logo.setGravity(Gravity.CENTER); logo.setTag("pg_logo");
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff17233a, blendColor(blue, Color.BLACK, 0.55f)});
        logoBg.setCornerRadius(dp(13)); logoBg.setStroke(dp(2), blue); logo.setBackground(logoBg);
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(48)));
        TextView brand = text("TV LAUNCHER PG", 18, white, true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.leftMargin = dp(13); header.addView(brand, bp);
        View spacer = new View(this); header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        Button systemSettings = button("⚙ Ajustes del TV", false); systemSettings.setOnClickListener(v -> openSystemSettings());
        header.addView(systemSettings, new LinearLayout.LayoutParams(-2, dp(46)));
        Button config = button("Personalizar", true); config.setOnClickListener(v -> showPersonalizationMenu());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(46)); cp.leftMargin = dp(9); header.addView(config, cp);
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        View gap = new View(this); content.addView(gap, new LinearLayout.LayoutParams(1, dp(55)));
        TextView featuredLabel = text("DESTACADO PARA VOS", 13, blue, true); featuredLabel.setTag("accent_label"); content.addView(featuredLabel);
        heroTitle = text("Tu pantalla, tu mundo", 34, white, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = dp(7); content.addView(heroTitle, tp);
        heroDescription = text("Tus aplicaciones y recomendaciones en un solo lugar.", 15, muted, false); heroDescription.setMaxLines(3);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(600), -2); dp.topMargin = dp(8); content.addView(heroDescription, dp);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button change = button("↻ Cambiar destacado", true); change.setOnClickListener(v -> changeFeatured());
        actions.addView(change, new LinearLayout.LayoutParams(-2, dp(48)));
        Button system = button("Abrir ajustes", false); system.setOnClickListener(v -> openSystemSettings());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(48)); sp.leftMargin = dp(10); actions.addView(system, sp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(54)); ap.topMargin = dp(9); content.addView(actions, ap);

        addSectionTitle(content, "TUS APLICACIONES");
        HorizontalScrollView appsScroll = railScroll(); appsScroll.setClipChildren(false); appsScroll.setClipToPadding(false); appRail = new LinearLayout(this); appRail.setOrientation(LinearLayout.HORIZONTAL);
        appRail.setClipChildren(false); appRail.setClipToPadding(false);
        appsScroll.addView(appRail, new HorizontalScrollView.LayoutParams(-2, -1)); content.addView(appsScroll, new LinearLayout.LayoutParams(-1, dp(112)));

        addSectionTitle(content, "PELÍCULAS POPULARES");
        HorizontalScrollView moviesScroll = railScroll(); movieRail = new LinearLayout(this); movieRail.setOrientation(LinearLayout.HORIZONTAL);
        moviesScroll.addView(movieRail, new HorizontalScrollView.LayoutParams(-2, -1)); content.addView(moviesScroll, new LinearLayout.LayoutParams(-1, dp(190)));

        addSectionTitle(content, "SERIES POPULARES");
        HorizontalScrollView seriesScroll = railScroll(); seriesRail = new LinearLayout(this); seriesRail.setOrientation(LinearLayout.HORIZONTAL);
        seriesScroll.addView(seriesRail, new HorizontalScrollView.LayoutParams(-2, -1)); content.addView(seriesScroll, new LinearLayout.LayoutParams(-1, dp(190)));

        statusText = text("Android 9 · optimizado para TV Box · PG", 11, Color.rgb(160,173,195), false);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2); stp.topMargin = dp(12); content.addView(statusText, stp);
        setContentView(root);
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
        appRail.removeAllViews(); PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        Collections.sort(apps, new Comparator<android.content.pm.ResolveInfo>() {
            @Override public int compare(android.content.pm.ResolveInfo a, android.content.pm.ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });
        for (android.content.pm.ResolveInfo info : apps) {
            if (info.activityInfo.packageName.equals(getPackageName())) continue;
            final String pkg = info.activityInfo.packageName; final String cls = info.activityInfo.name;
            final String name = String.valueOf(info.loadLabel(pm));

            // Google TV-inspired app tile: a wide rounded card, not a square box around the icon.
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setPadding(dp(2), dp(3), dp(2), dp(3));
            tile.setBackgroundColor(Color.TRANSPARENT);

            FrameLayout card = new FrameLayout(this);
            GradientDrawable normalCard = glassDrawable(0x55202d43, 0x66ffffff, 15);
            GradientDrawable focusedCard = glassDrawable(blendColor(blue, 0xff20334c, 0.48f), 0xffd9ebff, 15);
            focusedCard.setStroke(dp(2), blue);
            StateListDrawable cardStates = new StateListDrawable();
            cardStates.addState(new int[]{android.R.attr.state_focused}, focusedCard);
            cardStates.addState(new int[]{}, normalCard);
            card.setTag("app_card");
            card.setBackground(cardStates);
            card.setClipToPadding(false);
            card.setClipToOutline(true);
            // A subtle top reflection gives the translucent card a glass-like finish on Android 9.
            View sheen = new View(this);
            GradientDrawable sheenDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x44ffffff, 0x12ffffff, 0x00ffffff});
            sheenDrawable.setCornerRadius(dp(15));
            sheen.setBackground(sheenDrawable);
            card.addView(sheen, new FrameLayout.LayoutParams(-1, -1));

            ImageView icon = new ImageView(this);
            icon.setBackgroundColor(Color.TRANSPARENT);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try { icon.setImageDrawable(info.loadIcon(pm)); } catch (Exception ignored) { }
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
            card.addView(icon, iconParams);
            tile.addView(card, new LinearLayout.LayoutParams(dp(128), dp(66)));

            TextView label = text(name, 11, white, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(128), -2);
            np.topMargin = dp(5);
            tile.addView(label, np);

            // Google TV-style focus: the selected tile comes forward clearly, with a light parallax lift.
            tile.setPivotX(dp(70));
            tile.setPivotY(dp(50));
            tile.setOnFocusChangeListener((v, focused) -> {
                v.animate().cancel();
                v.animate()
                        .scaleX(focused ? 1.15f : 1f)
                        .scaleY(focused ? 1.15f : 1f)
                        .translationY(focused ? -dp(6) : 0f)
                        .setDuration(focused ? 150 : 110)
                        .start();
                label.setTextColor(focused ? blue : white);
                if (focused) selectApp(pkg, name);
            });
            tile.setOnClickListener(v -> launchApp(pkg, cls));
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(140), dp(100));
            item.rightMargin = dp(8);
            appRail.addView(tile, item);
        }
        if (apps.isEmpty()) statusText.setText("No se encontraron aplicaciones iniciables.");
    }

    private void selectApp(String pkg, String name) {
        selectedPackage = pkg; selectedAppName = name;
        heroTitle.setText(name);
        heroDescription.setText("Seleccionaste " + name + ". Explorá las recomendaciones o presioná OK para abrir la aplicación.");
        if (!hasCustomWallpaper()) {
            if (!trending.isEmpty()) loadBackdropForApp(pkg);
            else showFallback("Fondo destacado para " + name);
        }
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
            addCatalogPlaceholder(movieRail, "Configurá TMDB para ver películas");
            addCatalogPlaceholder(seriesRail, "Configurá TMDB para ver series");
            return;
        }
        statusText.setText("Cargando catálogos de películas y series…");
        executor.execute(() -> {
            try {
                JSONObject trendJson = getJson("https://api.themoviedb.org/3/trending/all/day?api_key=" + enc(tmdbKey) + "&language=es-ES");
                JSONArray t = trendJson.optJSONArray("results");
                synchronized (trending) { trending.clear(); if (t != null) for (int i=0;i<t.length();i++) { JSONObject o=t.optJSONObject(i); if(o!=null && !o.optString("backdrop_path", "").isEmpty()) trending.add(o); } }
                JSONObject movies = getJson("https://api.themoviedb.org/3/movie/popular?api_key=" + enc(tmdbKey) + "&language=es-ES&page=1");
                JSONObject shows = getJson("https://api.themoviedb.org/3/tv/popular?api_key=" + enc(tmdbKey) + "&language=es-ES&page=1");
                JSONArray ma = movies.optJSONArray("results"), sa = shows.optJSONArray("results");
                handler.post(() -> {
                    movieRail.removeAllViews(); seriesRail.removeAllViews();
                    addMediaCards(movieRail, ma, false); addMediaCards(seriesRail, sa, true);
                    statusText.setText("Catálogos online · TMDB · los enlaces de reproducción dependen de cada aplicación.");
                });
            } catch (Exception e) {
                handler.post(() -> { statusText.setText("No se pudo cargar TMDB. Revisá la clave y la conexión."); addCatalogPlaceholder(movieRail, "No se pudo cargar el catálogo"); addCatalogPlaceholder(seriesRail, "No se pudo cargar el catálogo"); });
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
        if (array == null || array.length() == 0) { addCatalogPlaceholder(rail, "No hay títulos disponibles"); return; }
        int limit = Math.min(array.length(), 12);
        for (int i=0;i<limit;i++) {
            JSONObject item = array.optJSONObject(i); if (item == null) continue;
            String title = item.optString("title", item.optString("name", "Sin título"));
            String overview = item.optString("overview", "Sin descripción disponible.");
            String poster = item.optString("poster_path", "");
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setFocusable(true); card.setClickable(true); card.setPadding(dp(3),dp(3),dp(3),dp(3));
            GradientDrawable normal = glassDrawable(0x88202d43, 0x66ffffff, 10);
            GradientDrawable focusedBackground = glassDrawable(blendColor(blue, 0xff263f68, 0.35f), 0xffe4f1ff, 10); focusedBackground.setStroke(dp(2), blue);
            StateListDrawable state = new StateListDrawable(); state.addState(new int[]{android.R.attr.state_focused},focusedBackground); state.addState(new int[]{},normal); card.setBackground(state);
            card.setClipToOutline(true);
            ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(0x33202a3d);
            card.addView(image,new LinearLayout.LayoutParams(dp(112),dp(125)));
            TextView label = text(title,10,white,true); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112),dp(35)); lp.topMargin=dp(3); card.addView(label,lp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(124),dp(171)); cp.rightMargin=dp(9); rail.addView(card,cp);
            if (!poster.isEmpty()) {
                String imageUrl="https://image.tmdb.org/t/p/w342"+poster;
                executor.execute(() -> { try { Bitmap bmp=getBitmap(imageUrl); handler.post(() -> { if(bmp!=null) image.setImageBitmap(bmp); }); } catch(Exception ignored){} });
            }
            card.setOnFocusChangeListener((v, focused) -> { v.setScaleX(focused?1.04f:1f); v.setScaleY(focused?1.04f:1f); if(focused){heroTitle.setText(title); heroDescription.setText(overview.isEmpty()?"Explorá este título en tus aplicaciones instaladas.":overview); if(!hasCustomWallpaper() && !poster.isEmpty()) { final int token=++backdropToken; executor.execute(() -> { try { Bitmap bmp=getBitmap("https://image.tmdb.org/t/p/w1280"+item.optString("backdrop_path", "")); handler.post(() -> { if(bmp!=null && token==backdropToken) applyHeroBitmap(bmp); }); } catch(Exception ignored){} }); } } });
            card.setOnClickListener(v -> showMediaDetails(title, overview, tv));
        }
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
            "Establecer como launcher predeterminado"
        };
        new AlertDialog.Builder(this)
            .setTitle("Personalizar TV Launcher PG")
            .setItems(options, (dialog, which) -> {
                if (which == 0) chooseWallpaper();
                else if (which == 1) useAutomaticWallpapers();
                else if (which == 2) showCatalogSettings();
                else openHomeSettings();
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
}
