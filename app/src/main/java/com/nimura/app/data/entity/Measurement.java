package com.nimura.app.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "measurements",
    foreignKeys = @ForeignKey(
        entity = Session.class,
        parentColumns = "id",
        childColumns = "session_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("session_id")
)
public class Measurement {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "session_id")
    public long sessionId;

    public long timestamp;

    @ColumnInfo(name = "blink_rate")
    public float blinkRate;

    @ColumnInfo(name = "ear_value")
    public float earValue;

    public float perclos;

    @ColumnInfo(name = "head_tilt_angle")
    public float headTiltAngle;

    @ColumnInfo(name = "shoulder_tilt_angle")
    public float shoulderTiltAngle;

    @ColumnInfo(name = "eye_fatigue_score")
    public int eyeFatigueScore;

    @ColumnInfo(name = "posture_fatigue_score")
    public int postureFatigueScore;

    @ColumnInfo(name = "composite_fatigue_score")
    public int compositeFatigueScore;
}
