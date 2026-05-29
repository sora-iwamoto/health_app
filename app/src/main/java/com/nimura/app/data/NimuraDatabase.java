package com.nimura.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.nimura.app.data.converter.Converters;
import com.nimura.app.data.dao.DailySummaryDao;
import com.nimura.app.data.dao.MeasurementDao;
import com.nimura.app.data.dao.NudgeLogDao;
import com.nimura.app.data.dao.SessionDao;
import com.nimura.app.data.entity.DailySummary;
import com.nimura.app.data.entity.Measurement;
import com.nimura.app.data.entity.NudgeLog;
import com.nimura.app.data.entity.Session;

@Database(
    entities = {Session.class, Measurement.class, NudgeLog.class, DailySummary.class},
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class NimuraDatabase extends RoomDatabase {

    private static volatile NimuraDatabase INSTANCE;

    public abstract SessionDao sessionDao();
    public abstract MeasurementDao measurementDao();
    public abstract NudgeLogDao nudgeLogDao();
    public abstract DailySummaryDao dailySummaryDao();

    public static NimuraDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NimuraDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        NimuraDatabase.class,
                        "nimura_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
