package com.fine.player;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small consistent stroke icons, independent of system fonts. */
final class BrowserIcon extends Drawable {
    private final String name;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BrowserIcon(String name) { this.name = name; }
    @Override public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        paint.setColor(isStateEnabled() ? QuietStyle.INK : 0xffc4c8c6);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        switch (name) {
            case "back": line(canvas, 14, 5, 7, 12, 14, 19); break;
            case "forward": line(canvas, 10, 5, 17, 12, 10, 19); break;
            case "home": line(canvas, 3, 10, 12, 3, 21, 10); line(canvas, 6, 9, 6, 21, 10, 21, 10, 15, 14, 15, 14, 21, 18, 21, 18, 9); break;
            case "menu": line(canvas, 4, 6, 20, 6); line(canvas, 4, 12, 20, 12); line(canvas, 4, 18, 20, 18); break;
            case "search": canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); line(canvas, 16, 16, 21, 21); break;
            case "reload": canvas.drawArc(4, 4, 20, 20, 40, 290, false, paint); line(canvas, 20, 4, 20, 10, 14, 10); break;
            case "stop": line(canvas, 6, 6, 18, 18); line(canvas, 18, 6, 6, 18); break;
            case "go": line(canvas, 5, 12, 20, 12); line(canvas, 14, 6, 20, 12, 14, 18); break;
            case "media": canvas.drawRoundRect(3, 5, 21, 19, 3, 3, paint); line(canvas, 10, 9, 15, 12, 10, 15, 10, 9); break;
            case "download": line(canvas, 12, 3, 12, 15); line(canvas, 7, 10, 12, 15, 17, 10); line(canvas, 4, 16, 4, 21, 20, 21, 20, 16); break;
            case "copy": canvas.drawRoundRect(8, 8, 21, 21, 2, 2, paint); line(canvas, 16, 4, 4, 4, 4, 16); break;
            case "settings": canvas.drawCircle(12, 12, 7, paint); canvas.drawCircle(12, 12, 2, paint); for (int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,12,12);line(canvas,12,2,12,5);canvas.restore();} break;
            default: canvas.drawRoundRect(4, 4, 20, 20, 3, 3, paint);
        }
        if (name.startsWith("tabs:")) {
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(11); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(name.substring(5), 12, 16, paint);
        }
        canvas.restore();
    }
    private boolean isStateEnabled() { for (int state : getState()) if (state == android.R.attr.state_enabled) return true; return false; }
    private void line(Canvas canvas, float... coordinates) {
        Path path = new Path(); path.moveTo(coordinates[0], coordinates[1]);
        for(int i=2;i<coordinates.length;i+=2) path.lineTo(coordinates[i], coordinates[i+1]);
        canvas.drawPath(path, paint);
    }
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}
