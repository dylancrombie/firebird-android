package com.oasis.firebird.android.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.oasis.firebird.android.util.FirebirdAndroidUtils;

import java.io.IOException;
import java.util.List;

public class BaseCamera extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener, FirebirdCamera {

	private Camera theCamera;
	private Camera.Size mPreviewSize;
	private Parameters params;
    private Camera.PictureCallback pictureCallback;
	private int size;

	private SensorManager sensorManager;
	private int lastOrientation;
	
	public BaseCamera(Context context) {
		super(context);
	}

    public void initialise(Activity activity, final PictureListener pictureListener, CameraListener cameraListener) {

        theCamera = isCameraAvailable();

        if (theCamera != null) {

            theCamera.setPreviewCallback(null);

            sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

            params = theCamera.getParameters();

            if (params.getSupportedFlashModes() != null && params.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                params.setFlashMode(Parameters.FLASH_MODE_AUTO);
            }
            if (params.getSupportedAntibanding() != null && params.getSupportedAntibanding().contains(Camera.Parameters.ANTIBANDING_AUTO)) {
                params.setAntibanding(Parameters.ANTIBANDING_AUTO);
            }
            if (params.getSupportedSceneModes() != null && params.getSupportedSceneModes().contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            }
            if (params.getSupportedWhiteBalance() != null && params.getSupportedWhiteBalance().contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            }
            if (params.getSupportedFocusModes() != null && params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            params.setPreviewFormat(ImageFormat.NV21);

			params.setExposureCompensation(0);
			if(params.isAutoExposureLockSupported()) {
				params.setAutoExposureLock(false);
			}

            List<Size> sizes = params.getSupportedPictureSizes();

            Camera.Size size = sizes.get(0);
            for (int i = 0; i < sizes.size(); i++) {
                if (sizes.get(i).width > size.width) {
                    size = sizes.get(i);
                }
            }

            List<Size> previewSizes = params.getSupportedPreviewSizes();
            Camera.Size previewSize = previewSizes.get(0);
            for (int i = 0; i < previewSizes.size(); i++) {
                if (previewSizes.get(i).width > previewSize.width) {
                    previewSize = previewSizes.get(i);
                }
            }

            params.setPictureSize(size.width, size.height);
            params.setPreviewSize(previewSize.width, previewSize.height);

            theCamera.setDisplayOrientation(90);
            SurfaceHolder holdMe = getHolder();
            holdMe.addCallback(this);

            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

            pictureCallback = new Camera.PictureCallback() {

                public void onPictureTaken(byte[] data, Camera camera) {

                    pictureListener.onImageTaken(getBitmap(data, lastOrientation));

                }
            };

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            setLayoutParams(params);

			cameraListener.onReady(true);

        } else {

            cameraListener.onReady(false);

        }

    }

	private Bitmap getBitmap(byte[] data, int lastOrientation) {

		Matrix matrix = new Matrix();

		int orientation = lastOrientation;
		orientation -= FirebirdAndroidUtils.getDeviceDefaultOrientation(getContext());
		matrix.postRotate(orientation < 0 ? 270 : orientation);

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inMutable = true;
		options.inSampleSize = 2;
		Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);

		double imageRatio = (double) image.getHeight() / (double) image.getWidth();
		boolean portrait = image.getHeight() > image.getWidth();

		int width = portrait ? (int) (imageRatio * size) : size;
		int height = portrait ? size : (int) (imageRatio * size);

		return Bitmap.createBitmap(Bitmap.createScaledBitmap(image, width, height, false), 0, 0, width, height, matrix, true);

	}

	public View getView() {
        return this;
    }

    public void takePicture(int size) {

		this.size = size;

        theCamera.takePicture(null, null, pictureCallback);

    }
	
	public void setZoom(int zoom) {
		
		params.setZoom(zoom);
		theCamera.setParameters(params);
		
		
	}

    public boolean isZoomSupported() {
        return params.isZoomSupported();
    }

