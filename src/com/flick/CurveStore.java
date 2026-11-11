package com.flick;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** On-device curve library. No account, no sync, no network. */
public final class CurveStore extends SQLiteOpenHelper {
    private static final String DB = "flick.db";
    private static final int VERSION = 2;
    private static final String T = "curves";

    public CurveStore(Context ctx) { super(ctx, DB, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "tags TEXT DEFAULT '',"
                + "favourite INTEGER DEFAULT 0,"
                + "model TEXT NOT NULL,"
                + "x1 REAL, y1 REAL, x2 REAL, y2 REAL,"
                + "omega REAL, zeta REAL,"
                + "duration REAL,"
                + "stops TEXT,"
                + "created INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + T);
        onCreate(db);
    }

    @Override public void onDowngrade(SQLiteDatabase db, int oldV, int newV) {
        onUpgrade(db, oldV, newV);
    }

    public long insert(SavedCurve c) {
        ContentValues v = new ContentValues();
        v.put("name", c.name);
        v.put("tags", c.tags);
        v.put("favourite", c.favourite ? 1 : 0);
        v.put("model", c.model);
        v.put("x1", c.x1); v.put("y1", c.y1);
        v.put("x2", c.x2); v.put("y2", c.y2);
        v.put("omega", c.omega); v.put("zeta", c.zeta);
        v.put("duration", c.durationMs);
        v.put("stops", SavedCurve.encode(c.traceStops));
        v.put("created", c.created);
        long id = getWritableDatabase().insert(T, null, v);
        c.id = id;
        return id;
    }

    public void delete(long id) {
        getWritableDatabase().delete(T, "id=?", new String[]{ String.valueOf(id) });
    }

    public void rename(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update(T, v, "id=?", new String[]{ String.valueOf(id) });
    }

    public void setFavourite(long id, boolean fav) {
        ContentValues v = new ContentValues();
        v.put("favourite", fav ? 1 : 0);
        getWritableDatabase().update(T, v, "id=?", new String[]{ String.valueOf(id) });
    }

    /** Favourites first, then most recent. */
    public List<SavedCurve> all() {
        List<SavedCurve> out = new ArrayList<SavedCurve>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(T, null, null, null, null, null,
                    "favourite DESC, created DESC");
            while (c.moveToNext()) out.add(read(c));
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Name, tags or model, matched in SQL so large libraries stay fast. */
    public List<SavedCurve> search(String query) {
        if (query == null || query.trim().length() == 0) return all();
        List<SavedCurve> out = new ArrayList<SavedCurve>();
        Cursor c = null;
        String like = "%" + query.trim() + "%";
        try {
            c = getReadableDatabase().query(T, null,
                    "name LIKE ? OR tags LIKE ? OR model LIKE ?",
                    new String[]{ like, like, like },
                    null, null, "favourite DESC, created DESC");
            while (c.moveToNext()) out.add(read(c));
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    private static SavedCurve read(Cursor c) {
        SavedCurve s = new SavedCurve();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        int ti = c.getColumnIndex("tags");
        s.tags = ti >= 0 && c.getString(ti) != null ? c.getString(ti) : "";
        int fi = c.getColumnIndex("favourite");
        s.favourite = fi >= 0 && c.getInt(fi) != 0;
        s.model = c.getString(c.getColumnIndexOrThrow("model"));
        s.x1 = c.getDouble(c.getColumnIndexOrThrow("x1"));
        s.y1 = c.getDouble(c.getColumnIndexOrThrow("y1"));
        s.x2 = c.getDouble(c.getColumnIndexOrThrow("x2"));
        s.y2 = c.getDouble(c.getColumnIndexOrThrow("y2"));
        s.omega = c.getDouble(c.getColumnIndexOrThrow("omega"));
        s.zeta = c.getDouble(c.getColumnIndexOrThrow("zeta"));
        s.durationMs = c.getDouble(c.getColumnIndexOrThrow("duration"));
        s.traceStops = SavedCurve.decode(c.getString(c.getColumnIndexOrThrow("stops")));
        s.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return s;
    }

    public int count() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + T, null);
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /** The whole library as one pasteable Kotlin file. */
    public static String kotlinLibrary(List<SavedCurve> curves) {
        return LibraryExport.kotlin(curves);
    }
}
