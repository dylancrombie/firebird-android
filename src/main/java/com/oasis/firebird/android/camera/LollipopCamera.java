package com.oasis.firebird.android.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.oasis.firebird.android.R;
import com.oasis.firebird.android.util.FirebirdAndroidUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopCamera extends TextureView implements SensorEventListener, FirebirdCamera {

	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	private static final String TAG = "CameraActivity";
	private static final int STATE_PREVIEW = 0;
	private static final int STATE_WAITING_LOCK = 1;
	private static final int STATE_WAITING_PRECAPTURE = 2;
	private static final int STATE_WAITING_NON_PRECAPTURE = 3;
	private static final int STATE_PICTURE_TAKEN = 4;
	private static final int MAX_PREVIEW_WIDTH = 1920;
	private static final int MAX_PREVIEW_HEIGHT = 1080;
	private int mState = STATE_PREVIEW;

    private SensorManager sensorManager;
	private Semaphore mCameraOpenCloseLock = new Semaphore(1);
	private CameraDevice mCameraDevice;
	private Size mPreviewSize;
	private String mCameraId;
	private Handler mBackgroundHandler;
	private ImageReader mImageReader;
	private boolean mFlashSupported;
	private CaptureRequest.Builder mPreviewRequestBuilder;
	private CameraCaptureSession mCaptureSession;
	private CaptureRequest mPreviewRequest;
    private HandlerThread mBackgroundThread;
    private CameraListener cameraListener;
    private PictureListener pictureListener;
	private Activity activity;

    private int lastOrientation;
    private int size;
	private int mRatioWidth = 0;
	private int mRatioHeight = 0;

	private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(@NonNull CameraDevice cameraDevice) {
			// This method is called when the camera is opened.  We start camera preview here.
			mCameraOpenCloseLock.release();
			mCameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(@NonNull CameraDevice cameraDevice) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(@NonNull CameraDevice cameraDevice, int error) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
			if (null != activity) {
				activity.finish();
			}
		}

	};

	private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
			openCamera(width, height);
            cameraListener.onReady(true);

		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture) {
		}

	};

	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

		@Override
		public void onImageAvailable(ImageReader reader) {
			mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
		}

	};

	private CameraCaptureSession.CaptureCallback mCaptureCallback
			= new CameraCaptureSession.CaptureCallback() {

		private void process(CaptureResult result) {
			switch (mState) {
				case STATE_PREVIEW: {
					// We have nothing to do when the camera preview is working normally.
					break;
				}
				case STATE_WAITING_LOCK: {
					Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
					if (afState == null) {
						captureStillPicture();
					} else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
							CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
							CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState ||
							CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState) {
						// CONTROL_AE_STATE can be null on some devices
						Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
						if (aeState == null ||
								aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
							mState = STATE_PICTURE_TAKEN;
							captureStillPicture();
						} else {
							runPrecaptureSequence();
						}
					}
					break;
				}
				case STATE_WAITING_PRECAPTURE: {
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null ||
							aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
							aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
						mState = STATE_WAITING_NON_PRECAPTURE;
					}
					break;
				}
				case STATE_WAITING_NON_PRECAPTURE: {
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
						mState = STATE_PICTURE_TAKEN;
						captureStillPicture();
					}
					break;
				}
			}
		}

		@Override
		public void onCaptureProgressed(@NonNull CameraCaptureSession session,
										@NonNull CaptureRequest request,
										@NonNull CaptureResult partialResult) {
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(@NonNull CameraCaptureSession session,
									   @NonNull CaptureRequest request,
									   @NonNull TotalCaptureResult result) {
			process(result);
		}

	};

	public LollipopCamera(Context context) {
		super(context);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        setLayoutParams(params);
		setBackgroundColor(ContextCompat.getColor(context, R.color.white));

	}

    public void initialise(Activity activity, final PictureListener pictureListener, CameraListener cameraListener) {

        this.activity = activity;
        this.cameraListener = cameraListener;
        this.pictureListener = pictureListener;

        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

        startBackgroundThread();

		if (isAvailable()) {

            this.cameraListener.onReady(openCamera(getWidth(), getHeight()));

		} else {
			setSurfaceTextureListener(mSurfaceTextureListener);
		}

    }

	private boolean openCamera(int width, int height) {

		boolean success = setUpCameraOutputs(width, height);
		configureTransform(width, height);
		CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

		try {
			if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				throw new RuntimeException("Time out waiting to lock camera opening.");
			}
			manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
            e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
            e.printStackTrace();
			return false;
		}

		return success;

	}

	private boolean setUpCameraOutputs(int width, int height) {

		CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
		try {

			for (String cameraId : manager.getCameraIdList()) {
				CameraCharacteristics characteristics
						= manager.getCameraCharacteristics(cameraId);

				// We don't use a front facing camera in this sample.
				Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
				if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
					continue;
				}

				StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
				if (map == null) {
					continue;
				}

				// For still image captures, we use the largest available size.
				Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                Size optimal = getOptimalPreviewSize(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), width, height);
				mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
				mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

				// Find out if we need to swap dimension to get the preview size relative to sensor
				// coordinate.
				int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
				int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
				boolean swappedDimensions = false;
				switch (displayRotation) {
					case Surface.ROTATION_0:
					case Surface.ROTATION_180:
						if (sensorOrientation == 90 || sensorOrientation == 270) {
							swappedDimensions = true;
						}
						break;
					case Surface.ROTATION_90:
					case Surface.ROTATION_270:
						if (sensorOrientation == 0 || sensorOrientation == 180) {
							swappedDimensions = true;
						}
						break;
					default:
						Log.e(TAG, "Display rotation is invalid: " + displayRotation);
				}

				Point displaySize = new Point();
				activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
				int rotatedPreviewWidth = width;
				int rotatedPreviewHeight = height;
				int maxPreviewWidth = displaySize.x;
				int maxPreviewHeight = displaySize.y;

				if (swappedDimensions) {
					rotatedPreviewWidth = height;
					rotatedPreviewHeight = width;
					maxPreviewWidth = displaySize.y;
					maxPreviewHeight = displaySize.x;
				}

				if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
					maxPreviewWidth = MAX_PREVIEW_WIDTH;
				}

				if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
					maxPreviewHeight = MAX_PREVIEW_HEIGHT;
				}

				// Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
				// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
				// garbage capture data.
				mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, optimal);
