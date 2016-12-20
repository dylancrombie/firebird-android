package com.oasis.firebird.android.transformer;

import android.support.v4.view.ViewPager;
import android.view.View;

public class StackTransformer implements ViewPager.PageTransformer {
    private static final float MIN_SCALE = 0.75F;

    public StackTransformer() {
    }

    protected void onTransform(View view, float position) {
        if(position <= 0.0F) {
            view.setTranslationX(0.0F);
            view.setScaleX(1.0F);
            view.setScaleY(1.0F);
        } else if(position <= 4.0F) {
            float scaleFactor = 0.98F + 0.02F * (1.0F - Math.abs(position));
            view.setAlpha(1.0F);
            view.setPivotY(0.5F * (float) view.getHeight());
            view.setPivotX(0.5F * (float) view.getWidth());
            view.setTranslationX((float) view.getWidth() * -position);
            view.setTranslationY(20F * position);
            view.setScaleX(scaleFactor);
            view.setScaleY(scaleFactor);
        }

    }

    public void transformPage(View view, float position) {
        this.onPreTransform(view, position);
        this.onTransform(view, position);
        this.onPostTransform(view, position);
    }

    protected boolean hideOffscreenPages() {
        return false;
    }

    protected boolean isPagingEnabled() {
        return true;
    }

    protected void onPreTransform(View view, float position) {}

    protected void onPostTransform(View view, float position) {}

}
