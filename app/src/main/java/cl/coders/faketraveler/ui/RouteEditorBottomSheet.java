package cl.coders.faketraveler.ui;

import android.location.Location;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.RouteWaypointEntity;
import cl.coders.faketraveler.db.SavedRouteEntity;

/**
 * Module 2: builds a {@link SavedRouteEntity} + ordered {@link RouteWaypointEntity} list. Waypoints
 * are added via a numeric dialog, reordered by drag, and deleted in place. Save validates a name and
 * ≥2 waypoints, computes distance/duration, then writes route + waypoints on a background thread
 * (Room forbids main). The hosting {@link cl.coders.faketraveler.RouteLabActivity} list auto-refreshes
 * via its DAO LiveData observer.
 */
public class RouteEditorBottomSheet extends BottomSheetDialogFragment
        implements WaypointAdapter.Listener {

    @NonNull private final List<RouteWaypointEntity> waypoints = new ArrayList<>();
    @Nullable private WaypointAdapter adapter;
    @Nullable private EditText nameInput;
    @Nullable private View empty;
    @Nullable private ItemTouchHelper touchHelper;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_route_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameInput = view.findViewById(R.id.route_editor_name);
        empty = view.findViewById(R.id.route_editor_empty);
        final RecyclerView list = view.findViewById(R.id.route_editor_list);

        adapter = new WaypointAdapter(waypoints, this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder a,
                                            @NonNull RecyclerView.ViewHolder b) {
                if (adapter == null) return false;
                adapter.onMove(a.getBindingAdapterPosition(), b.getBindingAdapterPosition());
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) { }
            @Override public boolean isItemViewSwipeEnabled() { return false; }
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                if (adapter != null) adapter.refresh(); // renumber the seq column
            }
        });
        touchHelper.attachToRecyclerView(list);

        view.findViewById(R.id.route_editor_add_btn).setOnClickListener(v -> showAddDialog());
        view.findViewById(R.id.route_editor_save_btn).setOnClickListener(v -> save());
        refreshEmpty();
    }

    @Override
    public void onDelete(int position) {
        if (position < 0 || position >= waypoints.size() || adapter == null) return;
        waypoints.remove(position);
        adapter.refresh();
        refreshEmpty();
    }

    @Override
    public void onDragStart(@NonNull RecyclerView.ViewHolder vh) {
        if (touchHelper != null) touchHelper.startDrag(vh);
    }

    private void refreshEmpty() {
        if (empty != null) empty.setVisibility(waypoints.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        if (!isAdded()) return;
        final LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        box.setPadding(pad, pad, pad, 0);
        final EditText lat = field(R.string.RouteEditor_Hint_Lat, true);
        final EditText lng = field(R.string.RouteEditor_Hint_Lng, true);
        final EditText speed = field(R.string.RouteEditor_Hint_Speed, true);
        final EditText dwell = field(R.string.RouteEditor_Hint_Dwell, false);
        box.addView(lat); box.addView(lng); box.addView(speed); box.addView(dwell);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.RouteEditor_Dialog_Title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final Double la = parse(lat), ln = parse(lng);
                    if (la == null || ln == null || la < -90 || la > 90 || ln < -180 || ln > 180) {
                        toast(R.string.RouteEditor_Error_LatLng);
                        return;
                    }
                    final RouteWaypointEntity wp = new RouteWaypointEntity();
                    wp.lat = la;
                    wp.lng = ln;
                    final Double sp = parse(speed);
                    wp.speedKmh = sp == null || sp <= 0 ? 30.0 : sp;
                    final Double dw = parse(dwell);
                    wp.dwellMs = dw == null || dw < 0 ? 0L : (long) (double) dw;
                    waypoints.add(wp);
                    if (adapter != null) adapter.refresh();
                    refreshEmpty();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    private EditText field(int hintRes, boolean allowSign) {
        final EditText e = new EditText(requireContext());
        e.setHint(hintRes);
        int type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        if (allowSign) type |= InputType.TYPE_NUMBER_FLAG_SIGNED;
        e.setInputType(type);
        return e;
    }

    @Nullable
    private static Double parse(@NonNull EditText e) {
        final String s = e.getText().toString().trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
    }

    private void save() {
        final String name = nameInput == null ? "" : nameInput.getText().toString().trim();
        if (name.isEmpty()) { toast(R.string.RouteEditor_Error_Name); return; }
        if (waypoints.size() < 2) { toast(R.string.RouteEditor_Error_MinPoints); return; }

        final List<RouteWaypointEntity> snapshot = new ArrayList<>(waypoints);
        double dist = 0;
        long durMs = 0;
        final float[] res = new float[1];
        for (int i = 0; i < snapshot.size() - 1; i++) {
            final RouteWaypointEntity a = snapshot.get(i), b = snapshot.get(i + 1);
            Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, res);
            dist += res[0];
            durMs += (long) (res[0] / Math.max(0.1, a.speedKmh / 3.6) * 1000.0) + b.dwellMs;
        }
        final double totalDist = dist;
        final int durSec = (int) (durMs / 1000L);
        final int count = snapshot.size();

        final android.content.Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            final SavedRouteEntity route = new SavedRouteEntity();
            route.name = name;
            route.createdAt = System.currentTimeMillis();
            route.pointCount = count;
            route.totalDistanceMeters = totalDist;
            route.estimatedDurationSeconds = durSec;
            final long routeId = AppDatabase.get(appCtx).routeDao().insertRoute(route);
            for (int i = 0; i < snapshot.size(); i++) {
                snapshot.get(i).routeId = routeId;
                snapshot.get(i).sequence = i;
            }
            AppDatabase.get(appCtx).routeDao().insertWaypoints(snapshot);
        }, "RouteEditorIO").start();

        dismiss();
    }

    private void toast(int res) {
        if (isAdded()) Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
    }
}
