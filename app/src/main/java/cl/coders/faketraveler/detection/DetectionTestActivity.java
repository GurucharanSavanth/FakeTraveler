package cl.coders.faketraveler.detection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.util.Inputs;

/** Runs {@link DetectionEngine} and renders the result as a risk badge + per-check list. */
public class DetectionTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection_test);
        Inputs.<android.view.View>requireView(this, R.id.run_btn, "run_btn")
                .setOnClickListener(v -> runTest());
        runTest();
    }

    private void runTest() {
        final DetectionEngine.Report report = DetectionEngine.run(this);
        final TextView risk = Inputs.requireView(this, R.id.risk_badge, "risk_badge");
        final TextView reco = Inputs.requireView(this, R.id.risk_recommendation, "risk_recommendation");
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
        final LinearLayout host = Inputs.requireView(this, R.id.breakdown_host, "breakdown_host");
        host.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(this);
        for (DetectionEngine.CheckResult c : report.checks) {
            final View row = inflater.inflate(R.layout.item_detection_check, host, false);
            ((TextView) row.findViewById(R.id.check_label)).setText(c.label);
            ((TextView) row.findViewById(R.id.check_detail)).setText(c.detail);
            ((TextView) row.findViewById(R.id.check_status)).setText(c.passed ? "✅" : "❌");
            host.addView(row);
        }
    }
}
