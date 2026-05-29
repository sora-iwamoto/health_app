package com.nimura.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.nimura.app.R;
import com.nimura.app.export.CsvExporter;
import com.nimura.app.settings.AppPreferences;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private AppPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = new AppPreferences(requireContext());

        SwitchMaterial switchNudge = view.findViewById(R.id.switchNudge);
        SwitchMaterial switchControlGroup = view.findViewById(R.id.switchControlGroup);
        TextInputEditText editParticipantId = view.findViewById(R.id.editParticipantId);
        MaterialButton buttonExport = view.findViewById(R.id.buttonExport);

        // Load current values
        switchNudge.setChecked(prefs.isNudgeEnabled());
        switchControlGroup.setChecked(prefs.isControlGroup());
        editParticipantId.setText(prefs.getUserId());

        // Save on change
        switchNudge.setOnCheckedChangeListener((v, checked) -> prefs.setNudgeEnabled(checked));
        switchControlGroup.setOnCheckedChangeListener((v, checked) -> prefs.setControlGroup(checked));

        editParticipantId.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String text = editParticipantId.getText() != null ? editParticipantId.getText().toString() : "";
                prefs.setUserId(text);
            }
        });

        buttonExport.setOnClickListener(v -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    String userId = prefs.getUserId().isEmpty() ? "unknown" : prefs.getUserId();
                    CsvExporter exporter = new CsvExporter(requireContext(), userId);
                    File exportDir = exporter.exportAll();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                "エクスポート完了: " + exportDir.getAbsolutePath(),
                                Toast.LENGTH_LONG).show()
                        );
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                "エクスポート失敗: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                        );
                    }
                }
                executor.shutdown();
            });
        });
    }
}
