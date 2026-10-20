package com.gastonpg.tvboxagentconnect;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    EditText host, token;
    TextView status, output;
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(Color.rgb(12,18,31));
        getWindow().setNavigationBarColor(Color.rgb(12,18,31));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(16));
        root.setBackgroundColor(Color.rgb(12,18,31));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this); title.setText("TV Box Agent Connect"); title.setTextColor(Color.WHITE); title.setTextSize(25); title.setTypeface(null,Typeface.BOLD);
        content.addView(title);
        TextView sub = new TextView(this); sub.setText("Conexión local · Diagnóstico de solo lectura"); sub.setTextColor(Color.LTGRAY); sub.setTextSize(14); sub.setPadding(0,dp(5),0,dp(18)); content.addView(sub);
        host = new EditText(this); host.setSingleLine(true); host.setText("http://192.168.110.35:8765"); host.setHint("Dirección del agente"); host.setTextColor(Color.WHITE); host.setHintTextColor(Color.GRAY); host.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(100,160,255))); content.addView(host);
        token = new EditText(this); token.setSingleLine(true); token.setHint("Pegá el token del TV Box Agent"); token.setTextColor(Color.WHITE); token.setHintTextColor(Color.GRAY); token.setInputType(129); token.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(100,160,255))); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(8);content.addView(token,tp);
        TextView note = new TextView(this); note.setText("El token queda en este celular. No lo compartas. Ambos dispositivos deben estar en la misma red Wi‑Fi."); note.setTextColor(Color.LTGRAY); note.setTextSize(12); note.setPadding(0,dp(8),0,dp(12)); content.addView(note);
        status = new TextView(this); status.setText("Listo para conectar"); status.setTextColor(Color.rgb(125,205,160)); status.setPadding(0,dp(5),0,dp(10)); content.addView(status);
        String[][] endpoints={{"Probar conexión","/v1/health"},{"Información del dispositivo","/v1/device"},{"Launchers","/v1/launchers"},{"Paquetes instalados","/v1/packages"},{"Diagnóstico","/v1/diagnostics"}};
        for(String[] ep:endpoints){ Button btn=new Button(this); btn.setText(ep[0]); btn.setAllCaps(false); btn.setTextColor(Color.WHITE); btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(36,70,112))); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(7);content.addView(btn,p);btn.setOnClickListener(v->request(ep[1]));}
        output = new TextView(this); output.setTextColor(Color.rgb(225,233,245)); output.setTextSize(13); output.setTextIsSelectable(true); output.setPadding(dp(12),dp(12),dp(12),dp(12)); output.setBackgroundColor(Color.rgb(22,30,46)); LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.topMargin=dp(8);content.addView(output,op);
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    void request(String path) {
        String base=host.getText().toString().trim().replaceAll("/+$","");
        String tok=token.getText().toString().trim();
        if(base.isEmpty()||tok.isEmpty()){status.setText("Completá la dirección y el token.");return;}
        status.setText("Consultando "+path+"…"); output.setText("");
        executor.execute(()->{
            HttpURLConnection c=null;
            try {
                URL u=new URL(base+path); c=(HttpURLConnection)u.openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(7000); c.setReadTimeout(10000); c.setRequestProperty("Authorization","Bearer "+tok); c.setRequestProperty("Accept","application/json");
                int code=c.getResponseCode(); InputStream is=(code>=200&&code<400)?c.getInputStream():c.getErrorStream();
                ByteArrayOutputStream bos=new ByteArrayOutputStream(); byte[] buf=new byte[4096];int n;while((n=is.read(buf))!=-1)bos.write(buf,0,n);
                String body=new String(bos.toByteArray(),StandardCharsets.UTF_8);
                final String result="HTTP "+code+"\n\n"+body;
                runOnUiThread(()->{status.setText(code>=200&&code<300?"Conexión correcta":"El agente respondió con un error");output.setText(result);});
            } catch(Exception e){runOnUiThread(()->{status.setText("No se pudo conectar");output.setText(e.getClass().getSimpleName()+": "+e.getMessage()+"\n\nRevisá que el agente esté iniciado y ambos equipos estén en la misma red.");});}
            finally{if(c!=null)c.disconnect();}
        });
    }
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
