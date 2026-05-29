package com.nimura.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nimura.app.data.entity.Measurement;

import java.util.List;

@Dao
public interface MeasurementDao {

    @Insert
    long insertMeasurement(Measurement measurement);

    @Query("SELECT * FROM measurements WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    List<Measurement> getRecentMeasurements(long sessionId, int limit);

    @Query("SELECT * FROM measurements WHERE session_id = :sessionId ORDER BY timestamp DESC")
    List<Measurement> getMeasurementsForSession(long sessionId);

    @Query("SELECT * FROM measurements WHERE id = :measurementId")
    Measurement getMeasurementById(long measurementId);

    @Query("SELECT * FROM measurements WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<Measurement> getMeasurementsForPeriod(long startTime, long endTime);

    @Query("SELECT AVG(composite_fatigue_score) FROM measurements WHERE session_id = :sessionId")
    float getAverageFatigueScoreForSession(long sessionId);
}
