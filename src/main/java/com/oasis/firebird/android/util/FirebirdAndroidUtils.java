package com.oasis.firebird.android.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.oasis.firebird.android.R;
import com.oasis.firebird.android.application.ListResultListener;
import com.oasis.firebird.android.application.MessageModel;
import com.oasis.firebird.android.application.ResultListener;
import com.oasis.firebird.android.application.ResultListener.Result;
import com.oasis.firebird.core.ErrorMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FirebirdAndroidUtils {

	public static final String MESSAGE_PREFERNCES = "MessagePreferences";

	private static TelephonyManager telephonyManager;
	private static PhoneStateListener callStateListener;

	private static BroadcastReceiver wifiReceiver;
	private static boolean mobileData;
	private static boolean wifi;

	private static long startMobileRX;
	private static long startMobileTX;

	private static Runnable task;

	private static boolean registeredMobileListener;
	private static boolean registeredWifiListener;

	public static boolean isDebug(Context context) {
		return (0 != (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
	}

	public static void manageErrors(Activity context, List<ErrorMessage> errorMessages) {

		for (ErrorMessage errorMessage : errorMessages) {

			manageError(context, errorMessage, null);

		}

	}

	public static void manageErrors(Activity context, List<ErrorMessage> errorMessages, final CloseListener closeListener) {

		int count = 1;
		for (ErrorMessage errorMessage : errorMessages) {

			if (count == errorMessages.size()) {
				manageError(context, errorMessage, closeListener);
			} else {
				manageError(context, errorMessage, null);
			}

		}

	}

	public static void manageError(final Activity context, ErrorMessage errorMessage, final CloseListener closeListener) {

		if(!context.isFinishing()) {

			AlertDialog.Builder builder;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				builder = new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert);
			} else {
				builder = new AlertDialog.Builder(context);
			}
			builder.setCancelable(true);
			builder.setTitle(errorMessage.getTag());
			builder.setMessage(errorMessage.getMessage());
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {

					dialog.dismiss();

					if (closeListener != null && !context.isFinishing()) {
						closeListener.onAllClosed();
					}

				}
			});

			if (!context.isFinishing()) {

				AlertDialog alert = builder.create();
				alert.show();

				TextView msgTxt = (TextView) alert.findViewById(android.R.id.message);
				msgTxt.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_title));

			}

		}

	}

	public static void showOptionsAlert(final Activity context, final MessageModel messageModel, final ResultListener resultListener) {

		LayoutInflater inflater = LayoutInflater.from(context);
		View eulaLayout = inflater.inflate(R.layout.checkbox, null);
		final CheckBox dontShowAgain = (CheckBox) eulaLayout.findViewById(R.id.chk_skip);

		AlertDialog.Builder builder;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder = new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert);
		} else {
			builder = new AlertDialog.Builder(context);
		}
		builder.setCancelable(true);
		builder.setTitle(messageModel.getTitle());
		builder.setMessage(messageModel.getMessage());
		builder.setPositiveButton(messageModel.getPositive(), new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				savePreference(context, messageModel, dontShowAgain, Result.POSISTIVE);

				resultListener.onResultReceived(Result.POSISTIVE);

			}

		});
		if (messageModel.getNegative() != null)
			builder.setNegativeButton(messageModel.getNegative(), new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					savePreference(context, messageModel, dontShowAgain, Result.NEGATIVE);

					resultListener.onResultReceived(Result.NEGATIVE);

				}
			});
		if (messageModel.getNeutral() != null)
			builder.setNeutralButton(messageModel.getNeutral(), new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					savePreference(context, messageModel, dontShowAgain, Result.NEUTRAL);

					resultListener.onResultReceived(Result.NEUTRAL);

				}
			});

		SharedPreferences settings = context.getSharedPreferences(MESSAGE_PREFERNCES, 0);
		Boolean skipMessage = settings.getBoolean(messageModel.getPreference(), false);
		Result skipResult = Result.valueOf(settings.getString(messageModel.getPreference() + " Result", Result.NEGATIVE.name()));

		if (!skipMessage) {

			if (messageModel.getAllowSkip() != null && messageModel.getAllowSkip()) {

				builder.setView(eulaLayout);

			}

			if (!context.isFinishing()) {
				
				AlertDialog alert = builder.create();
				alert.show();
				alert.getWindow().getAttributes();

				try {

					TextView msgTxt = (TextView) alert.findViewById(android.R.id.message);
					if (msgTxt != null) {
						msgTxt.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_title));
					}

				} catch (NullPointerException e) {
					e.printStackTrace();
				}

			}

		} else {

			resultListener.onResultReceived(skipResult);

		}

	}
	
	public static void showOptionsList(final Activity context, final MessageModel messageModel, List<String> data, Integer icon, final ListResultListener resultListener) {

		AlertDialog.Builder builder;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder = new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert);
		} else {
			builder = new AlertDialog.Builder(context);
		}
		if (icon != null) {
			builder.setIcon(icon);
		}
		builder.setTitle(messageModel.getTitle());
		final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(context, R.layout.dialog_list_item);
		arrayAdapter.addAll(data);
		if (messageModel.getNegative() != null)
			builder.setNegativeButton(messageModel.getNegative(), new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					resultListener.onResultReceived(Result.NEGATIVE);

				}
			});

		builder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				
				resultListener.onResultSelected(which);
				
			}
		});
		builder.show();

	}

	public static void showBitmap(final Activity context, final MessageModel messageModel, Bitmap data, final ResultListener resultListener) {

		AlertDialog.Builder builder;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder = new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert);
		} else {
			builder = new AlertDialog.Builder(context);
		}
		builder.setTitle(messageModel.getTitle());

		ImageView imageView = new ImageView(context);
		imageView.setImageBitmap(data);

		builder.setView(imageView);

		builder.setPositiveButton(messageModel.getPositive(), new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				resultListener.onResultReceived(Result.POSISTIVE);

			}

		});
		if (messageModel.getNegative() != null)
			builder.setNegativeButton(messageModel.getNegative(), new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					resultListener.onResultReceived(Result.NEGATIVE);

				}
			});
		if (messageModel.getNeutral() != null)
			builder.setNeutralButton(messageModel.getNeutral(), new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					resultListener.onResultReceived(Result.NEUTRAL);

				}
			});
		builder.show();

	}
	
	public static void getDate(Context context, final String title, final Calendar oldValue, final String message, final DateListener inputListener) {

		DatePickerDialog.OnDateSetListener dateListener = new DatePickerDialog.OnDateSetListener() {
			@Override
			public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {

				Calendar calendar = new GregorianCalendar();
				calendar.set(Calendar.YEAR, year);
				calendar.set(Calendar.MONTH, monthOfYear);
				calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
				inputListener.onInput(calendar);

			}

		};

		DatePickerDialog datePicker;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			datePicker = new DatePickerDialog(context, android.R.style.Theme_Material_Light_Dialog, dateListener, oldValue.get(Calendar.YEAR), oldValue.get(Calendar.MONTH), oldValue.get(Calendar.DAY_OF_MONTH));
		} else {
			datePicker = new DatePickerDialog(context, dateListener, oldValue.get(Calendar.YEAR), oldValue.get(Calendar.MONTH), oldValue.get(Calendar.DAY_OF_MONTH));
		}
		datePicker.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				inputListener.onCancel();
			}
		});
		datePicker.show();
		
	}

	public static void getDate(Context context, final String title, final Calendar oldValue, final Long minDate, final Long maxDate, final DateListener inputListener) {

		DatePickerDialog.OnDateSetListener dateListener = new DatePickerDialog.OnDateSetListener() {
			@Override
			public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {

				Calendar calendar = new GregorianCalendar();
				calendar.set(Calendar.YEAR, year);
				calendar.set(Calendar.MONTH, monthOfYear);
				calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
				inputListener.onInput(calendar);

			}

		};

		DatePickerDialog datePicker;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			datePicker = new DatePickerDialog(context, android.R.style.Theme_Material_Light_Dialog, dateListener, oldValue.get(Calendar.YEAR), oldValue.get(Calendar.MONTH), oldValue.get(Calendar.DAY_OF_MONTH));
		} else {
			datePicker = new DatePickerDialog(context, dateListener, oldValue.get(Calendar.YEAR), oldValue.get(Calendar.MONTH), oldValue.get(Calendar.DAY_OF_MONTH));
		}
		if (minDate != null) datePicker.getDatePicker().setMinDate(minDate);
		if (maxDate != null) datePicker.getDatePicker().setMinDate(maxDate);
		datePicker.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				inputListener.onCancel();
			}
		});
		datePicker.show();

	}
	
	public static void getInput(Activity context, final String title, final String oldValue, final String message, final String hint, final Integer inputType, final int characterLimit, final InputListener inputListener) {

		AlertDialog.Builder builder;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder = new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert);
		} else {
			builder = new AlertDialog.Builder(context);
		}
		builder.setTitle(title);
		
		LayoutInflater inflater = context.getLayoutInflater();
		View inputView = inflater.inflate(R.layout.input_dialog, null);
		
		TextView lblMessage = (TextView) inputView.findViewById(R.id.lbl_message);
		lblMessage.setText(message);
		lblMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_title));
		
		final EditText fldInput = (EditText) inputView.findViewById(R.id.fld_input);
		fldInput.setHint(hint);
		fldInput.setInputType(inputType);
		fldInput.setTransformationMethod(PasswordTransformationMethod.getInstance());

		if (inputType != InputType.TYPE_TEXT_VARIATION_PASSWORD) {

			fldInput.setSingleLine(false);
			if (oldValue != null) {
				fldInput.setText(oldValue);
				fldInput.setSelection(fldInput.getText().length());
			}

		}

		InputFilter[] filterArray = new InputFilter[1];
		filterArray[0] = new InputFilter.LengthFilter(characterLimit);

		fldInput.setFilters(filterArray);
		fldInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_message));
		fldInput.setTextColor(ContextCompat.getColor(context, R.color.text_dialog));

		builder.setView(inputView);

		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int whichButton) {

				inputListener.onInput(fldInput.getText().toString());

			}

		});
		
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int whichButton) {

				inputListener.onCancel();

			}

		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {

				inputListener.onCancel();

			}
		});
		
		final AlertDialog alert = builder.create();

		fldInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
		    @Override
		    public void onFocusChange(View v, boolean hasFocus) {
		    	
		        if (hasFocus) {
		        	alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		        }
		        
		    }
		});

		alert.show();
		
	}

	protected static void savePreference(Context context, MessageModel messageModel, CheckBox dontShowAgain, Result result) {

		if (messageModel.getPreference() != null && messageModel.getAllowSkip()) {

			Boolean checkBoxResult = false;

			if (dontShowAgain.isChecked()) {
				checkBoxResult = true;
			}

			SharedPreferences settings = context.getSharedPreferences(MESSAGE_PREFERNCES, 0);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(messageModel.getPreference(), checkBoxResult);
			editor.putString(messageModel.getPreference() + " Result", result.name());
			editor.commit();

		}

	}

	public static Boolean isMobileDataEnabled(Context context) {

		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		try {
			Class<?> c = Class.forName(connectivityManager.getClass().getName());
			Method m = c.getDeclaredMethod("getMobileDataEnabled");
			m.setAccessible(true);
			return (Boolean) m.invoke(connectivityManager);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	public static Boolean isNetworkAvailable(Context context) {

		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();

	}

	public static Boolean isWiFiEnabled(Context context) {

		WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		if (wifi.isWifiEnabled()) {
			return true;
		}

		return false;

	}

	public static void checkMobileDataAvailability(final Activity context, NetworkHandler networkHandler) {

		if (!isMobileDataEnabled(context)) {

			MessageModel messageModel = new MessageModel();
			messageModel.setMessage(context.getResources().getString(R.string.message_enable_mobile_data));
			messageModel.setTitle(context.getResources().getString(R.string.title_enable_mobile_data));
			messageModel.setPositive(context.getResources().getString(R.string.button_open_settings));
			messageModel.setNegative(context.getResources().getString(R.string.button_cancel));

			showOptionsAlert(context, messageModel, new ResultListener() {

				@Override
				public void onResultReceived(Result result) {

					if (result == Result.POSISTIVE) {

						Intent intent = new Intent(Intent.ACTION_MAIN);
						intent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
						context.startActivity(intent);

					}

				}

			});

		} else {

			networkHandler.onAction();

		}

	}

	public static void checkNetworkAvailability(final Activity context, final NetworkHandler networkHandler) {

		if (!FirebirdAndroidUtils.isWiFiEnabled(context)) {

			MessageModel messageModel = new MessageModel();
			messageModel.setMessage(context.getResources().getString(R.string.message_enable_wifi));
			messageModel.setTitle(context.getResources().getString(R.string.title_enable_wifi));
			messageModel.setPositive(context.getResources().getString(R.string.button_open_settings));
			messageModel.setNeutral(context.getResources().getString(R.string.button_use_mobile_data));
			messageModel.setNegative(context.getResources().getString(R.string.button_cancel));
			messageModel.setAllowSkip(true);
			messageModel.setPreference(context.getResources().getString(R.string.preferences_allow_mobile_data));

			showOptionsAlert(context, messageModel, new ResultListener() {

				@Override
				public void onResultReceived(Result result) {

					if (result == Result.POSISTIVE) {

						context.startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));

					} else if (result == Result.NEUTRAL) {

						checkMobileDataAvailability(context, networkHandler);

					}

				}

			});

		} else {

			networkHandler.onAction();

		}

	}

	public static void registerNetworkListener(final Context context, final NetworkListener networkListener) {
		
		if (!registeredMobileListener) {

			telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			callStateListener = new PhoneStateListener() {

				public void onDataConnectionStateChanged(int state) {

					switch (state) {
					case TelephonyManager.DATA_CONNECTED:

						mobileData = true;
						networkListener.onConnected(true, telephonyManager.getNetworkType(), false);

						break;

					case TelephonyManager.DATA_DISCONNECTED:

						mobileData = false;

						if (!wifi) {

							networkListener.onConnected(false, telephonyManager.getNetworkType(), false);

						}

						break;

					}

				}

			};

			telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
			registeredMobileListener = true;

		}

		if (!registeredWifiListener) {

			wifiReceiver = new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {

					ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
					NetworkInfo netInfo = conMan.getActiveNetworkInfo();
					if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {

						wifi = true;
						networkListener.onConnected(true, telephonyManager.getNetworkType(), true);

					} else {

						wifi = false;

						if (!mobileData) {

							networkListener.onConnected(false, telephonyManager.getNetworkType(), false);

						}

					}

				}

			};

			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
			context.registerReceiver(wifiReceiver, intentFilter);

			registeredWifiListener = true;

		}

		new Thread(new Runnable() {

			@Override
			public void run() {

				if (isNetworkAvailable(context)) {

					networkListener.onConnected(true, telephonyManager.getNetworkType(), true);

				} else {

					networkListener.onConnected(false, telephonyManager.getNetworkType(), false);

				}

			}
		}).start();

	}

	public static void deregisterNetworkListener(Context context) {
		
		if (registeredWifiListener) {

			telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
			registeredMobileListener = false;

		}
		if (registeredWifiListener) {

			context.unregisterReceiver(wifiReceiver);
			registeredWifiListener = false;

		}

	}
	
	public static boolean hasSimFeature(final Context context) {
		
		TelephonyManager telephonyManager1 = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

		return telephonyManager1.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;

	}

	public static boolean isSimUsable(final Context context) {

		TelephonyManager telephonyManager1 = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

		int simState = telephonyManager1.getSimState();
		switch (simState) {
			case TelephonyManager.SIM_STATE_READY:
				return true;
		}

		return false;

	}
	
	public static String getMacAddress(Context context) {
		
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = manager.getConnectionInfo();
		return info.getMacAddress();
		
	}

	public static void setTrafficListener(final ApplicationInfo applicationInfo, final DataTrackListener dataTrackListener) {

		startMobileRX = TrafficStats.getUidRxBytes(applicationInfo.uid);
		startMobileTX = TrafficStats.getUidTxBytes(applicationInfo.uid);

		if (startMobileRX == TrafficStats.UNSUPPORTED || startMobileTX == TrafficStats.UNSUPPORTED) {
			
			dataTrackListener.notSupported();
			
		} else {
			
			final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
			
			task = new Runnable() {

				public void run() {

					long rxBytes = TrafficStats.getUidRxBytes(applicationInfo.uid) - startMobileRX;
					long txBytes = TrafficStats.getUidTxBytes(applicationInfo.uid) - startMobileTX;

					dataTrackListener.onTraffic(txBytes, rxBytes);
					
					worker.schedule(task, 1000, TimeUnit.MILLISECONDS);

				}

			};
			worker.schedule(task, 1000, TimeUnit.MILLISECONDS);

		}

	}
	
	public static InetAddress getBroadcastAddress(Context context) throws IOException {
		
	    WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	    DhcpInfo dhcp = wifi.getDhcpInfo();
	    // handle null somehow

	    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
	    byte[] quads = new byte[4];
	    for (int k = 0; k < 4; k++)
	      quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
	    return InetAddress.getByAddress(quads);
	    
	}
	
	public static int getResId(String resName, Class<?> c) {

	    try {
	        Field idField = c.getDeclaredField(resName);
	        return idField.getInt(idField);
	    } catch (Exception e) {
	        e.printStackTrace();
	        return -1;
	    }
	    
	}

	public static byte[] getByteFromBitmap(Bitmap bitmap, Bitmap.CompressFormat compressFormat) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bitmap.compress(compressFormat, 100, baos);

		return baos.toByteArray();

	}

	public static Bitmap cropBitmapTransparency(Bitmap sourceBitmap, int padding) {

		int minX = sourceBitmap.getWidth();
		int minY = sourceBitmap.getHeight();
		int maxX = -1;
		int maxY = -1;
		for(int y = 0; y < sourceBitmap.getHeight(); y++)
		{
			for(int x = 0; x < sourceBitmap.getWidth(); x++)
			{
				int alpha = (sourceBitmap.getPixel(x, y) >> 24) & 255;
				if(alpha > 0)   // pixel is not 100% transparent
				{
					if(x < minX)
						minX = x;
					if(x > maxX)
						maxX = x;
					if(y < minY)
						minY = y;
					if(y > maxY)
						maxY = y;
				}
			}
		}
		if((maxX < minX) || (maxY < minY))
			return null; // Bitmap is entirely transparent

		if (minX > padding) {
			minX -= padding;
		} else {
			minX = 1;
		}
		if (minY > padding) {
			minY -= padding;
		} else {
			minY = 1;
		}
		if ((maxX + padding+1) < sourceBitmap.getWidth()) {
			maxX += padding;
		} else {
			maxX = sourceBitmap.getWidth()-1;
		}
		if ((maxY + padding+1) < sourceBitmap.getHeight()) {
			maxY += padding;
		} else {
			maxY = sourceBitmap.getHeight()-1;
		}

        Log.d("SOURCE", sourceBitmap.getWidth() + " " + sourceBitmap.getHeight());
        Log.d("CROP", minX + " " + minY + " " + maxX + " " + maxY);

		sourceBitmap = Bitmap.createBitmap(sourceBitmap, minX, minY, (maxX - minX) + 1, (maxY - minY) + 1);

		return sourceBitmap;

	}

	public static Bitmap alphaToWhite(Bitmap bitmap) {

		Bitmap whiteBgBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(whiteBgBitmap);
		canvas.drawColor(Color.WHITE);
		canvas.drawBitmap(bitmap, 0, 0, null);
		return whiteBgBitmap;

	}

	public static float getScreenWidth(Activity activity) {
		Display display = activity.getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size.x;
	}

	public static float getScreenHeight(Activity activity) {
		Display display = activity.getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size.y;
	}

	public static boolean isValidEmail(String email) {
		return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
	}

	public static void hideKeyboard(Activity activity) {

		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
		View view = activity.getCurrentFocus();
		if (view == null) {
			view = new View(activity);
		}
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

	}

	public interface NetworkListener {

		void onConnected(Boolean connected, int networkType, boolean wifi);

	}

	public interface NetworkHandler {

		void onAction();

	}

	public interface CloseListener {

		void onAllClosed();

	}

	public interface DataTrackListener {

		void onTraffic(long transmitted, long recevied);
		void notSupported();

	}
	
	public interface InputListener {
		
		void onInput(String value);
		void onCancel();
	}
	
	public interface DateListener {
		
		void onInput(Calendar value);
		void onCancel();
	}
	
	public static boolean isTablet(Context context) {
	    return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
	}
	
	public static int getDeviceDefaultOrientation(Context context) {

	    WindowManager windowManager =  (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

	    Configuration config = context.getResources().getConfiguration();

	    int rotation = windowManager.getDefaultDisplay().getRotation();

	    if ( ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
	            config.orientation == Configuration.ORIENTATION_LANDSCAPE)
	        || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&    
	            config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
	      return 90;
	    } else { 
	      return 0;
	    }
	}

	public static boolean isColorDark(int color){
		double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
		if (darkness < 0.5){
			return false; // It's a light color
		} else {
			return true; // It's a dark color
		}
	}

	public static float getDarkness(int color){
		return 1f - (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255;
	}

	public static int lightenColor(int color,float factor){
		float r = Color.red(color)*factor;
		float g = Color.green(color)*factor;
		float b = Color.blue(color)*factor;
		int ir = Math.min(255,(int)r);
		int ig = Math.min(255,(int)g);
		int ib = Math.min(255,(int)b);
		int ia = Color.alpha(color);
		return(Color.argb(ia, ir, ig, ib));
	}

    public static boolean listIsAtTop(ListView listView)   {
        if(listView.getChildCount() == 0) return true;
        return listView.getChildAt(0).getTop() == 0;
    }

    public static boolean listIsAtBottom(ListView listView) {

        if (listView.getLastVisiblePosition() == listView.getAdapter().getCount() -1 &&
                listView.getChildAt(listView.getChildCount() - 1).getBottom() <= listView.getHeight())
        {
            return true;

        }

        return false;

    }

	public static int getActionBarSize(Activity activity) {

		TypedValue tv = new TypedValue();
		if (activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
			return TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
		}

		return 0;

	}

	public static int getStatusBarHeight(Activity activity) {

		Rect rectangle = new Rect();
		Window window = activity.getWindow();
		window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
		return rectangle.top;

	}

	public static boolean isLocationEnabled(Context context) {

		LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		boolean gps_enabled = false;
		boolean network_enabled = false;

		try {
			gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch(Exception ex) {}

		try {
			network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		} catch(Exception ex) {}

		return gps_enabled || network_enabled;

	}

	public static String saveToInternalStorage(Context context, Bitmap bitmapImage, String folder, String filename, Bitmap.CompressFormat format, int quality) {

		ContextWrapper cw = new ContextWrapper(context);
		File directory = cw.getDir(folder, Context.MODE_PRIVATE);
		File path = new File(directory,filename);

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(path);
			bitmapImage.compress(format, quality, fos);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (fos != null) fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return path.getAbsolutePath();

	}

	public static void setTranslucentStatusBar(Window window) {
		if (window == null) return;
		int sdkInt = Build.VERSION.SDK_INT;
		if (sdkInt >= Build.VERSION_CODES.LOLLIPOP) {
			setTranslucentStatusBarLollipop(window);
		} else if (sdkInt >= Build.VERSION_CODES.KITKAT) {
			setTranslucentStatusBarKiKat(window);
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void setTranslucentStatusBarLollipop(Window window) {
		window.setStatusBarColor(ContextCompat.getColor(window.getContext(), android.R.color.transparent));
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private static void setTranslucentStatusBarKiKat(Window window) {
		window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
	}

	public static Bitmap getBitmapFromView(View view) {

		Bitmap returnedBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(returnedBitmap);
		Drawable bgDrawable =view.getBackground();

		if (bgDrawable!=null) {
			bgDrawable.draw(canvas);
		} else {
			canvas.drawColor(Color.WHITE);
		}

		view.draw(canvas);
		return returnedBitmap;

	}

}