//				mPreviewSize = optimal;

				// We fit the aspect ratio of TextureView to the size of preview we picked.
				int orientation = getResources().getConfiguration().orientation;
				if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
					setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
				} else {
					setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
				}

				// Check if the flash is supported.
				Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
				mFlashSupported = available == null ? false : available;

				mCameraId = cameraId;
				return true;
			}

		} catch (CameraAccessException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			// Currently an NPE is thrown when the Camera2API is used but not supported on the
			// device this code runs.
			e.printStackTrace();
		}
		return false;

	}



    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {

        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Size size : sizes) {

            double ratio = (double) size.getHeight() / size.getWidth();
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue;
            }

            if (Math.abs(size.getHeight() - h) < minDiff) {

                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - h);

            }

        }

        if (optimalSize == null) {

            minDiff = Double.MAX_VALUE;

            for (Size size : sizes) {

                if (Math.abs(size.getHeight() - h) < minDiff) {

                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - h);

                }

            }

        }

        if (optimalSize == null) {
            optimalSize = sizes.get(0);
        }

        return optimalSize;

    }

	private void configureTransform(int viewWidth, int viewHeight) {

		if (null == mPreviewSize || null == activity) {
			return;
		}
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		Matrix matrix = new Matrix();
		RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
		RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
		float centerX = viewRect.centerX();
		float centerY = viewRect.centerY();
		if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
			bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			float scale = Math.max(
					(float) viewHeight / mPreviewSize.getHeight(),
					(float) viewWidth / mPreviewSize.getWidth());
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90 * (rotation - 2), centerX, centerY);
		} else if (Surface.ROTATION_180 == rotation) {
			matrix.postRotate(180, centerX, centerY);
		}
		setTransform(matrix);
	}

	private void createCameraPreviewSession() {
		try {
			SurfaceTexture texture = getSurfaceTexture();
			assert texture != null;

			// We configure the size of default buffer to be the size of camera preview we want.
			texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

			// This is the output Surface we need to start preview.
			Surface surface = new Surface(texture);

			// We set up a CaptureRequest.Builder with the output Surface.
			mPreviewRequestBuilder
					= mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder.addTarget(surface);

			// Here, we create a CameraCaptureSession for camera preview.
			mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
					new CameraCaptureSession.StateCallback() {

						@Override
						public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
							// The camera is already closed
							if (null == mCameraDevice) {
								return;
							}

							// When the session is ready, we start displaying the preview.
							mCaptureSession = cameraCaptureSession;
							try {
								// Auto focus should be continuous for camera preview.
								mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
								// Flash is automatically enabled when necessary.
								setAutoFlash(mPreviewRequestBuilder);

								// Finally, we start displaying the camera preview.
								mPreviewRequest = mPreviewRequestBuilder.build();
								mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
							} catch (CameraAccessException e) {
								e.printStackTrace();
							}
						}

						@Override
						public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
							showToast("Failed");
						}
					}, null
			);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	static class CompareSizesByArea implements Comparator<Size> {

		@Override
		public int compare(Size lhs, Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
					(long) rhs.getWidth() * rhs.getHeight());
		}

	}

	private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
										  int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

		// Collect the supported resolutions that are at least as big as the preview Surface
		List<Size> bigEnough = new ArrayList<>();
		// Collect the supported resolutions that are smaller than the preview Surface
		List<Size> notBigEnough = new ArrayList<>();
		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		for (Size option : choices) {
			if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
					option.getHeight() == option.getWidth() * h / w) {
				if (option.getWidth() >= textureViewWidth &&
						option.getHeight() >= textureViewHeight) {
					bigEnough.add(option);
				} else {
					notBigEnough.add(option);
				}
			}
		}

		// Pick the smallest of those big enough. If there is no one big enough, pick the
		// largest of those not big enough.
		if (bigEnough.size() > 0) {
			return Collections.min(bigEnough, new CompareSizesByArea());
		} else if (notBigEnough.size() > 0) {
			return Collections.max(notBigEnough, new CompareSizesByArea());
		} else {
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

	public void setAspectRatio(int width, int height) {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException("Size cannot be negative.");
		}
		mRatioWidth = width;
		mRatioHeight = height;
		requestLayout();
	}

	private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
		if (mFlashSupported) {
			requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
		}
	}

	private void captureStillPicture() {
		try {
			if (null == activity || null == mCameraDevice) {
				return;
			}
			// This is the CaptureRequest.Builder that we use to take a picture.
			final CaptureRequest.Builder captureBuilder =
					mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(mImageReader.getSurface());

			// Use the same AE and AF modes as the preview.
			captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			setAutoFlash(captureBuilder);

			// Orientation
			int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

			CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

				@Override
				public void onCaptureCompleted(@NonNull CameraCaptureSession session,
											   @NonNull CaptureRequest request,
											   @NonNull TotalCaptureResult result) {
                    unlockFocus();
				}
			};

			mCaptureSession.stopRepeating();
			mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void runPrecaptureSequence() {
		try {
			// This is how to tell the camera to trigger.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the precapture sequence to be set.
			mState = STATE_WAITING_PRECAPTURE;
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void unlockFocus() {
		try {
			// Reset the auto-focus trigger
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			setAutoFlash(mPreviewRequestBuilder);
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
			// After this, the camera will go back to the normal state of preview.
			mState = STATE_PREVIEW;
			mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void lockFocus() {

		try {
			// This is how to tell the camera to lock focus.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the lock.
			mState = STATE_WAITING_LOCK;
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}

	}

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void showToast(final String text) {
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    @Override
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
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public View getView() {
        return this;
    }

    public void takePicture(int size) {

        this.size = size;

        lockFocus();

    }

	public void setZoom(int zoom) {

	}

    public boolean isZoomSupported() {
		return true;
    }

    public int getMaxZoom() {
		return 1;
    }

	public void setFlash(FlashMode flashMode) {

	}

    public void stopPreview() {
        destroy();
    }

    public void release() {

		new Thread(new Runnable() {
			@Override
			public void run() {
				closeCamera();
				stopBackgroundThread();
			}
		}).start();

    }

    private void destroy() {
        sensorManager.unregisterListener(this);
	}

    private class ImageSaver implements Runnable {

        private final Image mImage;

        public ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {

            new Thread(new Runnable() {
                @Override
                public void run() {

                    Image.Plane[] planes = mImage.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

                    pictureListener.onImageTaken(getBitmap(data));
                    mImage.close();

                }
            }).start();

        }

    }

	private Bitmap getBitmap(byte[] data) {

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

}
