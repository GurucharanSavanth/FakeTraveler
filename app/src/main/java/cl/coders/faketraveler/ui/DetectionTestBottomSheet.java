package cl.coders.faketraveler.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.detection.DetectionEngine;
import cl.coders.faketraveler.util.Inputs;

/**
 * Bottom-sheet host for {@link DetectionEngine}. Ports the original
 * {@code DetectionTestActivity}: runs the engine once on appearance,
 * renders the risk badge + per-check breakdown, and re-runs on the
 * "Run" button. Placeholder layout — T15 will rebuild the surface.
 */
public class DetectionTestBottomSheet extends BottomSheetDialogFragment {

    private static final Executor BG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_detection_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Inputs.<View>requireView(view, R.id.run_btn, "run_btn")
                .setOnClickListener(v -> runTest(view));
        runTest(view);
    }

    private void runTest(@NonNull View view) {
        final Context appCtx = requireContext().getApplicationContext();
        BG.execute(() -> {
            final DetectionEngine.Report report = DetectionEngine.run(appCtx);
            MAIN.post(() -> {
                if (!isAdded()) return;
                bindReport(view, report);
            });
        });
    }

    private void bindReport(@NonNull View view, @NonNull DetectionEngine.Report report) {
        final TextView risk = Inputs.requireView(view, R.id.risk_badge, "risk_badge");
        final TextView reco = Inputs.requireView(view, R.id.risk_recommendation, "risk_recommendation");
        switch (report.risk) {
            case LOW    -> {
                risk.setText(R.string.Detection_Low);
                reco.setText(R.string.Detection_Low_Recommendation);
            }
            case MEDIUM -> {
                risk.setText(R.string.Detection_Medium);
                reco.setText(R.string.Detection_Medium_Recommendation);
            }
            case HIGH   -> {
                risk.setText(R.string.Detection_High);
                reco.setText(R.string.Detection_High_Recommendation);
            }
        }
        final LinearLayout host = Inputs.requireView(view, R.id.breakdown_host, "breakdown_host");
        host.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (DetectionEngine.CheckResult c : report.checks) {
            final View row = inflater.inflate(R.layout.item_detection_check, host, false);
            ((TextView) row.findViewById(R.id.check_label)).setText(c.label);
            ((TextView) row.findViewById(R.id.check_detail)).setText(c.detail);
            ((TextView) row.findViewById(R.id.check_status)).setText(c.passed ? "✅" : "❌");
            host.addView(row);
        }
    }
}
