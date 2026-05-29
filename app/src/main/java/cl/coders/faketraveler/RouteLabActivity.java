package cl.coders.faketraveler;

import android.content.Intent;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.SavedRouteEntity;
import cl.coders.faketraveler.ui.RouteAdapter;
import cl.coders.faketraveler.ui.RouteEditorBottomSheet;

/**
 * Module 2: lists saved routes, opens {@link RouteEditorBottomSheet} to create one, and returns a
 * chosen route id to {@link MainActivity} (which owns the service binder) for playback via
 * {@link RoutePlayer}. The live Leaflet map overlay from the spec is deferred — editing is by
 * numeric waypoint entry; map-tap capture would need new JS in {@code map.html}/{@code init.js}.
 */
public class RouteLabActivity extends AppCompatActivity implements RouteAdapter.Listener {

    public static final String EXTRA_PLAY_ROUTE_ID = "cl.coders.faketraveler.extra.PLAY_ROUTE_ID";

    @Nullable private RouteAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_route_lab);

        final View root = findViewById(R.id.route_lab_root);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left + l, bars.top + t, bars.right + r, bars.bottom + b);
            return WindowInsetsCompat.CONSUMED;
        });

        final View empty = findViewById(R.id.route_lab_empty);
        final RecyclerView list = findViewById(R.id.route_lab_list);
        adapter = new RouteAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        final FloatingActionButton fab = findViewById(R.id.route_lab_fab);
        fab.setOnClickListener(v ->
                new RouteEditorBottomSheet().show(getSupportFragmentManager(), "routeEditor"));

        AppDatabase.get(this).routeDao().getAllRoutes().observe(this, routes -> {
            if (adapter != null) adapter.submit(routes);
            empty.setVisibility(routes.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onPlay(@NonNull SavedRouteEntity route) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PLAY_ROUTE_ID, route.id));
        finish();
    }

    @Override
    public void onLongPress(@NonNull SavedRouteEntity route) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.RouteLab_Delete_Title)
                .setMessage(R.string.RouteLab_Delete_Message)
                .setPositiveButton(android.R.string.ok, (d, w) -> runDb(() ->
                        AppDatabase.get(getApplicationContext()).routeDao().deleteRoute(route)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void runDb(@NonNull Runnable r) {
        final Thread th = new Thread(r, "RouteLabIO");
        th.setDaemon(true);
        th.start();
    }
}
