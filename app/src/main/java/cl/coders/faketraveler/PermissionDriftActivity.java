package cl.coders.faketraveler;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cl.coders.faketraveler.db.ModuleRepository;
import cl.coders.faketraveler.db.PermissionDriftAlertEntity;
import cl.coders.faketraveler.db.PermissionSnapshotEntity;
import cl.coders.faketraveler.ui.PermissionAppAdapter;
import cl.coders.faketraveler.ui.PermissionAppDetailBottomSheet;

/**
 * Module 5: per-app permission audit built from the latest {@link PermissionSnapshotEntity} plus open
 * {@link PermissionDriftAlertEntity}s. "Scan now" runs {@link PermissionScanner} on the repo's IO
 * thread; the list is rebuilt afterwards. A filter toggle limits the list to apps with open alerts.
 */
public class PermissionDriftActivity extends AppCompatActivity
        implements PermissionAppAdapter.Listener {

    @Nullable private PermissionAppAdapter adapter;
    @Nullable private View empty, progress;
    private boolean onlyAlerts = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_permission_drift);

        final View root = findViewById(R.id.permission_drift_root);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left + l, bars.top + t, bars.right + r, bars.bottom + b);
            return WindowInsetsCompat.CONSUMED;
        });

        empty = findViewById(R.id.permission_drift_empty);
        progress = findViewById(R.id.permission_drift_progress);
        final RecyclerView list = findViewById(R.id.permission_drift_list);
        adapter = new PermissionAppAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        final MaterialButton filter = findViewById(R.id.permission_drift_filter_btn);
        filter.setOnClickListener(v -> {
            onlyAlerts = filter.isChecked(); // checkable MaterialButton toggles before this fires
            load();
        });

        final FloatingActionButton fab = findViewById(R.id.permission_drift_scan_fab);
        fab.setOnClickListener(v -> scanNow());

        load();
    }

    private void scanNow() {
        showProgress(true);
        final ModuleRepository repo = ModuleRepository.get(this);
        repo.io(() -> {
            try {
                new PermissionScanner(getApplicationContext()).scan();
            } catch (Throwable ignored) {
            }
            loadInternal(repo);
        });
    }

    private void load() {
        showProgress(true);
        final ModuleRepository repo = ModuleRepository.get(this);
        repo.io(() -> loadInternal(repo));
    }

    private void loadInternal(@NonNull ModuleRepository repo) {
        final long ts = repo.getPermissionSnapshotDao().getLatestTimestamp();
        final List<PermissionSnapshotEntity> snaps = ts > 0
                ? repo.getPermissionSnapshotDao().getSnapshotsAt(ts)
                : new ArrayList<>();
        final List<PermissionDriftAlertEntity> alerts =
                repo.getPermissionDriftAlertDao().getUnacknowledgedAlerts();

        final Map<String, PermissionAppAdapter.AppRow> map = new LinkedHashMap<>();
        for (PermissionSnapshotEntity s : snaps) {
            PermissionAppAdapter.AppRow row = map.get(s.packageName);
            if (row == null) {
                row = new PermissionAppAdapter.AppRow();
                row.packageName = s.packageName;
                row.appName = s.appName;
                map.put(s.packageName, row);
            }
            row.total++;
            if (s.status == 1) row.granted++;
            if (s.isDangerous) row.dangerous++;
        }
        for (PermissionDriftAlertEntity a : alerts) {
            PermissionAppAdapter.AppRow row = map.get(a.packageName);
            if (row == null) {
                row = new PermissionAppAdapter.AppRow();
                row.packageName = a.packageName;
                row.appName = a.appName;
                map.put(a.packageName, row);
            }
            row.alertCount++;
        }

        final List<PermissionAppAdapter.AppRow> rows = new ArrayList<>(map.values());
        if (onlyAlerts) {
            final List<PermissionAppAdapter.AppRow> filtered = new ArrayList<>();
            for (PermissionAppAdapter.AppRow row : rows) if (row.alertCount > 0) filtered.add(row);
            rows.clear();
            rows.addAll(filtered);
        }
        java.util.Collections.sort(rows, Comparator
                .comparingInt((PermissionAppAdapter.AppRow x) -> x.alertCount).reversed()
                .thenComparing(x -> x.appName == null ? "" : x.appName.toLowerCase()));

        runOnUiThread(() -> {
            if (adapter != null) adapter.submit(rows);
            if (empty != null) empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            showProgress(false);
        });
    }

    private void showProgress(boolean show) {
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onApp(@NonNull PermissionAppAdapter.AppRow row) {
        PermissionAppDetailBottomSheet.newInstance(row.packageName, row.appName)
                .show(getSupportFragmentManager(), "permAppDetail");
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(); // reflect acknowledgements made in the detail sheet
    }
}
