package com.tvbox.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
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
        info.setText("Agente de diagnóstico de solo lectura para TV Launcher PG. Funciona dentro de tu red local; no lo expongas a Internet.");
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
            status.setText("Agente iniciado. Puerto 8765. Mantén esta app instalada y el dispositivo conectado a tu Wi‑Fi.");
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
}
