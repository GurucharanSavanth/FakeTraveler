package cl.coders.faketraveler.ui;

import android.content.Context;
import android.content.Intent;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.detection.PrivacyExposureScanner;
import cl.coders.faketraveler.util.Inputs;

/**
 * Bottom-sheet host for {@link PrivacyExposureScanner}. Runs the scan off the main thread on
 * appearance and on the Run button; renders the risk badge + per-check breakdown with a deep-link
 * button for each fired finding. Read-only — it never changes a system setting.
 */
public class PrivacyGuardBottomSheet extends BottomSheetDialogFragment {

    private static final Executor BG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_privacy_guard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Inputs.<View>requireView(view, R.id.pg_run_btn, "pg_run_btn")
                .setOnClickListener(v -> runScan(view));
        runScan(view);
    }

    private void runScan(@NonNull View view) {
        final Context appCtx = requireContext().getApplicationContext();
        BG.execute(() -> {
            final PrivacyExposureScanner.Report report = PrivacyExposureScanner.run(appCtx);
            MAIN.post(() -> {
                if (!isAdded()) return;
                final View v = getView();
                if (v == null) return;
                bindReport(v, report);
            });
        });
    }

    private void bindReport(@NonNull View view, @NonNull PrivacyExposureScanner.Report report) {
        final TextView badge = Inputs.requireView(view, R.id.pg_risk_badge, "pg_risk_badge");
        final TextView reco = Inputs.requireView(view, R.id.pg_recommendation, "pg_recommendation");
        switch (report.risk) {
            case LOW    -> { badge.setText(R.string.PrivacyGuard_Low);
                             reco.setText(R.string.PrivacyGuard_LowReco); }
            case MEDIUM -> { badge.setText(R.string.PrivacyGuard_Medium);
                             reco.setText(R.string.PrivacyGuard_MediumReco); }
            case HIGH   -> { badge.setText(R.string.PrivacyGuard_High);
                             reco.setText(R.string.PrivacyGuard_HighReco); }
        }
        final LinearLayout host =
                Inputs.requireView(view, R.id.pg_breakdown_host, "pg_breakdown_host");
        host.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(view.getContext());
        for (PrivacyExposureScanner.CheckResult c : report.checks) {
            final View row = inflater.inflate(R.layout.item_privacy_check, host, false);
            final TextView label = Inputs.requireView(row, R.id.check_label, "check_label");
            final TextView detail = Inputs.requireView(row, R.id.check_detail, "check_detail");
            final TextView status = Inputs.requireView(row, R.id.check_status, "check_status");
            final MaterialButton action = Inputs.requireView(row, R.id.check_action, "check_action");
            label.setText(c.label);
            detail.setText(c.detail);
            status.setText(c.passed ? "✅" : "⚠️");
            if (!c.passed && c.settingsAction != null) {
                final String act = c.settingsAction;
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(v -> openSettings(act));
            } else {
                action.setVisibility(View.GONE);
            }
            host.addView(row);
        }
    }

    private void openSettings(@NonNull String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            // device lacks this settings screen — best effort, no crash
        }
    }
}
