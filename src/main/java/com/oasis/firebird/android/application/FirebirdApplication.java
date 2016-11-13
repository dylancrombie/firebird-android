package com.oasis.firebird.android.application;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.database.sqlite.SQLiteDatabase;

import com.oasis.firebird.android.storage.FirebirdSQLiteHelper;
import com.oasis.firebird.storage.DaoFactory;

@Deprecated
public abstract class FirebirdApplication extends Application {

	protected DaoFactory daoFactory;
	
	@Override
	public void onCreate() {
		super.onCreate();

		if (getSQLiteHelper() != null) {

			daoFactory = new DaoFactory();
			SQLiteDatabase database = getSQLiteHelper().getWritableDatabase();
			onSetupApplication(database);

		}

	}
	
	@Override
	public void onTerminate() {
		super.onTerminate();

		if (getSQLiteHelper() != null) {
			getSQLiteHelper().close();
		}

	}
	
	protected void onSetupApplication(SQLiteDatabase database) {

	}

	protected FirebirdSQLiteHelper getSQLiteHelper() {
		return null;
	}

}
