package com.oasis.firebird.android.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.text.style.ReplacementSpan;

/**
 * Created by Libero Rignanese.
 */


public class MarginSpan extends ReplacementSpan {

    private int mPadding;

    public MarginSpan(int padding) {
        mPadding = padding;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float[] widths = new float[end - start];
        paint.getTextWidths(text, start, end, widths);
        int sum = mPadding;
        for (float width : widths) {
            sum += width;
        }
        return sum;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.drawText(text, start, end, x, y, paint);
    }

}