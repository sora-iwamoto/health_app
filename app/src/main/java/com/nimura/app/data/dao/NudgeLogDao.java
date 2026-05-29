package com.nimura.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.nimura.app.data.entity.NudgeLog;

import java.util.List;

@Dao
public interface NudgeLogDao {

    @Insert
    long insertNudgeLog(NudgeLog nudgeLog);

    @Update
    void updateNudgeLog(NudgeLog nudgeLog);

    @Query("SELECT * FROM nudge_logs WHERE id = :nudgeLogId")
    NudgeLog getNudgeLogById(long nudgeLogId);

    @Query("SELECT * FROM nudge_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<NudgeLog> getNudgeLogsForPeriod(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM nudge_logs WHERE user_response = 'accepted' AND timestamp BETWEEN :startTime AND :endTime")
    int getAcceptedCount(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM nudge_logs WHERE timestamp BETWEEN :startTime AND :endTime")
    int getTotalCount(long startTime, long endTime);
}
