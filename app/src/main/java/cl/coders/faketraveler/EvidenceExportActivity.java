package cl.coders.faketraveler;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;

import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.EvidenceReportEntity;
import cl.coders.faketraveler.db.ModuleRepository;
import cl.coders.faketraveler.ui.EvidenceReportAdapter;

/**
 * Module 8: pick modules + format, generate a report via {@link EvidenceExporter} (on the repo IO
 * thread), and manage past reports (share via FileProvider, delete). Date-range pickers are out of
 * scope here (exports default to all dates); the exporter already supports a range when supplied.
 */
public class EvidenceExportActivity extends AppCompatActivity
        implements EvidenceReportAdapter.Listener {

    @Nullable private EvidenceReportAdapter adapter;
    @Nullable private View progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_evidence_export);

        final View root = findViewById(R.id.evidence_root);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left + l, bars.top + t, bars.right + r, bars.bottom + b);
            return WindowInsetsCompat.CONSUMED;
        });

        progress = findViewById(R.id.evidence_progress);
        final View empty = findViewById(R.id.evidence_empty);
        final RecyclerView list = findViewById(R.id.evidence_list);
        adapter = new EvidenceReportAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.evidence_generate_btn).setOnClickListener(v -> generate());

        AppDatabase.get(this).evidenceReportDao().getAllReports().observe(this, reports -> {
            if (adapter != null) adapter.submit(reports);
            empty.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void generate() {
        final EvidenceExporter.Options o = new EvidenceExporter.Options();
        o.sessions = cb(R.id.ev_cb_sessions);
        o.routes = cb(R.id.ev_cb_routes);
        o.geofences = cb(R.id.ev_cb_geofences);
        o.permissions = cb(R.id.ev_cb_permissions);
        o.exif = cb(R.id.ev_cb_exif);
        o.wipes = cb(R.id.ev_cb_wipes);
        o.format = selectedFormat();

        showProgress(true);
        final EvidenceExporter exporter = new EvidenceExporter(getApplicationContext());
        ModuleRepository.get(this).io(() -> {
            boolean ok = true;
            try {
                exporter.export(o);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                showProgress(false);
                Toast.makeText(this, done ? R.string.Evidence_Generated : R.string.Evidence_Failed,
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private boolean cb(int id) {
        final MaterialCheckBox c = findViewById(id);
        return c != null && c.isChecked();
    }

    @NonNull
    private String selectedFormat() {
        final MaterialButtonToggleGroup group = findViewById(R.id.evidence_format_group);
        final int checked = group.getCheckedButtonId();
        if (checked == R.id.evidence_fmt_csv) return "csv";
        if (checked == R.id.evidence_fmt_pdf) return "pdf";
        return "json";
    }

    private void showProgress(boolean show) {
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onShare(@NonNull EvidenceReportEntity report) {
        try {
            final File f = new File(report.filePath);
            final Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            final Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(mime(report.reportType))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.Evidence_Share)));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.Evidence_ShareFailed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(@NonNull EvidenceReportEntity report) {
        ModuleRepository.get(this).io(() -> {
            try {
                final File f = new File(report.filePath);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } catch (Throwable ignored) {
            }
            AppDatabase.get(getApplicationContext()).evidenceReportDao().delete(report);
        });
    }

    @NonNull
    private static String mime(@Nullable String type) {
        if ("pdf".equals(type)) return "application/pdf";
        if ("csv".equals(type)) return "text/csv";
        return "application/json";
    }
}
