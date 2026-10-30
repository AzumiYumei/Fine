package com.fine.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.*;

/** Small native drawing primitives; no image assets or additional UI libraries. */
final class Bauhaus {
    static final int PAPER = Color.rgb(244, 239, 228);
    static final int INK = Color.rgb(25, 25, 25);
    static final int BLUE = Color.rgb(31, 70, 184);
    static final int RED = Color.rgb(198, 45, 35);
    static final int YELLOW = Color.rgb(244, 196, 48);
    static final int MUTED = Color.rgb(98, 94, 86);
    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static GradientDrawable panel(Context context, int fill, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        if (stroke > 0) background.setStroke(dp(context, stroke), INK);
        return background;
    }
    static void button(Button button, int fill, int foreground) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{MUTED, foreground}));
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33777777), panel(context, fill, 1), null));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 4), 0, dp(context, 4), 0);
    }
    static void input(EditText input) {
        input.setTextColor(INK); input.setHintTextColor(MUTED);
        input.setBackgroundTintList(null);
        input.setBackground(panel(input.getContext(), PAPER, 1));
        input.setPadding(dp(input.getContext(), 12), 0, dp(input.getContext(), 12), 0);
    }
    static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }
    static LinearLayout header(Context context, String number, String heading, String description) {
        LinearLayout column = new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
        TextView index = text(context, number + "   /   FINE 轻播", 12, INK, true);
        index.setLetterSpacing(.1f);
        column.addView(index);
        LinearLayout brand = new LinearLayout(context); brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView wordmark = text(context, "fine.", 64, INK, true);
        wordmark.setLetterSpacing(-.055f); wordmark.setIncludeFontPadding(false);
        brand.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        brand.addView(new Geometry(context), new LinearLayout.LayoutParams(dp(context, 128), dp(context, 104)));
        LinearLayout.LayoutParams brandSize = new LinearLayout.LayoutParams(-1, -2); brandSize.topMargin = dp(context, 12);
        column.addView(brand, brandSize);
        View rule = new View(context); rule.setBackgroundColor(INK);
        LinearLayout.LayoutParams lineSize = new LinearLayout.LayoutParams(-1, dp(context, 3)); lineSize.topMargin = dp(context, 12);
        column.addView(rule, lineSize);
        TextView title = text(context, heading, 32, INK, true);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, -2); titleSize.topMargin = dp(context, 16);
        column.addView(title, titleSize);
        TextView subtitle = text(context, description, 13, MUTED, false);
        LinearLayout.LayoutParams subSize = new LinearLayout.LayoutParams(-1, -2); subSize.topMargin = dp(context, 6);
        column.addView(subtitle, subSize);
        return column;
    }
    private static final class Geometry extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();
        Geometry(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save(); canvas.scale(getWidth() / 128f, getHeight() / 104f);
            paint.setColor(YELLOW); canvas.drawRect(54, 0, 128, 74, paint);
            paint.setColor(RED); canvas.drawCircle(39, 38, 34, paint);
            paint.setColor(BLUE); triangle.reset(); triangle.moveTo(53, 46); triangle.lineTo(125, 46); triangle.lineTo(89, 103); triangle.close(); canvas.drawPath(triangle, paint);
            paint.setColor(INK); canvas.drawRect(5, 88, 49, 94, paint);
            canvas.restore();
        }
    }
}
