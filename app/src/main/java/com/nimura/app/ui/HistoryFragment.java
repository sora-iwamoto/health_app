package com.nimura.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.chip.Chip;
import com.nimura.app.R;
import com.nimura.app.data.NimuraDatabase;
import com.nimura.app.data.entity.DailySummary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment {

    private LineChart chart;
    private TextView textSummaryAvg;
    private TextView textSummaryNudge;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chart = view.findViewById(R.id.chartFatigue);
        textSummaryAvg = view.findViewById(R.id.textSummaryAvg);
        textSummaryNudge = view.findViewById(R.id.textSummaryNudge);

        setupChart();

        Chip chipDay = view.findViewById(R.id.chipDay);
        Chip chipWeek = view.findViewById(R.id.chipWeek);
        Chip chipMonth = view.findViewById(R.id.chipMonth);

        chipDay.setOnClickListener(v -> loadData(1));
        chipWeek.setOnClickListener(v -> loadData(7));
        chipMonth.setOnClickListener(v -> loadData(30));

        loadData(7); // Default: week view
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.getLegend().setEnabled(false);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setAxisMinimum(0);
        yAxis.setAxisMaximum(100);

        // Threshold lines
        LimitLine mildLine = new LimitLine(30, "");
        mildLine.setLineColor(Color.parseColor("#10B981"));
        mildLine.setLineWidth(1f);
        mildLine.enableDashedLine(10f, 10f, 0f);
        yAxis.addLimitLine(mildLine);

        LimitLine moderateLine = new LimitLine(60, "");
        moderateLine.setLineColor(Color.parseColor("#F59E0B"));
        moderateLine.setLineWidth(1f);
        moderateLine.enableDashedLine(10f, 10f, 0f);
        yAxis.addLimitLine(moderateLine);

        LimitLine severeLine = new LimitLine(80, "");
        severeLine.setLineColor(Color.parseColor("#EF4444"));
        severeLine.setLineWidth(1f);
        severeLine.enableDashedLine(10f, 10f, 0f);
        yAxis.addLimitLine(severeLine);

        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setEnabled(true);
    }

    private void loadData(int days) {
        executor.execute(() -> {
            Calendar cal = Calendar.getInstance();
            String endDate = dateFormat.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, -days);
            String startDate = dateFormat.format(cal.getTime());

            NimuraDatabase db = NimuraDatabase.getInstance(requireContext());
            List<DailySummary> summaries = db.dailySummaryDao().getSummariesForRange(startDate, endDate);

            ArrayList<Entry> entries = new ArrayList<>();
            float totalScore = 0;
            float totalNudgeRate = 0;

            for (int i = 0; i < summaries.size(); i++) {
                entries.add(new Entry(i, summaries.get(i).avgFatigueScore));
                totalScore += summaries.get(i).avgFatigueScore;
                totalNudgeRate += summaries.get(i).nudgeAcceptRate;
            }

            float avgScore = summaries.isEmpty() ? 0 : totalScore / summaries.size();
            float avgNudgeRate = summaries.isEmpty() ? 0 : totalNudgeRate / summaries.size();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (entries.isEmpty()) {
                        chart.clear();
                        chart.setNoDataText("データがありません");
                        textSummaryAvg.setText("平均疲労スコア: --");
                        textSummaryNudge.setText("ナッジ応答率: --");
                        return;
                    }

                    LineDataSet dataSet = new LineDataSet(entries, "疲労スコア");
                    dataSet.setColor(Color.parseColor("#6366F1"));
                    dataSet.setLineWidth(2f);
                    dataSet.setCircleColor(Color.parseColor("#6366F1"));
                    dataSet.setCircleRadius(4f);
                    dataSet.setDrawValues(false);
                    dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                    chart.setData(new LineData(dataSet));
                    chart.animateX(500);
                    chart.invalidate();

                    textSummaryAvg.setText(String.format("平均疲労スコア: %.0f", avgScore));
                    textSummaryNudge.setText(String.format("ナッジ応答率: %.0f%%", avgNudgeRate * 100));
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
