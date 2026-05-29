package cl.coders.faketraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.GeoFenceEventEntity;

/**
 * Module 3: read-only log of geofence transitions with a CSV share action. Export is inline text
 * via {@code ACTION_SEND} (no FileProvider needed); the Evidence Export module handles file-based
 * exports with checksums.
 */
public class GeoFenceEventBottomSheet extends BottomSheetDialogFragment {

    @Nullable private GeoFenceEventAdapter adapter;
    @NonNull private final List<GeoFenceEventEntity> current = new ArrayList<>();
    @NonNull private final Map<Long, String> fenceNames = new HashMap<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_geofence_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final View empty = view.findViewById(R.id.geofence_event_empty);
        final RecyclerView list = view.findViewById(R.id.geofence_event_list);
        adapter = new GeoFenceEventAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.geofence_event_export_btn).setOnClickListener(v -> exportCsv());

        AppDatabase.get(requireContext()).geoFenceDao().getAllEvents().observe(
                getViewLifecycleOwner(),
                events -> {
                    current.clear();
                    current.addAll(events);
                    if (adapter != null) adapter.submit(events);
                    empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                });

        AppDatabase.get(requireContext()).geoFenceDao().getAllFences().observe(
                getViewLifecycleOwner(),
                fences -> {
                    fenceNames.clear();
                    for (cl.coders.faketraveler.db.GeoFenceEntity f : fences) fenceNames.put(f.id, f.name);
                    if (adapter != null) adapter.setFenceNames(fenceNames);
                });
    }

    private void exportCsv() {
        if (!isAdded() || current.isEmpty()) return;
        final StringBuilder sb = new StringBuilder("fenceId,fenceName,type,timestamp,lat,lng,sessionId\n");
        for (GeoFenceEventEntity e : current) {
            final String name = fenceNames.get(e.geofenceId);
            sb.append(e.geofenceId).append(',')
                    .append(csv(name != null ? name : "")).append(',')
                    .append(e.eventType).append(',')
                    .append(e.timestamp).append(',')
                    .append(String.format(Locale.US, "%.6f", e.triggeredLat)).append(',')
                    .append(String.format(Locale.US, "%.6f", e.triggeredLng)).append(',')
                    .append(e.sessionId).append('\n');
        }
        final Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_SUBJECT, "geofence_events.csv")
                .putExtra(Intent.EXTRA_TEXT, sb.toString());
        try {
            startActivity(Intent.createChooser(send, getString(R.string.GeoFence_Event_Export)));
        } catch (Throwable ignored) {
        }
    }

    /** RFC-4180-ish escaping: wrap in quotes and double embedded quotes when needed. */
    @NonNull
    private static String csv(@NonNull String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