    public int getMaxZoom() {
        return params.getMaxZoom();
    }
	
	public void setFlash(FlashMode flashMode) {
		
		switch (flashMode) {
		case AUTO:
			params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
			break;
		case OFF:
			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
			break;
		case ON:
			params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
			break;

		}

		theCamera.setParameters(params);
		
	}

	public void surfaceChanged(SurfaceHolder holder, int arg1, int arg2, int arg3) {

		try {

			theCamera.setPreviewDisplay(holder);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void surfaceCreated(SurfaceHolder holder) {

		try {

			params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
			requestLayout();

			theCamera.setParameters(params);
			theCamera.setPreviewDisplay(holder);
			theCamera.startPreview();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		destroy();
	}

	private void destroy() {
		if (sensorManager != null) sensorManager.unregisterListener(this);
	}

	private void setCameraDisplayOrientation(int rotation) {

		if (isCameraInUse()) {
			
			Parameters parameters = theCamera.getParameters();
			parameters.setRotation(rotation);
			theCamera.setParameters(parameters);
			
		}

	}

	public void onSensorChanged(SensorEvent event) {

        float[] g;
        g = event.values.clone();

        double norm_Of_g = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);

        g[0] = (float) (g[0] / norm_Of_g);
        g[1] = (float) (g[1] / norm_Of_g);
        g[2] = (float) (g[2] / norm_Of_g);

        int rotation = (int) Math.round(Math.toDegrees(Math.atan2(g[0], g[1])));

        int orientation = 0;

        if (rotation <= 135 && rotation >= 45) {
            orientation = 0;
        } else if ((rotation <= 45 && rotation >= 0) || (rotation >= -45 && rotation <= 0)) {
            orientation = 90;
        } else if ((rotation >= -135 && rotation <= -45)) {
            orientation = 180;
        } else {
            orientation = 270;
        }

        if (lastOrientation != orientation) {
            lastOrientation = orientation;
            setCameraDisplayOrientation(lastOrientation);
        }

	}
	
	public boolean isCameraInUse() {
		
	    Camera camera = null;
	    try {
	        camera = Camera.open();
	    } catch (RuntimeException e) {
	        return true;
	    } finally {
	        if (camera != null) camera.release();
	    }
	    return false;
	    
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (params != null) {

            final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);

            mPreviewSize = getOptimalPreviewSize(width, height);

            float ratio;
            if (mPreviewSize.height >= mPreviewSize.width) {

                ratio = (float) mPreviewSize.height / (float) mPreviewSize.width;

            } else {

                ratio = (float) mPreviewSize.width / (float) mPreviewSize.height;

            }

            setMeasuredDimension(width, (int) (width * ratio));

        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

	}

	private Camera.Size getOptimalPreviewSize(int w, int h) {

		final double ASPECT_TOLERANCE = 0.5;
		double targetRatio = (double) h / w;

		List<Camera.Size> sizes = params.getSupportedPreviewSizes();

		Camera.Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		for (Camera.Size size : sizes) {

			double ratio = (double) size.height / size.width;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
				continue;
			}

			if (Math.abs(size.height - h) < minDiff) {

				optimalSize = size;
				minDiff = Math.abs(size.height - h);

			}

		}

		if (optimalSize == null) {

			minDiff = Double.MAX_VALUE;

			for (Camera.Size size : sizes) {

				if (Math.abs(size.height - h) < minDiff) {

					optimalSize = size;
					minDiff = Math.abs(size.height - h);

				}

			}

		}

        if (optimalSize == null) {
            optimalSize = sizes.get(0);
        }

		return optimalSize;

	}

	private Camera isCameraAvailable() {

		try {
			return Camera.open();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;

	}

    public void stopPreview() {

        if (theCamera != null) {
            theCamera.stopPreview();
            theCamera.setPreviewCallback(null);
        }

        getHolder().removeCallback(this);
        destroy();

    }

    public void release() {

        if (theCamera != null) {
            theCamera.release();
            theCamera = null;
        }

    }

}
