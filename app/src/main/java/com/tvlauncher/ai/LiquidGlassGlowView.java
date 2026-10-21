package com.tvlauncher.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Lightweight white liquid-glass halo for the focused app card. */
public final class LiquidGlassGlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public LiquidGlassGlowView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(false);
        setClickable(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float inset = 8f * d;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = 15f * d;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * d);
        paint.setColor(0x99ffffff);
        paint.setShadowLayer(13f * d, 0, 0, 0x99ffffff);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.clearShadowLayer();
        paint.setStrokeWidth(1f * d);
        paint.setColor(0x77ffffff);
        canvas.drawRoundRect(rect, radius, radius, paint);
        rect.inset(2.5f * d, 2.5f * d);
        paint.setStrokeWidth(0.8f * d);
        paint.setColor(0x44eaf5ff);
        canvas.drawRoundRect(rect, radius - 2f * d, radius - 2f * d, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}