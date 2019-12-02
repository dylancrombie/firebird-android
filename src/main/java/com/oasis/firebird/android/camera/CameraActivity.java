package com.oasis.firebird.android.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.oasis.firebird.android.R;
import com.oasis.firebird.android.activity.FirebirdActivity;
import com.oasis.firebird.android.application.MessageModel;
import com.oasis.firebird.android.application.ResultListener;
import com.oasis.firebird.android.camera.FirebirdCamera.FlashMode;
import com.oasis.firebird.android.util.FirebirdAndroidUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class CameraActivity extends FirebirdActivity implements PictureListener {

	private static final String IMAGE_SIZE = "image_size";
	public static final String IMAGE_PATH = "image_uri";

	private static final int IMAGE_SIZE_DEFAULT = 2048;

	private int imageSize;

	public static final String[] LOCATION_PERMS={
			Manifest.permission.ACCESS_FINE_LOCATION
	};
	public static final String[] CAMERA_PERMS={
			Manifest.permission.CAMERA
	};

	public static final int INITIAL_REQUEST=37;
	public static final int CAMERA_REQUEST=INITIAL_REQUEST+1;
	public static final int LOCATION_REQUEST=INITIAL_REQUEST+3;

	private enum ViewState {CAMERA, PREVIEW}

	private Bitmap image;

	private LinearLayout lytZoom;
	private SeekBar fldZoom;

	private FirebirdCamera showCamera;

	private FrameLayout lytPreview;
	private ImageButton btnCaptureImage;
	private RelativeLayout lytCapture;

	private RelativeLayout lytImagePreview;
	private ImageView imgPreview;
	private Button btnAcceptImage;

	private boolean zoomSupported;
	private Button btnFlash;
	private FlashMode flashMode;
	private int lastOrientation;
	private boolean storingImage;


	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);

		imageSize = getIntent().getIntExtra(IMAGE_SIZE, IMAGE_SIZE_DEFAULT);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {

            actionBar.setIcon(new ColorDrawable(ContextCompat.getColor(this, android.R.color.transparent)));
			actionBar.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent));
            actionBar.setStackedBackgroundDrawable(ContextCompat.getDrawable(this, R.color.dark));
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setElevation(10);

        }

		lytCapture = (RelativeLayout) findViewById(R.id.lyt_capture);

		lytPreview = (FrameLayout) findViewById(R.id.lyt_camera_preview);
		
		btnCaptureImage = (ImageButton) findViewById(R.id.btn_capture);
		btnCaptureImage.setSoundEffectsEnabled(false);
		btnCaptureImage.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {

				onCapture();

				if (((AudioManager)getSystemService(Context.AUDIO_SERVICE)).getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
					final MediaPlayer mp = MediaPlayer.create(CameraActivity.this, R.raw.camera);

					Handler handler = new Handler();
					final Runnable r = new Runnable() {
						public void run() {
							mp.start();
						}
					};

					handler.postDelayed(r, 500);
				}

			}
		});

        ImageButton btnRotateImage = (ImageButton) findViewById(R.id.btn_rotate_image);
		btnRotateImage.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				
				rotateImage();
				
			}
		});

		lytImagePreview = (RelativeLayout) findViewById(R.id.lyt_image_preview);
		lytImagePreview.setVisibility(View.INVISIBLE);
		
		imgPreview = (ImageView) findViewById(R.id.img_preview);
		
		btnAcceptImage = (Button) findViewById(R.id.btn_accept_image);
		btnAcceptImage.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {

				accept();

			}
		});

        Button btnRetake = (Button) findViewById(R.id.btn_retake);
		btnRetake.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {

				retake();
				
			}
		});

		lytZoom = (LinearLayout) findViewById(R.id.lyt_zoom);
		lytZoom.setVisibility(View.GONE);
		
		fldZoom = (SeekBar) findViewById(R.id.fld_zoom);
		fldZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				
				if (zoomSupported) {
					
					try {
					
						showCamera.setZoom(progress);
						seekBar.setProgress(progress);
					
					} catch (RuntimeException e) {
						
						zoomSupported = false;
						lytZoom.setVisibility(View.GONE);
						
					}
					
				}
				
			}
			
		});
		
		btnFlash = (Button) findViewById(R.id.btn_flash);
		btnFlash.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				adjustFlash();

			}
		});
		btnFlash.setVisibility(View.GONE);
		flashMode = FlashMode.AUTO;

	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

	private void adjustFlash() {
		
		switch (flashMode) {
		case AUTO:
			flashMode = FlashMode.ON;
			btnFlash.setText(getResources().getString(R.string.button_on));
			break;
		case OFF:
			flashMode = FlashMode.AUTO;
			btnFlash.setText(getResources().getString(R.string.button_auto));
			break;
		case ON:
			flashMode = FlashMode.OFF;
			btnFlash.setText(getResources().getString(R.string.button_off));
			break;
		}
		
		showCamera.setFlash(flashMode);
		
	}

	private void retake() {

        setViewState(ViewState.CAMERA);

        this.image = null;

        initialiseCamera();

	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	protected void onResume() {
		super.onResume();

		if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
			if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.CAMERA)) {
				initialiseCamera();
			} else {
				requestPermissions(CAMERA_PERMS, CAMERA_REQUEST);
			}
		} else {
			initialiseCamera();
		}

	}

	@Override
	protected void onPause() {
		super.onPause();

		releaseCamera();

	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {

		switch(requestCode) {

			case CAMERA_REQUEST:
				if (PackageManager.PERMISSION_GRANTED==checkSelfPermission(Manifest.permission.CAMERA)) {
					initialiseCamera();
				} else {
                    finish();
                }
				break;

            case LOCATION_REQUEST:
                if (PackageManager.PERMISSION_GRANTED==checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    onImageStore(image);
                }
                break;

		}

	}

	private void rotateImage() {
		
//		lastOrientation += 90; FIXME
//
//		if (lastOrientation >= 360) {
//			lastOrientation = 0;
//		}
//
//		onImageTaken(image);
		
	}

	public void onImageTaken(final Bitmap image) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {

				CameraActivity.this.lastOrientation = lastOrientation;

				btnCaptureImage.setVisibility(View.VISIBLE);
				lytZoom.setVisibility(View.VISIBLE);
				btnFlash.setVisibility(View.VISIBLE);

				setViewState(ViewState.PREVIEW);
				imgPreview.setImageBitmap(image);

				CameraActivity.this.image = image;

				releaseCamera();

			}
		});

	}

	private void initialiseCamera() {

        if (showCamera != null) {
            return;
        }

		if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
			showCamera = new LollipopCamera(this);
		} else {
			showCamera = new BaseCamera(this);
		}

		lytPreview.addView(showCamera.getView());

		showCamera.initialise(this, this, new CameraListener() {
			@Override
			public void onReady(final boolean succeeded) {

				runOnUiThread(new Runnable() {
					@Override
					public void run() {

						if (succeeded) {
							zoomSupported = showCamera.isZoomSupported();

							if (zoomSupported) {

								fldZoom.setMax(showCamera.getMaxZoom());
								fldZoom.setProgress(0);
								lytZoom.setVisibility(View.VISIBLE);

							}

							boolean flashSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);

							if (flashSupported) {

								btnFlash.setVisibility(View.VISIBLE);
								showCamera.setFlash(flashMode);

							}

						} else {

							MessageModel messageModel = new MessageModel();
							messageModel.setMessage(getResources().getString(R.string.message_camera_unavailable));
							messageModel.setNegative(getResources().getString(R.string.button_cancel));
							messageModel.setPositive(getResources().getString(R.string.button_retry));
							messageModel.setTitle(getResources().getString(R.string.title_camera_unavailable));

							FirebirdAndroidUtils.showOptionsAlert(CameraActivity.this, messageModel, new ResultListener() {

								@Override
								public void onResultReceived(Result result) {

									if (result == Result.POSISTIVE) {

										initialiseCamera();

									} else {

										finish();

									}

								}
							});

						}

					}
				});

			}
		});

	}

	private void releaseCamera() {
		
		if (showCamera != null) {

			showCamera.stopPreview();
			lytPreview.removeView(showCamera.getView());
			showCamera.release();
			showCamera = null;
			
		}

	}
	
	private void setViewState(ViewState state) {
		
		lytImagePreview.setVisibility(View.INVISIBLE);
		lytCapture.setVisibility(View.INVISIBLE);
		lytZoom.setVisibility(View.INVISIBLE);
		btnFlash.setVisibility(View.INVISIBLE);
		
		switch (state) {

        case CAMERA:

            btnCaptureImage.setVisibility(View.VISIBLE);
            lytZoom.setVisibility(View.VISIBLE);
            btnFlash.setVisibility(View.VISIBLE);
            lytCapture.setVisibility(View.VISIBLE);

            break;

		case PREVIEW:
			
			lytImagePreview.setVisibility(View.VISIBLE);
			
			break;
		}
		
	}

	private void onCapture() {
		
		btnCaptureImage.setVisibility(View.INVISIBLE);
		lytZoom.setVisibility(View.INVISIBLE);
		btnFlash.setVisibility(View.INVISIBLE);
		showCamera.takePicture(imageSize);
		
	}

	@TargetApi(Build.VERSION_CODES.M)
	public void accept() {

		if (!storingImage) {

            if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
                if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    onImageStore(image);
                } else {
                    requestPermissions(LOCATION_PERMS, LOCATION_REQUEST);
                }
            } else {
                onImageStore(image);
            }

		}

	}

	@SuppressLint("SimpleDateFormat")
	private void onImageStore(final Bitmap image) {

        storingImage = true;

        btnAcceptImage.setEnabled(false);

		new Thread(new Runnable() {

			@Override
			public void run() {

				String filePath = CameraActivity.this.getFilesDir().getAbsolutePath() + "Image_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Calendar.getInstance().getTime()) + ".png";

				Log.d("FILE_PATH", filePath);

				try {

					FileOutputStream out = null;
					try {
						out = new FileOutputStream(new File(filePath));
						image.compress(Bitmap.CompressFormat.JPEG, 100, out);
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						try {
							if (out != null) {
								out.close();
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

					Intent intent = new Intent();
					Bundle extras = new Bundle();
					extras.putString(IMAGE_PATH, filePath);
					intent.putExtras(extras);
					setResult(RESULT_OK, intent);
					finish();

					CameraActivity.this.image = null;

				} catch (Exception e) {

					e.printStackTrace();

				}

			}

		}).start();

	}

}
