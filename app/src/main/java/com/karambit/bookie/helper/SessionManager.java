package com.karambit.bookie.helper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by orcan on 11/13/16.
 */

public class SessionManager {

    public static final String TAG = SessionManager.class.getSimpleName();

    public static final String NAME_SHARED_PREFERENCES = "bookie_sp";

    public static final String IS_LOGGED_IN = "is_logged_in";

    public static boolean isLoggedIn(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(NAME_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(IS_LOGGED_IN, false);
    }

    public static void changeLoginStatus(Context context, boolean isLoggedIn) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(NAME_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(IS_LOGGED_IN, isLoggedIn);
        editor.apply();
        editor.commit();
    }

    public static void logout(Context context) {
        changeLoginStatus(context, false);
    }

    public static void login(Context context) {
        changeLoginStatus(context, true);
    }


}
