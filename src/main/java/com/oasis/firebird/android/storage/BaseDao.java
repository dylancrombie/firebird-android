package com.oasis.firebird.android.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.oasis.firebird.model.BaseEntity;
import com.oasis.firebird.storage.SQLiteGenericDao;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseDao<E extends BaseEntity> implements SQLiteGenericDao<E> {

	protected SQLiteDatabase database;
	
	public BaseDao(SQLiteDatabase database) {
		this.database = database;
	}

	public void delete(E entity) {

		long id = entity.getId();
		database.delete(getTableName(), FirebirdSQLiteHelper.COLUMN_ID + " = " + id, null);

	}

	public List<E> findAll() {

		List<E> entities = new ArrayList<>();

		Cursor cursor = database.query(getTableName(), getAllColumns(), null, null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			E entity = cursorToEntity(cursor);
			entities.add(entity);
			cursor.moveToNext();
		}

		cursor.close();

		return entities;

	}

	public E findById(Long id) {

		Cursor cursor = database.query(getTableName(), getAllColumns(), FirebirdSQLiteHelper.COLUMN_ID + " = ?", new String[] { String.valueOf(id) }, null, null, null);
		cursor.moveToFirst();

		E entity = cursorToEntity(cursor);

		cursor.close();

		return entity;

	}

	public E create(E entity) {

		long insertId = database.insert(getTableName(), null, getValues(entity));
		Cursor cursor = database.query(getTableName(), getAllColumns(), FirebirdSQLiteHelper.COLUMN_ID + " = " + insertId, null, null, null, null);
		cursor.moveToFirst();

		E createdEntity = cursorToEntity(cursor);

		cursor.close();

		return createdEntity;

	}

	public E update(E entity) {

		database.update(getTableName(), getValues(entity), FirebirdSQLiteHelper.COLUMN_ID + " = ?", new String[] { String.valueOf(entity.getId()) });

		return findById(entity.getId());

	}

	protected abstract E cursorToEntity(Cursor cursor);
	protected abstract String getTableName();
	protected abstract String[] getAllColumns();
	protected abstract ContentValues getValues(E entity);
	
}
