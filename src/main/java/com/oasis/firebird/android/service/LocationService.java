package com.oasis.firebird.android.service;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;

import com.oasis.firebird.android.location.FirebirdLocation;

public class LocationService extends Service {

    private static final Integer LOCATION_TIMEOUT_MILISECONDS = 15000;
    private IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public LocationService getServerInstance() {
            return LocationService.this;
        }
    }

	@Override
	public void onCreate() {
		super.onCreate();
		
	}

    public void getLocation() {

        new FirebirdLocation().getLocation(this, new FirebirdLocation.LocationResult() {

            @Override
            public void gotLocation(Location location) {

                if (location != null) {
                    // TODO implement location callback
                }

            }

        }, LOCATION_TIMEOUT_MILISECONDS);

    }

}