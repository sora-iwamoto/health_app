package com.nimura.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nimura.app.data.entity.DailySummary;

import java.util.List;

@Dao
public interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDailySummary(DailySummary dailySummary);

    @Query("SELECT * FROM daily_summaries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    List<DailySummary> getSummariesForRange(String startDate, String endDate);

    @Query("SELECT * FROM daily_summaries ORDER BY date DESC LIMIT 1")
    DailySummary getLatestSummary();

    @Query("SELECT * FROM daily_summaries WHERE date = :date")
    DailySummary getSummaryForDate(String date);
}
