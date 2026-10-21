package com.tvlauncher.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Floating refractive-looking glass numerals; deliberately has no panel/background. */
public final class GlassClockView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public GlassClockView(Context context) {
        super(context);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Reloj");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        String time = format.format(new Date());
        float size = 35f * getResources().getDisplayMetrics().scaledDensity;
        fill.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        fill.setTextSize(size);
        fill.setTextAlign(Paint.Align.CENTER);
        edge.setTypeface(fill.getTypeface());
        edge.setTextSize(size);
        edge.setTextAlign(Paint.Align.CENTER);
        float x = getWidth() / 2f;
        float y = (getHeight() - fill.ascent() - fill.descent()) / 2f;

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);
        fill.setShadowLayer(5f * getResources().getDisplayMetrics().density, 0, 1f, 0x99ffffff);
        canvas.drawText(time, x, y, fill);
        fill.clearShadowLayer();
        fill.setShader(new LinearGradient(0, y - size, 0, y + size * 0.18f,
                new int[]{0xffffffff, 0xffe5f5ff, 0x99ffffff, 0xffc6d9ed},
                new float[]{0f, 0.24f, 0.57f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawText(time, x, y, fill);
        fill.setShader(null);

        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.65f * getResources().getDisplayMetrics().density);
        edge.setColor(0x99ffffff);
        canvas.drawText(time, x, y - 0.6f, edge);
        edge.setStyle(Paint.Style.FILL);
    }
}