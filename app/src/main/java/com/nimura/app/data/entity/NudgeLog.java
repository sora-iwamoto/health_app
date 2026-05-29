package com.nimura.app.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "nudge_logs",
    foreignKeys = @ForeignKey(
        entity = Measurement.class,
        parentColumns = "id",
        childColumns = "measurement_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("measurement_id")
)
public class NudgeLog {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "measurement_id")
    public long measurementId;

    public long timestamp;

    @ColumnInfo(name = "nudge_level")
    public String nudgeLevel;

    @ColumnInfo(name = "nudge_content")
    public String nudgeContent;

    @ColumnInfo(name = "user_response")
    public String userResponse;

    @ColumnInfo(name = "response_time_ms")
    public long responseTimeMs;
}
