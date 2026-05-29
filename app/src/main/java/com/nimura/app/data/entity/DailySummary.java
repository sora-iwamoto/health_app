package com.nimura.app.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "daily_summaries",
    indices = @Index(value = "date", unique = true)
)
public class DailySummary {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String date;

    @ColumnInfo(name = "avg_fatigue_score")
    public float avgFatigueScore;

    @ColumnInfo(name = "avg_blink_rate")
    public float avgBlinkRate;

    @ColumnInfo(name = "avg_posture_score")
    public float avgPostureScore;

    @ColumnInfo(name = "total_nudges")
    public int totalNudges;

    @ColumnInfo(name = "nudge_accept_rate")
    public float nudgeAcceptRate;

    @ColumnInfo(name = "total_measurement_minutes")
    public int totalMeasurementMinutes;
}
