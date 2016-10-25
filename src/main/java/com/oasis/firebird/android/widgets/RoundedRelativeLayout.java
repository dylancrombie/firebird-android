package com.oasis.firebird.android.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.oasis.firebird.android.R;

public class RoundedRelativeLayout extends RelativeLayout {

    private Path path;
    private float corners;

    public RoundedRelativeLayout(Context context) {
        super(context);

    }

    public RoundedRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        onCreate(context, attrs);
    }

    public RoundedRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        onCreate(context, attrs);
    }

    public RoundedRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        onCreate(context, attrs);
    }

    private void onCreate(Context context, AttributeSet attrs) {

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.RoundedRelativeLayout, 0, 0);

        try {
            corners = a.getDimension(R.styleable.RoundedRelativeLayout_corners, 0);
        } finally {
            a.recycle();
        }

    }

    @Override
     protected void dispatchDraw(Canvas canvas){

        if (path == null) {
            path = new Path();
            path.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), corners, corners, Path.Direction.CW);
        }
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            canvas.clipPath(path);
        }
        super.dispatchDraw(canvas);

    }

}
