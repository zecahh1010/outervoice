package com.zecadev.outervoice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Local vector emotions, matching the approved simulator; no emoji-font dependency. */
final class SoundIcon extends TextView {
    private final int icon;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    SoundIcon(Context context, Typeface font, int icon, boolean live, int pixels, int color) {
        super(context); this.icon = live ? -1 : icon;
        setTypeface(font); setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels);
        setIncludeFontPadding(false); setGravity(Gravity.CENTER);
        setText(live ? FloatingConfig.MIC_ICON : FloatingConfig.ICONS[icon]);
    }
    @Override protected void onDraw(Canvas canvas) {
        if (icon < 3) { super.onDraw(canvas); return; }
        canvas.save(); float unit = getTextSize() / 32f;
        canvas.translate(getWidth() / 2f - 16 * unit, getHeight() / 2f - 16 * unit); canvas.scale(unit, unit);
        pen.setColor(getCurrentTextColor()); pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(2);
        pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
        if (icon <= 9) canvas.drawCircle(16, 16, 13, pen);
        switch (icon) {
            case 3: // Funny
                lines(canvas, 7,13,11,9,14,13); lines(canvas,18,13,21,9,25,13);
                quad(canvas,8,18,16,34,24,18); lines(canvas,8,18,24,18); lines(canvas,10,21,22,21); break;
            case 4: // Extreme Angry
                lines(canvas,6,8,13,12); lines(canvas,26,8,19,12); lines(canvas,9,14,12,14); lines(canvas,20,14,23,14);
                canvas.drawRoundRect(7,19,25,25,2,2,pen); lines(canvas,13,19,13,25); lines(canvas,19,19,19,25); lines(canvas,10,22,22,22); break;
            case 5: dot(canvas,11,13); dot(canvas,21,13); quad(canvas,9,19,16,28,23,19); break;
            case 6: quad(canvas,7,13,11,8,15,13); dot(canvas,22,13); quad(canvas,9,20,16,27,23,19); break;
            case 7:
                lines(canvas,8,10,13,8); lines(canvas,19,8,24,10); dot(canvas,11,14); dot(canvas,21,14);
                quad(canvas,10,24,16,17,22,24); canvas.drawOval(4,17,8,23,pen); break;
            case 8: canvas.drawCircle(11,12,2,pen); canvas.drawCircle(21,12,2,pen); canvas.drawOval(12,17,20,27,pen); break;
            case 9: quad(canvas,7,13,11,17,15,13); quad(canvas,18,13,22,17,26,13); quad(canvas,12,22,16,25,20,22); break;
            case 10:
                lines(canvas,11,3,3,11,3,21,11,29,21,29,29,21,29,11,21,3,11,3);
                lines(canvas,16,9,16,19); dot(canvas,16,24); break;
            case 11:
                lines(canvas,4,28,9,11,21,23,4,28); lines(canvas,6,21,12,26); lines(canvas,9,16,17,24);
                lines(canvas,16,4,17,9); lines(canvas,23,4,20,12); lines(canvas,29,14,23,16); lines(canvas,14,11,21,18);
                dot(canvas,27,5); dot(canvas,27,23); break;
        }
        canvas.restore();
    }
    private void lines(Canvas c, float... xy) { Path p = new Path(); p.moveTo(xy[0],xy[1]); for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]); c.drawPath(p,pen); }
    private void quad(Canvas c,float x,float y,float cx,float cy,float ex,float ey) { Path p=new Path(); p.moveTo(x,y);p.quadTo(cx,cy,ex,ey);c.drawPath(p,pen); }
    private void dot(Canvas c,float x,float y) { pen.setStyle(Paint.Style.FILL);c.drawCircle(x,y,1,pen);pen.setStyle(Paint.Style.STROKE); }
}
