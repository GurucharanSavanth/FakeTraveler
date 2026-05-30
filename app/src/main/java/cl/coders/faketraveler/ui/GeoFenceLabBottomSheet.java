package cl.coders.faketraveler.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.GeoFenceEntity;

/**
 * Module 3: manages circular geofences (name, center, radius, active toggle) and opens the event
 * log. Polygon fences are supported by the engine/schema but the polygon map editor is deferred
 * (same Leaflet-JS dependency as Route Lab); this sheet creates circular fences via numeric entry.
 *
 * <p>Toggling/editing updates the DB; the running {@link cl.coders.faketraveler.GeoFenceMonitor} is
 * told to reload by MainActivity (wired in the integration step).
 */
public class GeoFenceLabBottomSheet extends BottomSheetDialogFragment
        implements GeoFenceAdapter.Listener {

    /** Implemented by MainActivity to reload the running GeoFenceMonitor after fence edits. */
    public interface Host {
        void onGeofencesChanged();
    }

    @Nullable private Host host;
    @Nullable private GeoFenceAdapter adapter;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof Host) host = (Host) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_geofence_lab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final View empty = view.findViewById(R.id.geofence_empty);
        final RecyclerView list = view.findViewById(R.id.geofence_list);
        adapter = new GeoFenceAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.geofence_add_btn).setOnClickListener(v -> showFenceDialog(null));
        view.findViewById(R.id.geofence_events_btn).setOnClickListener(v -> {
            if (isAdded()) new GeoFenceEventBottomSheet().show(getParentFragmentManager(), "geofenceEvents");
        });

        AppDatabase.get(requireContext()).geoFenceDao().getAllFences().observe(
                getViewLifecycleOwner(),
                fences -> {
                    if (adapter != null) adapter.submit(fences);
                    empty.setVisibility(fences.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onToggleActive(@NonNull GeoFenceEntity fence, boolean active) {
        fence.active = active;
        final android.content.Context appCtx = requireContext().getApplicationContext();
        runDbNotify(() -> AppDatabase.get(appCtx).geoFenceDao().update(fence));
    }

    @Override
    public void onEdit(@NonNull GeoFenceEntity fence) { showFenceDialog(fence); }

    @Override
    public void onDelete(@NonNull GeoFenceEntity fence) {
        if (!isAdded()) return;
        final android.content.Context appCtx = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.GeoFence_Delete_Title)
                .setMessage(R.string.GeoFence_Delete_Message)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> runDbNotify(() -> AppDatabase.get(appCtx).geoFenceDao().delete(fence)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFenceDialog(@Nullable GeoFenceEntity existing) {
        if (!isAdded()) return;
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        box.setPadding(pad, pad, pad, 0);

        final EditText name = text(R.string.GeoFence_Hint_Name, InputType.TYPE_CLASS_TEXT);
        final EditText lat = number(R.string.GeoFence_Hint_Lat);
        final EditText lng = number(R.string.GeoFence_Hint_Lng);
        final EditText radius = number(R.string.GeoFence_Hint_Radius);
        if (existing != null) {
            name.setText(existing.name);
            lat.setText(String.valueOf(existing.centerLat));
            lng.setText(String.valueOf(existing.centerLng));
            radius.setText(String.valueOf(existing.radiusMeters));
        }
        box.addView(name); box.addView(lat); box.addView(lng); box.addView(radius);

        final android.content.Context appCtx = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? R.string.GeoFence_Add_Title : R.string.GeoFence_Edit_Title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final String nm = name.getText().toString().trim();
                    final Double la = parse(lat), ln = parse(lng), rad = parse(radius);
                    if (nm.isEmpty() || la == null || ln == null || rad == null
                            || la < -90 || la > 90 || ln < -180 || ln > 180 || rad <= 0) {
                        toast(R.string.GeoFence_Error_Invalid);
                        return;
                    }
                    final GeoFenceEntity f = existing != null ? existing : new GeoFenceEntity();
                    f.name = nm;
                    f.centerLat = la;
                    f.centerLng = ln;
                    f.radiusMeters = (float) (double) rad;
                    f.type = 0;
                    if (existing == null) {
                        f.monitorEntry = true;
                        f.monitorExit = true;
                        f.active = true;
                    }
                    runDbNotify(() -> {
                        if (existing == null) AppDatabase.get(appCtx).geoFenceDao().insert(f);
                        else AppDatabase.get(appCtx).geoFenceDao().update(f);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    private EditText text(int hint, int type) {
        final EditText e = new EditText(requireContext());
        e.setHint(hint);
        e.setInputType(type);
        return e;
    }

    @NonNull
    private EditText number(int hint) {
        return text(hint, InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
    }

    @Nullable
    private static Double parse(@NonNull EditText e) {
        final String s = e.getText().toString().trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
    }

    private void toast(int res) {
        if (isAdded()) Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
    }

    /** Run a fence mutation off the main thread, then tell the host to reload the monitor. */
    private void runDbNotify(@NonNull Runnable dbOp) {
        final Host h = host;
        runDb(() -> {
            dbOp.run();
            if (h != null) h.onGeofencesChanged();
        });
    }

    private static void runDb(@NonNull Runnable r) {
        final Thread t = new Thread(r, "GeoFenceLabIO");
        t.setDaemon(true);
        t.start();
    }
}
