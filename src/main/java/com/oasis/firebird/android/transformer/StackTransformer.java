package com.oasis.firebird.android.transformer;

import android.support.v4.view.ViewPager;
import android.view.View;

import com.oasis.firebird.android.R;

public class StackTransformer implements ViewPager.PageTransformer {

    public StackTransformer() {
    }

    private void onTransform(View view, float position) {

        if(position <= 0.0F) {
            view.setTranslationX(0.0F);
            view.setScaleX(1.0F);
            view.setScaleY(1.0F);
        } else if(position <= 2.0F) {
            float scaleFactor = 0.98F + 0.02F * (1.0F - Math.abs(position));
            view.setPivotY(0.5F * (float) view.getHeight());
            view.setPivotX(0.5F * (float) view.getWidth());
            view.setTranslationX((float) view.getWidth() * -position);
            view.setTranslationY(view.getResources().getDimension(R.dimen.stack_translation) * position);
            view.setScaleX(scaleFactor);
            view.setScaleY(scaleFactor);
            if (position >= 1.0) {
                view.setAlpha(2.0F - position);
            }
        }

    }

    public void transformPage(View view, float position) {
        this.onTransform(view, position);
    }

}
