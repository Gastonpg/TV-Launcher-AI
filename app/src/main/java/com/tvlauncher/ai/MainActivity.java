package com.tvlauncher.ai;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(15,19,29), panel = Color.rgb(30,38,54);
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28),dp(22),dp(28),dp(20)); root.setBackgroundColor(bg);
        TextView title = new TextView(this); title.setText("TV Launcher AI"); title.setTextColor(Color.WHITE); title.setTextSize(28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(title);
        TextView sub = new TextView(this); sub.setText("Tus aplicaciones, listas para el control remoto"); sub.setTextColor(Color.rgb(174,187,208)); sub.setTextSize(14); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(5); sp.bottomMargin=dp(18); root.addView(sub,sp);
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer = new TextView(this); footer.setText("Android TV Box · versión inicial"); footer.setTextColor(Color.rgb(115,129,151)); footer.setTextSize(12); root.addView(footer); setContentView(root);
        PackageManager pm=getPackageManager(); Intent q=new Intent(Intent.ACTION_MAIN); q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> apps=pm.queryIntentActivities(q,0);
        Collections.sort(apps,new Comparator<android.content.pm.ResolveInfo>() { public int compare(android.content.pm.ResolveInfo a, android.content.pm.ResolveInfo b) { return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))); }});
        for (android.content.pm.ResolveInfo info: apps) {
            final String pkg=info.activityInfo.packageName, cls=info.activityInfo.name; Button b=new Button(this); b.setAllCaps(false); b.setText(String.valueOf(info.loadLabel(pm))); b.setTextSize(17); b.setTextColor(Color.WHITE); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); b.setPadding(dp(18),dp(8),dp(18),dp(8));
            GradientDrawable shape=new GradientDrawable(); shape.setColor(panel); shape.setCornerRadius(dp(14)); b.setBackground(shape); b.setFocusable(true);
            b.setOnClickListener(v->{ try { Intent launch=new Intent(Intent.ACTION_MAIN); launch.setClassName(pkg,cls); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); } catch(Exception e) { try { Intent fallback=pm.getLaunchIntentForPackage(pkg); if(fallback!=null) startActivity(fallback); } catch(Exception ignored) {} } });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58)); lp.bottomMargin=dp(9); list.addView(b,lp);
        }
        if(list.getChildCount()>0) list.getChildAt(0).requestFocus();
    }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
