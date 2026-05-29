package com.nimura.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPreferences {

    private static final String PREFS_NAME = "nimura_prefs";
    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isNudgeEnabled() {
        return prefs.getBoolean("nudge_enabled", true);
    }

    public void setNudgeEnabled(boolean enabled) {
        prefs.edit().putBoolean("nudge_enabled", enabled).apply();
    }

    public boolean isControlGroup() {
        return prefs.getBoolean("control_group", false);
    }

    public void setControlGroup(boolean controlGroup) {
        prefs.edit().putBoolean("control_group", controlGroup).apply();
    }

    public String getUserId() {
        return prefs.getString("user_id", "");
    }

    public void setUserId(String userId) {
        prefs.edit().putString("user_id", userId).apply();
    }

    public float getAlphaWeight() {
        return prefs.getFloat("alpha_weight", 0.5f);
    }

    public void setAlphaWeight(float alpha) {
        prefs.edit().putFloat("alpha_weight", alpha).apply();
    }

    public float getBetaWeight() {
        return prefs.getFloat("beta_weight", 0.5f);
    }

    public void setBetaWeight(float beta) {
        prefs.edit().putFloat("beta_weight", beta).apply();
    }

    public int getNudgeCooldownMinutes() {
        return prefs.getInt("nudge_cooldown_minutes", 5);
    }

    public void setNudgeCooldownMinutes(int minutes) {
        prefs.edit().putInt("nudge_cooldown_minutes", minutes).apply();
    }
}
