package com.mwg.hyperoseq;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileStore {
    private static final String PREFS = "eq_profile";
    private static final String KEY_JSON = "json";
    private static final String KEY_ENABLED = "enabled";

    public static void save(Context context, String json) {
        prefs(context).edit().putString(KEY_JSON, json).apply();
    }

    public static String loadJson(Context context) {
        return prefs(context).getString(KEY_JSON, null);
    }

    public static boolean hasProfile(Context context) {
        return loadJson(context) != null;
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
