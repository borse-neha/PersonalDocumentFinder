package com.example.personaldocumentfinder.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class SettingsRepository {

    public static final String THEME_KEY = "app_theme";
    private static final String PREF_NAME = "user_settings";

    private final SharedPreferences prefs;
    private final MutableLiveData<String> themeLiveData;

    public SettingsRepository(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String initialTheme = prefs.getString(THEME_KEY, "System");
        this.themeLiveData = new MutableLiveData<>(initialTheme != null ? initialTheme : "System");
    }

    public LiveData<String> getThemeLiveData() {
        return themeLiveData;
    }

    public String getTheme() {
        String t = prefs.getString(THEME_KEY, "System");
        return t != null ? t : "System";
    }

    public void setTheme(String theme) {
        String safeTheme = (theme != null && !theme.trim().isEmpty()) ? theme : "System";
        prefs.edit().putString(THEME_KEY, safeTheme).apply();
        themeLiveData.postValue(safeTheme);
    }
}
