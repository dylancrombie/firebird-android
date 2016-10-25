package com.oasis.firebird.android.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public abstract class FirebirdSQLiteHelper extends SQLiteOpenHelper {

	protected static final String KEY_TYPE = " INTEGER PRIMARY KEY AUTOINCREMENT";
	protected static final String TEXT_TYPE = " TEXT";
	protected static final String INTEGER_TYPE = " INTEGER";
	protected static final String DOUBLE_TYPE = " REAL";
	protected static final String BLOB_TYPE = " BLOB";
	protected static final String COMMA_STEP = ",";

	public static final String COLUMN_ID = "system_id";

	public FirebirdSQLiteHelper(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	protected boolean existsColumnInTable(SQLiteDatabase database, String table, String column) {
	    
		try {
	    	
	        Cursor cursor  = database.rawQuery( "SELECT * FROM " + table + " LIMIT 0", null );
			return cursor.getColumnIndex(column) != -1;

	    } catch (Exception e) {
	        return false;
	    }
		
	}
	
}
