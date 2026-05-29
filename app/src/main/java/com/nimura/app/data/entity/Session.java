package com.nimura.app.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")
public class Session {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "start_time")
    public long startTime;

    @ColumnInfo(name = "end_time")
    public long endTime;

    @ColumnInfo(name = "is_active")
    public boolean isActive;
}
