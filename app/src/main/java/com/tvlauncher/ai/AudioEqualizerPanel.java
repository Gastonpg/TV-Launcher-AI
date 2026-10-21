package com.tvlauncher.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.audiofx.Equalizer;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** Uses the device's Android audio Equalizer effect; session 0 support varies by firmware. */
public final class AudioEqualizerPanel {
    private static Equalizer equalizer;
    private AudioEqualizerPanel() {}

    public static void restore(Context context) {
        try {
            if (!context.getSharedPreferences("tvpg", Context.MODE_PRIVATE).getBoolean("eq_enabled", false)) return;
            ensureEffect(context);
            if (equalizer != null) equalizer.setEnabled(true);
        } catch (Exception ignored) { }
    }

    public static void show(Activity activity) {
        final android.content.SharedPreferences prefs = activity.getSharedPreferences("tvpg", Context.MODE_PRIVATE);
        try {
            ensureEffect(activity);
        } catch (Exception ex) {
            new AlertDialog.Builder(activity)
                    .setTitle("Ecualizador de audio")
                    .setMessage("Este firmware no expone un ecualizador global compatible. No vamos a mostrar controles falsos: PCM por sí solo no garantiza que Android permita procesar el audio de otras aplicaciones.")
                    .setPositiveButton("Entendido", null).show();
            return;
        }
        if (equalizer == null) {
            Toast.makeText(activity, "El sistema no ofrece un ecualizador compatible.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 22), dp(activity, 12), dp(activity, 22), dp(activity, 4));
        box.setBackgroundColor(0xff141b27);

        TextView description = new TextView(activity);
        description.setText("Ajuste global del sistema · la disponibilidad depende del firmware y de cada salida de audio.");
        description.setTextColor(0xffcbd8e8);
        description.setTextSize(12);
        box.addView(description, new LinearLayout.LayoutParams(-1, -2));

        Switch toggle = new Switch(activity);
        toggle.setText("Ecualizador activado");
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(14);
        toggle.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        box.addView(toggle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bands = new LinearLayout(activity);
        bands.setOrientation(LinearLayout.HORIZONTAL);
        bands.setGravity(Gravity.CENTER);
        box.addView(bands, new LinearLayout.LayoutParams(-1, dp(activity, 190)));

        short bandCount = equalizer.getNumberOfBands();
        short[] range = equalizer.getBandLevelRange();
        for (short i = 0; i < bandCount; i++) {
            final short band = i;
            int centerMilliHz = equalizer.getCenterFreq(i);
            float hz = centerMilliHz / 1000f;
            String label = hz >= 1000 ? String.format(Locale.getDefault(), "%.1f kHz", hz / 1000f) : Math.round(hz) + " Hz";
            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1f);
            cp.setMargins(dp(activity, 3), 0, dp(activity, 3), 0);
            bands.addView(column, cp);

            TextView value = new TextView(activity);
            value.setTextColor(0xffeef6ff);
            value.setTextSize(10);
            value.setGravity(Gravity.CENTER);
            column.addView(value, new LinearLayout.LayoutParams(-1, dp(activity, 22)));

            SeekBar seek = new SeekBar(activity);
            seek.setMax(12);
            short saved = (short) prefs.getInt("eq_band_" + i, 0);
            int db = Math.max(-6, Math.min(6, saved));
            seek.setProgress(db + 6);
            LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, 0, 1f);
            column.addView(seek, seekParams);

            TextView freq = new TextView(activity);
            freq.setText(label);
            freq.setTextColor(0xffcbd8e8);
            freq.setTextSize(10);
            freq.setGravity(Gravity.CENTER);
            column.addView(freq, new LinearLayout.LayoutParams(-1, dp(activity, 28)));

            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    int dbValue = progress - 6;
                    value.setText((dbValue > 0 ? "+" : "") + dbValue + " dB");
                    try {
                        int millibels = Math.max(range[0], Math.min(range[1], dbValue * 100));
                        equalizer.setBandLevel(band, (short) millibels);
                        prefs.edit().putInt("eq_band_" + band, dbValue).apply();
                    } catch (Exception ignored) { }
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            value.setText((db > 0 ? "+" : "") + db + " dB");
            try { equalizer.setBandLevel(band, (short)Math.max(range[0], Math.min(range[1], db * 100))); } catch (Exception ignored) { }
        }

        toggle.setChecked(prefs.getBoolean("eq_enabled", false));
        setBandsEnabled(bands, toggle.isChecked());
        toggle.setOnCheckedChangeListener((CompoundButton button, boolean checked) -> {
            try {
                equalizer.setEnabled(checked);
                prefs.edit().putBoolean("eq_enabled", checked).apply();
                setBandsEnabled(bands, checked);
            } catch (Exception ex) {
                button.setChecked(false);
                Toast.makeText(activity, "No se pudo activar el efecto de audio.", Toast.LENGTH_LONG).show();
            }
        });

        new AlertDialog.Builder(activity)
                .setTitle("Ecualizador · TV Launcher PG")
                .setView(box)
                .setNeutralButton("Plano", (d, w) -> {
                    try {
                        short count = equalizer.getNumberOfBands();
                        short[] r = equalizer.getBandLevelRange();
                        for (short i = 0; i < count; i++) {
                            equalizer.setBandLevel(i, (short)Math.max(r[0], Math.min(r[1], 0)));
                            prefs.edit().putInt("eq_band_" + i, 0).apply();
                        }
                        Toast.makeText(activity, "Ecualizador en plano.", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) { }
                })
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private static void ensureEffect(Context context) {
        if (equalizer != null) return;
        equalizer = new Equalizer(0, 0);
        equalizer.setEnabled(context.getSharedPreferences("tvpg", Context.MODE_PRIVATE).getBoolean("eq_enabled", false));
    }

    private static void setBandsEnabled(View view, boolean enabled) {
        view.setAlpha(enabled ? 1f : 0.45f);
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) setBandsEnabled(group.getChildAt(i), enabled);
        }
    }

    private static int dp(Context c, float value) {
        return (int)(value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}