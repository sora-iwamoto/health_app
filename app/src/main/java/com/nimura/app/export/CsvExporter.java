package com.nimura.app.export;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.nimura.app.data.NimuraDatabase;
import com.nimura.app.data.entity.DailySummary;
import com.nimura.app.data.entity.Measurement;
import com.nimura.app.data.entity.NudgeLog;
import com.nimura.app.data.entity.Session;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvExporter {

    private static final String TAG = "CsvExporter";
    private final Context context;
    private final NimuraDatabase database;
    private final String userId;

    public CsvExporter(Context context, String userId) {
        this.context = context;
        this.database = NimuraDatabase.getInstance(context);
        this.userId = userId;
    }

    public File exportAll() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File exportDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "nimura_export_" + timestamp);
        if (!exportDir.mkdirs() && !exportDir.exists()) {
            throw new IOException("Failed to create export directory");
        }

        exportSessions(new File(exportDir, "sessions_" + userId + ".csv"));
        exportMeasurements(new File(exportDir, "measurements_" + userId + ".csv"));
        exportNudgeLogs(new File(exportDir, "nudge_logs_" + userId + ".csv"));
        exportDailySummaries(new File(exportDir, "daily_summaries_" + userId + ".csv"));

        Log.d(TAG, "Export complete: " + exportDir.getAbsolutePath());
        return exportDir;
    }

    private void exportSessions(File file) throws IOException {
        List<Session> sessions = database.sessionDao().getAllSessions();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("id,start_time,end_time,is_active\n");
            for (Session s : sessions) {
                writer.write(String.format(Locale.US, "%d,%d,%d,%b\n",
                        s.id, s.startTime, s.endTime, s.isActive));
            }
        }
    }

    private void exportMeasurements(File file) throws IOException {
        List<Session> sessions = database.sessionDao().getAllSessions();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("id,session_id,timestamp,blink_rate,ear_value,perclos,"
                    + "head_tilt_angle,shoulder_tilt_angle,"
                    + "eye_fatigue_score,posture_fatigue_score,composite_fatigue_score\n");
            for (Session s : sessions) {
                List<Measurement> measurements = database.measurementDao().getMeasurementsForSession(s.id);
                for (Measurement m : measurements) {
                    writer.write(String.format(Locale.US,
                            "%d,%d,%d,%.2f,%.4f,%.4f,%.2f,%.2f,%d,%d,%d\n",
                            m.id, m.sessionId, m.timestamp, m.blinkRate, m.earValue, m.perclos,
                            m.headTiltAngle, m.shoulderTiltAngle,
                            m.eyeFatigueScore, m.postureFatigueScore, m.compositeFatigueScore));
                }
            }
        }
    }

    private void exportNudgeLogs(File file) throws IOException {
        List<NudgeLog> logs = database.nudgeLogDao().getNudgeLogsForPeriod(0, Long.MAX_VALUE);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("id,measurement_id,timestamp,nudge_level,nudge_content,user_response,response_time_ms\n");
            for (NudgeLog n : logs) {
                writer.write(String.format(Locale.US, "%d,%d,%d,%s,\"%s\",%s,%d\n",
                        n.id, n.measurementId, n.timestamp, n.nudgeLevel,
                        n.nudgeContent != null ? n.nudgeContent.replace("\"", "\"\"") : "",
                        n.userResponse, n.responseTimeMs));
            }
        }
    }

    private void exportDailySummaries(File file) throws IOException {
        List<DailySummary> summaries = database.dailySummaryDao().getSummariesForRange("2000-01-01", "2099-12-31");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("id,date,avg_fatigue_score,avg_blink_rate,avg_posture_score,"
                    + "total_nudges,nudge_accept_rate,total_measurement_minutes\n");
            for (DailySummary d : summaries) {
                writer.write(String.format(Locale.US, "%d,%s,%.2f,%.2f,%.2f,%d,%.4f,%d\n",
                        d.id, d.date, d.avgFatigueScore, d.avgBlinkRate, d.avgPostureScore,
                        d.totalNudges, d.nudgeAcceptRate, d.totalMeasurementMinutes));
            }
        }
    }
}
