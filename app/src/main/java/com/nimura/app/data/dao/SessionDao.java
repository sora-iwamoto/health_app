package com.nimura.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.nimura.app.data.entity.Session;

import java.util.List;

@Dao
public interface SessionDao {

    @Insert
    long insertSession(Session session);

    @Update
    void updateSession(Session session);

    @Query("SELECT * FROM sessions WHERE is_active = 1 LIMIT 1")
    Session getActiveSession();

    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    List<Session> getAllSessions();

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    Session getSessionById(long sessionId);

    @Query("SELECT * FROM sessions ORDER BY start_time DESC LIMIT 1")
    Session getLatestSession();
}
