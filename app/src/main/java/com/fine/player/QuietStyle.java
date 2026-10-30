package com.fine.player;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.*;
final class QuietStyle {
    static final int PAPER=Color.rgb(247,248,247), INK=Color.rgb(40,48,46), ACCENT=Color.rgb(61,109,95);
    static final int MUTED=Color.rgb(113,122,118), SOFT=Color.rgb(232,239,235), RED=Color.rgb(158,78,72);
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable panel(Context c,int color,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,12));
        if(stroke>0)d.setStroke(dp(c,1),Color.rgb(221,227,223));return d;
    }
    static void button(Button b,int color,int foreground){
        b.setAllCaps(false);b.setTextSize(14);b.setTextColor(foreground);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777),panel(b.getContext(),color,0),null));
        b.setStateListAnimator(null);b.setElevation(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(4,0,4,0);
    }
    static void input(EditText e){
        e.setTextColor(INK);e.setHintTextColor(MUTED);e.setBackgroundTintList(null);e.setBackground(panel(e.getContext(),PAPER,1));
        e.setPadding(dp(e.getContext(),12),0,dp(e.getContext(),12),0);
    }
}
