package io.github.urtuba.tvask;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

// LevelMeter draws the voice level as a row of small square blocks. The
// level rises fast and falls slowly, so the row does not flicker between
// words. The first BASE blocks always stay lit, so it never looks dead.
final class LevelMeter extends View {
    private static final int MAX_BLOCKS = 48;
    private static final int BASE = 3;
    private static final float RISE = 0.3f;
    private static final float FALL = 0.07f;

    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float level, target;

    LevelMeter(Context c) {
        super(c);
        off.setColor(Color.argb(50, 255, 255, 255));
    }

    // setLevel sets the target level, from 0 for silence to 1 for loud.
    void setLevel(float v) {
        target = Math.max(0f, Math.min(1f, v));
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        level += (target - level) * (target > level ? RISE : FALL);
        if (Math.abs(target - level) > 0.002f) {
            postInvalidateOnAnimation();
        }
        float size = getHeight();
        float gap = size / 2;
        int n = Math.min(MAX_BLOCKS, (int) ((getWidth() + gap) / (size + gap)));
        float lit = BASE + level * (n - BASE);
        float corner = size / 4;
        for (int i = 0; i < n; i++) {
            float x = i * (size + gap);
            r.set(x, 0, x + size, size);
            float a = Math.max(0f, Math.min(1f, lit - i));
            if (a < 1f) {
                canvas.drawRoundRect(r, corner, corner, off);
            }
            if (a > 0f) {
                on.setColor(Color.rgb(255, 210, 90));
                on.setAlpha((int) (255 * a));
                canvas.drawRoundRect(r, corner, corner, on);
            }
        }
    }
}
