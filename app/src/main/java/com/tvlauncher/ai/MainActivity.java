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
    private final int blue = Color.rgb(130, 184, 255);
    private final int cardColor = Color.argb(225, 24, 33, 51);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private ImageView heroImage;
    private TextView heroTitle, heroDescription, statusText;
    private LinearLayout appRail, movieRail, seriesRail;
    private String tmdbKey = "";
    private String selectedPackage = "";
    private String selectedAppName = "";
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
        buildUi();
        loadApps();
        refreshCatalog();
        showFallback("Preparando TV Launcher PG…");
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
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(8,11,18), Color.argb(225,8,11,18), Color.argb(145,8,11,18), Color.argb(90,8,11,18)}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(205,8,11,18), Color.rgb(8,11,18)}));
        root.addView(bottomShade, new FrameLayout.LayoutParams(-1, dp(500), Gravity.BOTTOM));

        ScrollView page = new ScrollView(this); page.setFillViewport(false); page.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(38), dp(22), dp(38), dp(24));
        page.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("PG", 21, white, true); logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff17233a, 0xff263f68});
        logoBg.setCornerRadius(dp(13)); logoBg.setStroke(dp(2), 0xff82b8ff); logo.setBackground(logoBg);
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(48)));
        TextView brand = text("TV LAUNCHER PG", 18, white, true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.leftMargin = dp(13); header.addView(brand, bp);
        View spacer = new View(this); header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        Button systemSettings = button("⚙ Ajustes del TV", false); systemSettings.setOnClickListener(v -> openSystemSettings());
        header.addView(systemSettings, new LinearLayout.LayoutParams(-2, dp(46)));
        Button config = button("Catálogo", true); config.setOnClickListener(v -> showCatalogSettings());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(46)); cp.leftMargin = dp(9); header.addView(config, cp);
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        View gap = new View(this); content.addView(gap, new LinearLayout.LayoutParams(1, dp(55)));
        content.addView(text("DESTACADO PARA VOS", 13, blue, true));
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
        HorizontalScrollView appsScroll = railScroll(); appRail = new LinearLayout(this); appRail.setOrientation(LinearLayout.HORIZONTAL);
        appsScroll.addView(appRail, new HorizontalScrollView.LayoutParams(-2, -1)); content.addView(appsScroll, new LinearLayout.LayoutParams(-1, dp(106)));

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
        GradientDrawable normal = new GradientDrawable(); normal.setCornerRadius(dp(13)); normal.setColor(primary ? 0xff82b8ff : 0xb8293854);
        GradientDrawable focused = new GradientDrawable(); focused.setCornerRadius(dp(13)); focused.setColor(primary ? 0xffb2d4ff : 0xff354b70); focused.setStroke(dp(2), 0xffdceaff);
        StateListDrawable states = new StateListDrawable(); states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[]{}, normal); b.setBackground(states); return b;
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
            LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
            tile.setFocusable(true); tile.setClickable(true); tile.setPadding(dp(7),dp(6),dp(7),dp(6));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(cardColor); bg.setCornerRadius(dp(15)); tile.setBackground(bg);
            ImageView icon = new ImageView(this); try { icon.setImageDrawable(info.loadIcon(pm)); } catch (Exception ignored) { }
            tile.addView(icon, new LinearLayout.LayoutParams(dp(42),dp(42)));
            TextView label = text(name, 11, white, true); label.setGravity(Gravity.CENTER); label.setMaxLines(1); label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(96),-2); np.topMargin = dp(4); tile.addView(label,np);
            tile.setOnFocusChangeListener((v, focused) -> {
                v.setScaleX(focused ? 1.06f : 1f); v.setScaleY(focused ? 1.06f : 1f); v.setElevation(focused ? dp(8) : 0);
                if (focused) selectApp(pkg, name);
            });
            tile.setOnClickListener(v -> launchApp(pkg, cls));
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(112),dp(100)); item.rightMargin = dp(10); appRail.addView(tile,item);
        }
        if (apps.isEmpty()) statusText.setText("No se encontraron aplicaciones iniciables.");
    }

    private void selectApp(String pkg, String name) {
        selectedPackage = pkg; selectedAppName = name;
        heroTitle.setText(name);
        heroDescription.setText("Seleccionaste " + name + ". Explorá las recomendaciones o presioná OK para abrir la aplicación.");
        if (!trending.isEmpty()) loadBackdropForApp(pkg);
        else showFallback("Fondo destacado para " + name);
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
                handler.post(() -> { if (bmp != null && token == backdropToken) heroImage.setImageBitmap(bmp); });
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
        GradientDrawable bg = new GradientDrawable(); bg.setColor(cardColor); bg.setCornerRadius(dp(12)); placeholder.setBackground(bg); placeholder.setPadding(dp(16),0,dp(16),0);
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
            GradientDrawable normal = new GradientDrawable(); normal.setColor(cardColor); normal.setCornerRadius(dp(10));
            GradientDrawable focusedBackground = new GradientDrawable(); focusedBackground.setColor(0xff263f68); focusedBackground.setCornerRadius(dp(10)); focusedBackground.setStroke(dp(2),blue);
            StateListDrawable state = new StateListDrawable(); state.addState(new int[]{android.R.attr.state_focused},focusedBackground); state.addState(new int[]{},normal); card.setBackground(state);
            ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(0xff202a3d);
            card.addView(image,new LinearLayout.LayoutParams(dp(112),dp(125)));
            TextView label = text(title,10,white,true); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112),dp(35)); lp.topMargin=dp(3); card.addView(label,lp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(124),dp(171)); cp.rightMargin=dp(9); rail.addView(card,cp);
            if (!poster.isEmpty()) {
                String imageUrl="https://image.tmdb.org/t/p/w342"+poster;
                executor.execute(() -> { try { Bitmap bmp=getBitmap(imageUrl); handler.post(() -> { if(bmp!=null) image.setImageBitmap(bmp); }); } catch(Exception ignored){} });
            }
            card.setOnFocusChangeListener((v, focused) -> { v.setScaleX(focused?1.04f:1f); v.setScaleY(focused?1.04f:1f); if(focused){heroTitle.setText(title); heroDescription.setText(overview.isEmpty()?"Explorá este título en tus aplicaciones instaladas.":overview); if(!poster.isEmpty()) { final int token=++backdropToken; executor.execute(() -> { try { Bitmap bmp=getBitmap("https://image.tmdb.org/t/p/w1280"+item.optString("backdrop_path", "")); handler.post(() -> { if(bmp!=null && token==backdropToken) heroImage.setImageBitmap(bmp); }); } catch(Exception ignored){} }); } } });
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
        if (!trending.isEmpty()) {
            int index = (int)(System.currentTimeMillis() % trending.size()); JSONObject item = trending.get(index);
            heroTitle.setText(item.optString("title",item.optString("name","Contenido destacado")));
            heroDescription.setText(item.optString("overview","Una recomendación para descubrir."));
            String path=item.optString("backdrop_path","");
            if(!path.isEmpty()) { final int token=++backdropToken; executor.execute(() -> { try { Bitmap bmp=getBitmap("https://image.tmdb.org/t/p/w1280"+path); handler.post(() -> {if(bmp!=null && token==backdropToken) heroImage.setImageBitmap(bmp);}); }catch(Exception ignored){} }); }
        } else showFallback("Fondos cinematográficos online · configurá TMDB para catálogos reales.");
    }

    private void showFallback(String message) {
        final int index = fallbackIndex++ % fallbackImages.length; final int token=++backdropToken;
        executor.execute(() -> { try { Bitmap bmp=getBitmap(fallbackImages[index]); handler.post(() -> { if(bmp!=null && token==backdropToken) heroImage.setImageBitmap(bmp); if(selectedAppName.isEmpty()){ heroTitle.setText(new String[]{"Una noche de cine","Tu próxima aventura","Historias para descubrir","El espectáculo empieza acá","Tu pantalla, tu mundo","Un universo por explorar"}[index]); heroDescription.setText("Fondos cinematográficos online que cambian al elegir otro destacado."); } statusText.setText(message); }); } catch(Exception ignored) { handler.post(() -> statusText.setText("Sin conexión con las imágenes. Revisá internet.")); } });
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(urlText).openConnection(); connection.setConnectTimeout(9000); connection.setReadTimeout(9000); connection.setRequestMethod("GET");
        try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))>0) out.write(buffer,0,n); return new JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8)); }
        finally { connection.disconnect(); }
    }

    private Bitmap getBitmap(String urlText) throws Exception {
        if (urlText == null || urlText.isEmpty()) return null;
        HttpURLConnection connection=(HttpURLConnection)new URL(urlText).openConnection(); connection.setConnectTimeout(9000); connection.setReadTimeout(10000); connection.setRequestProperty("User-Agent","TVLauncherPG/2.0");
        try(InputStream in=connection.getInputStream()) { BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1; return BitmapFactory.decodeStream(in,null,options); }
        finally { connection.disconnect(); }
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
