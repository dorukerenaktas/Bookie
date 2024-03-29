package com.karambit.bookie.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.maps.model.LatLng;
import com.karambit.bookie.model.User;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * Created by doruk on 19.03.2017.
 */

public class NotificationUserDataSource {

    private static final String TAG = NotificationUserDataSource.class.getSimpleName();

    private SQLiteDatabase mSqLiteDatabase;

    private static final String NOTIFICATION_USER_TABLE_NAME = "notification_user";
    private static final String NOTIFICATION_USER_COLUMN_ID = "user_id";
    private static final String NOTIFICATION_USER_COLUMN_NAME = "name";
    private static final String NOTIFICATION_USER_COLUMN_IMAGE_URL = "image_url";
    private static final String NOTIFICATION_USER_COLUMN_THUMBNAIL_URL = "thumbnail_url";
    private static final String NOTIFICATION_USER_COLUMN_LATITUDE = "latitude";
    private static final String NOTIFICATION_USER_COLUMN_LONGITUDE = "longitude";

    static final String CREATE_NOTIFICATION_USER_TABLE_TAG = "CREATE TABLE " + NOTIFICATION_USER_TABLE_NAME + " (" +
            NOTIFICATION_USER_COLUMN_ID + " INTEGER PRIMARY KEY NOT NULL, " +
            NOTIFICATION_USER_COLUMN_NAME + " TEXT NOT NULL, " +
            NOTIFICATION_USER_COLUMN_IMAGE_URL + " TEXT, " +
            NOTIFICATION_USER_COLUMN_THUMBNAIL_URL + " TEXT, " +
            NOTIFICATION_USER_COLUMN_LATITUDE + " DOUBLE, " +
            NOTIFICATION_USER_COLUMN_LONGITUDE + " DOUBLE)";

    static final String UPGRADE_NOTIFICATION_USER_TABLE_TAG = "DROP TABLE IF EXISTS " + NOTIFICATION_USER_TABLE_NAME;

    NotificationUserDataSource(SQLiteDatabase database) {
        mSqLiteDatabase = database;
    }

    /**
     * Insert user to database.<br>
     *
     * @param user {@link User} which will be inserted
     * @return Returns boolean value if insertion successful returns true else returns false
     */
    private boolean insertUser(User user) {
        boolean result = false;
        try{
            ContentValues contentValues = new ContentValues();
            contentValues.put(NOTIFICATION_USER_COLUMN_ID, user.getID());
            contentValues.put(NOTIFICATION_USER_COLUMN_NAME, user.getName());
            contentValues.put(NOTIFICATION_USER_COLUMN_IMAGE_URL, user.getImageUrl());
            contentValues.put(NOTIFICATION_USER_COLUMN_THUMBNAIL_URL, user.getThumbnailUrl());
            contentValues.put(NOTIFICATION_USER_COLUMN_LATITUDE, (user.getLocation() != null) ? user.getLocation().latitude : null);
            contentValues.put(NOTIFICATION_USER_COLUMN_LONGITUDE, (user.getLocation() != null) ? user.getLocation().longitude : null);

            result = mSqLiteDatabase.insert(NOTIFICATION_USER_TABLE_NAME, null, contentValues) > 0;
        }finally {
            Logger.d("New User insertion successful");
        }
        return result;
    }

    /**
     * Updates user in database.<br>
     *
     * @param user {@link User} which will be inserted
     * @return Returns boolean value if update successful returns true else returns false
     */
    private boolean updateUser(User user) {
        boolean result = false;
        try{
            ContentValues contentValues = new ContentValues();
            contentValues.put(NOTIFICATION_USER_COLUMN_ID, user.getID());
            contentValues.put(NOTIFICATION_USER_COLUMN_NAME, user.getName());
            contentValues.put(NOTIFICATION_USER_COLUMN_IMAGE_URL, user.getImageUrl());
            contentValues.put(NOTIFICATION_USER_COLUMN_THUMBNAIL_URL, user.getThumbnailUrl());
            contentValues.put(NOTIFICATION_USER_COLUMN_LATITUDE, (user.getLocation() != null) ? user.getLocation().latitude : null);
            contentValues.put(NOTIFICATION_USER_COLUMN_LONGITUDE, (user.getLocation() != null) ? user.getLocation().longitude : null);

            result = mSqLiteDatabase.update(NOTIFICATION_USER_TABLE_NAME, contentValues, NOTIFICATION_USER_COLUMN_ID + "=" + user.getID(), null) > 0;
        }finally {
            Logger.d("New User update successful");
        }
        return result;
    }

