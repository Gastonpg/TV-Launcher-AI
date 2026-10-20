package com.tvbox.agent;

import android.app.Activity;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 24, 28, 24);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("TV Box Agent");
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Diagnóstico de solo lectura para TV Launcher PG. Solo red local; no expongas este agente a Internet.");
        info.setTextSize(16);
        info.setTextColor(0xFFD7E2EA);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = 14;
        root.addView(info, ip);

        status = new TextView(this);
        status.setText("Servidor detenido. Pulsa Iniciar.");
        status.setTextSize(16);
        status.setTextColor(0xFF80CBC4);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 20;
        root.addView(status, sp);

        Button start = new Button(this);
        start.setText("Iniciar agente");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 18;
        root.addView(start, bp);
        start.setOnClickListener(v -> {
            Intent i = new Intent(this, AgentService.class);
            i.setAction(AgentService.ACTION_START);
            startForegroundService(i);
            status.setText("Iniciando servidor...");
            handler.postDelayed(this::showConnectionInfo, 1000);
        });

        Button stop = new Button(this);
        stop.setText("Detener agente");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = 8;
        root.addView(stop, tp);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, AgentService.class));
            status.setText("Servidor detenido.");
        });

        setContentView(root);
    }

    private void showConnectionInfo() {
        String ip = "IP no disponible";
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            int v = wi.getIpAddress();
            if (v != 0) ip = (v & 255) + "." + ((v >> 8) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 24) & 255);
        } catch (Exception ignored) {}
        String token = getSharedPreferences("agent", MODE_PRIVATE).getString("token", "");
        status.setText("AGENTE ACTIVO\nDirección: http://" + ip + ":8765\nPuerto: 8765\nToken: " +
                (token.isEmpty() ? "iniciando..." : token) +
                "\n\nNo compartas el token públicamente. Los datos se consultan desde un dispositivo en la misma red Wi‑Fi.");
    }
}
