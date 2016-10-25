package com.oasis.firebird.android.camera;

import android.app.Activity;
import android.view.View;

public interface FirebirdCamera {

    enum FlashMode {AUTO, ON, OFF}

    boolean isZoomSupported();
    void setZoom(int zoom);
    int getMaxZoom();
    void setFlash(FlashMode flashMode);

    void initialise(Activity activity, PictureListener pictureListener, CameraListener cameraListener);
    void takePicture(int size);
    void stopPreview();
    void release();

    View getView();

}
