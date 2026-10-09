package com.zecadev.outervoice;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Strong, bounded recording pulse; every exit path can stop it safely. */
final class HoldFeedback {
    private static final WeakHashMap<View, ValueAnimator> pulses = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> observed = new WeakHashMap<>();
    static void start(TextView view) {
        if (view == null) return; stop(view, false); view.animate().cancel();
        Halo halo = new Halo(view.getCurrentTextColor()); view.setForeground(halo);
        if (!ValueAnimator.areAnimatorsEnabled()) { halo.phase=.5f; halo.invalidateSelf(); return; }
        ValueAnimator pulse=ValueAnimator.ofFloat(0,1,0); pulse.setDuration(1000); pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.addUpdateListener(a->{float p=(float)a.getAnimatedValue();view.setScaleX(1-.12f*p);view.setScaleY(1-.12f*p);halo.phase=p;halo.invalidateSelf();});
        pulses.put(view,pulse); pulse.start();
        if (!observed.containsKey(view)) { observed.put(view, true); view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v) { }
            public void onViewDetachedFromWindow(View v) { stop(v,false); observed.remove(v); v.removeOnAttachStateChangeListener(this); }
        }); }
    }
    static void stop(View view) { stop(view,true); }
    static void stop(View view,boolean smooth) {
        if(view==null)return;ValueAnimator pulse=pulses.remove(view);if(pulse!=null)pulse.cancel();view.setForeground(null);
        view.animate().cancel();
        if(smooth&&ValueAnimator.areAnimatorsEnabled())view.animate().scaleX(1).scaleY(1).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        else{view.setScaleX(1);view.setScaleY(1);}
    }
    private static final class Halo extends Drawable {
        final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);float phase;
        Halo(int color){pen.setColor(color);pen.setAlpha(217);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(4);}
        public void draw(Canvas c){float radius=Math.min(getBounds().width(),getBounds().height())/2f-6;c.drawCircle(getBounds().centerX(),getBounds().centerY(),radius*(.75f+.25f*phase),pen);}
        public void setAlpha(int alpha){pen.setAlpha(alpha);invalidateSelf();}
        public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);}
        public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
