package com.gastonpg.replicagoogletv;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.content.*;
import android.content.pm.*;
import java.util.*;

public class MainActivity extends Activity {
    private TVScreen screen;
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().getDecorView().setSystemUiVisibility(5894|1024|512); screen=new TVScreen(this); setContentView(screen); }
    @Override public void onBackPressed() { if(screen.tab>0){screen.tab=0;screen.invalidate();} else super.onBackPressed(); }

    class TVScreen extends View {
        Canvas c; Paint p=new Paint(3); int selected=0, tab=0, scroll=0; final String[] tabs={"Buscar","Para ti","Películas","Series","Apps","Biblioteca"};
        final int bg=Color.rgb(9,12,19), white=Color.rgb(248,249,252), muted=Color.rgb(165,174,190);
        ArrayList<ResolveInfo> apps=new ArrayList<>();
        TVScreen(Context ctx){super(ctx);setFocusable(true);setFocusableInTouchMode(true);refreshApps();requestFocus();}
        void refreshApps(){apps.clear(); Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);List<ResolveInfo> a=getPackageManager().queryIntentActivities(i,0);if(a.isEmpty()){i.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);i.addCategory(Intent.CATEGORY_LAUNCHER);a=getPackageManager().queryIntentActivities(i,0);} for(ResolveInfo r:a){if(!r.activityInfo.packageName.equals(getPackageName()))apps.add(r);} Collections.sort(apps,(x,y)->x.loadLabel(getPackageManager()).toString().compareToIgnoreCase(y.loadLabel(getPackageManager()).toString()));}
        protected void onDraw(Canvas canvas){super.onDraw(canvas); c=canvas; c.save(); c.scale(getWidth()/1920f,getHeight()/1080f);
            p.setShader(new LinearGradient(0,0,1920,1080,new int[]{Color.rgb(18,25,42),Color.rgb(10,13,21),Color.rgb(5,7,12)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1920,1080,p);p.setShader(null);
            // cinematic ambient glow
            p.setShader(new RadialGradient(1380,290,760,new int[]{0x553e5d8e,0x1b263957,0x00000000},null,Shader.TileMode.CLAMP));c.drawRect(600,0,1920,750,p);p.setShader(null);
            glass(48,35,1824,95,28,0x1bffffff,0x26ffffff);
            // GP logo tile
            glass(70,51,64,62,18,0x2cffffff,0x66ffffff);text("GP",102,91,30,white,true);
            text("Gastón Puig",151,89,29,white,true);
            String[] top={"⌕  Buscar","Para ti","Películas","Series","Apps","Biblioteca"};float x=445;
            for(int j=0;j<top.length;j++){float w=j==0?150: j==5?150:132; if(j==tab){glass(x-14,53,w,58,18,0x26ffffff,0x70ffffff);text(top[j],x+5,89,23,white,true);}else text(top[j],x+5,89,23,muted,false);x+=w+12;}
            glass(1750,51,66,62,20,0x20ffffff,0x55ffffff);text("⚙",1783,91,30,white,false);
            // Hero area
            p.setShader(new LinearGradient(700,140,1800,650,new int[]{0x00000000,0x2d8da4cb,0x885b7199},null,Shader.TileMode.CLAMP));Path hero=new Path();hero.moveTo(60,165);hero.lineTo(1860,165);hero.lineTo(1860,590);hero.quadTo(1860,620,1825,620);hero.lineTo(95,620);hero.quadTo(60,620,60,585);hero.close();c.drawPath(hero,p);p.setShader(null);
            glass(60,165,1800,455,30,0x0fffffff,0x25ffffff);
            // abstract cinematic artwork on right, no bundled poster assets in this first base
            p.setShader(new LinearGradient(1050,175,1840,610,new int[]{0x003b4e75,0x553f618a,0x88404d70},null,Shader.TileMode.CLAMP));c.drawRoundRect(1000,178,1845,605,25,25,p);p.setShader(null);
            p.setShader(new RadialGradient(1500,350,400,new int[]{0x557b9fc9,0x22334b72,0x00304060},null,Shader.TileMode.CLAMP));c.drawCircle(1510,355,400,p);p.setShader(null);
            text("TU UNIVERSO",112,255,19,0xffc8d8ef,true);
            text(tab==0?"Todo lo que querés ver.":"Descubrí tu próximo favorito.",112,330,48,white,true);
            text("Películas, series y aplicaciones en un solo lugar.",114,376,24,0xffd0d8e5,false);
            glass(112,420,205,64,21,0xeef8faff,0xffffffff);text("▶  Explorar",145,461,23,0xff111827,true);
            glass(334,420,185,64,21,0x22ffffff,0x66ffffff);text("＋  Mi lista",369,461,23,white,true);
            text("Seguir viendo",66,690,30,white,true);
            text("Tus apps",66,866,28,white,true);
            drawContinueCards();
            drawApps();
            c.restore();
        }
        void drawContinueCards(){String[] labels={"Continuar viendo","Descubrí contenido","Tu próxima historia"};for(int i=0;i<3;i++){float x=65+i*390;glass(x,715,360,112,20,0x22ffffff,0x38ffffff);int[] shades={0x553f5b83,0x554b526e,0x554f6c77};p.setShader(new LinearGradient(x,715,x+360,827,new int[]{shades[i],0x22203040},null,Shader.TileMode.CLAMP));c.drawRoundRect(x+1,716,x+359,826,19,19,p);p.setShader(null);text(labels[i],x+20,777,21,white,true);text("Abrí una app para empezar",x+20,807,16,muted,false);}}
        void drawApps(){int count=Math.min(7,Math.max(0,apps.size()));if(count==0){for(int j=0;j<5;j++){float x=65+j*255;card(x,890,225,115,j==selected,"Apps instaladas",j==0?"Abrir configuración":"");}return;}for(int j=0;j<count;j++){float x=65+j*255;boolean focus=j==selected;glass(x,888,225,120,22,focus?0x35ffffff:0x17ffffff,focus?0xfff7fbff:0x38ffffff);if(focus){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(0xfff9fbff);c.drawRoundRect(x+2,890, x+223,1006,21,21,p);p.setStyle(Paint.Style.FILL);}Drawable d=apps.get(j).loadIcon(getPackageManager());d.setBounds((int)x+18,906,(int)x+70,958);d.draw(c);text(ellipsize(apps.get(j).loadLabel(getPackageManager()).toString(),15),x+18,983,19,white,focus);}}
        String ellipsize(String s,int n){return s.length()>n?s.substring(0,n-1)+"…":s;}
        void glass(float x,float y,float w,float h,float r,int fill,int stroke){p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRoundRect(x,y,x+w,y+h,r,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setColor(stroke);c.drawRoundRect(x,y,x+w,y+h,r,r,p);p.setStyle(Paint.Style.FILL);}
        void text(String s,float x,float y,float size,int color,boolean bold){p.setShader(null);p.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.NORMAL):Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(size);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
        void card(float x,float y,float w,float h,boolean f,String a,String b){glass(x,y,w,h,20,f?0x35ffffff:0x17ffffff,f?white:0x38ffffff);text(a,x+16,y+46,20,white,true);text(b,x+16,y+75,16,muted,false);}
        @Override public boolean onKeyDown(int key,KeyEvent e){switch(key){case KeyEvent.KEYCODE_DPAD_LEFT: if(selected>0)selected--;invalidate();return true;case KeyEvent.KEYCODE_DPAD_RIGHT:selected=Math.min(Math.max(0,apps.size()-1),selected+1);invalidate();return true;case KeyEvent.KEYCODE_DPAD_UP: if(selected>=0){tab=(tab+5)%6;invalidate();}return true;case KeyEvent.KEYCODE_DPAD_DOWN:tab=(tab+1)%6;invalidate();return true;case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_DPAD_CENTER:launchSelected();return true;case KeyEvent.KEYCODE_MENU: startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));return true;default:return super.onKeyDown(key,e);}}
        void launchSelected(){if(selected>=0&&selected<apps.size()){ResolveInfo r=apps.get(selected);Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);i.setClassName(r.activityInfo.packageName,r.activityInfo.name);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(i);}catch(Exception ignored){}}}
    }
}