    /**
     * Checks the user exists. Updates given user in database if its not exist.<br>
     *
     * @param user {@link User User}<br>
     *
     * @return boolean value. If update successful returns true else returns false.
     */
    public boolean checkAndUpdateUser(User user) {
        if (isUserExists(user)) {
            return updateUser(user);
        } else {
            return false;
        }
    }

    /**
     * Checks the user exists. Inserts given user in database if its not exist.<br>
     *
     * @param user {@link User User}<br>
     *
     * @return boolean value. If insertion successful returns true else returns false.
     */
    public boolean saveUser(User user) {
        if (!isUserExists(user)) {
            return insertUser(user);
        } else {
            return false;
        }
    }

    /**
     * Checks database for given user's existence. Use before all user insertions.<br>
     *
     * @param user {@link User User}
     *
     * @return  boolean value. If message {@link User user} exist returns true else returns false.
     */
    public boolean isUserExists(User user) {
        Cursor res = null;

        try {
            res = mSqLiteDatabase.rawQuery("SELECT * FROM " + NOTIFICATION_USER_TABLE_NAME + " WHERE " + NOTIFICATION_USER_COLUMN_ID  + " = " + user.getID(), null);
            res.moveToFirst();

            return res.getCount() > 0;

        }finally {
            if (res != null) {
                res.close();
            }
        }
    }

    /**
     * Get all {@link User user's} from database.<br>
     *
     * @return All {@link User user's}
     */
    public ArrayList<User> getAllUsers() {
        Cursor res = null;
        ArrayList<User> users = new ArrayList<>();
        try {
            res = mSqLiteDatabase.rawQuery("SELECT * FROM " + NOTIFICATION_USER_TABLE_NAME, null);
            res.moveToFirst();

            if (res.getCount() > 0) {
                do {
                    User user;
                    if (res.isNull(res.getColumnIndex(NOTIFICATION_USER_COLUMN_LATITUDE)) || res.isNull(res.getColumnIndex(NOTIFICATION_USER_COLUMN_LONGITUDE))){
                        user = new User(res.getInt(res.getColumnIndex(NOTIFICATION_USER_COLUMN_ID)),
                                res.getString(res.getColumnIndex(NOTIFICATION_USER_COLUMN_NAME)),
                                res.getString(res.getColumnIndex(NOTIFICATION_USER_COLUMN_IMAGE_URL)),
                                res.getString(res.getColumnIndex(NOTIFICATION_USER_COLUMN_THUMBNAIL_URL)),
                                null);
                    }else {
                        user = new User(res.getInt(res.getColumnIndex(NOTIFICATION_USER_COLUMN_ID)),
                                res.getString(res.getColumnIndex(NOTIFICATION_USER_COLUMN_NAME)),
                                res.getString(res.getColumnIndex(NOTIFICATION_USER_COLUMN_IMAGE_URL)),
                                res.getString(res.getColumnIndex(NOTIFICATION_USER_COLUMN_THUMBNAIL_URL)),
                                new LatLng(res.getDouble(res.getColumnIndex(NOTIFICATION_USER_COLUMN_LATITUDE)), res.getDouble(res.getColumnIndex(NOTIFICATION_USER_COLUMN_LONGITUDE)))
                        );
                    }


                    users.add(user);
                } while (res.moveToNext());
            }
        }finally {
            if (res != null) {
                res.close();
            }
        }
        return users;
    }

    /**
     * Deletes all {@link User users} from database.<br>
     */
    public void deleteAllUsers() {
        try{
            mSqLiteDatabase.delete(NOTIFICATION_USER_TABLE_NAME, null, null);
        }finally {
            Logger.d("All users deleted from database");
        }
    }

    //Callable Methods
    public Callable<Boolean> cCheckAndUpdateUser(final User user){
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return checkAndUpdateUser(user);
            }
        };
    }
}
