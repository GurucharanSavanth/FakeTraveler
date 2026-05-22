package cl.coders.faketraveler.route;

import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;

import cl.coders.faketraveler.GpxImporter;
import cl.coders.faketraveler.MockLogger;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.SharedPrefsUtil;

/**
 * Picks a GPX file via the Storage Access Framework and persists the parsed route to
 * shared prefs (and, by way of the dual-write contract, to DataStore).
 *
 * <p>Hard-caps the input at {@value #MAX_BYTES} bytes before parsing. Without this the
 * parser would happily allocate memory for a 100 MB file before its own point cap kicked
 * in (V38).
 */
public class RouteImportActivity extends AppCompatActivity {

    private static final String TAG = "RouteImport";
    private static final long MAX_BYTES = 5L * 1024L * 1024L;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::onFilePicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_import);
        findViewById(R.id.route_pick_btn).setOnClickListener(v ->
                picker.launch(new String[]{"application/gpx+xml", "application/octet-stream", "*/*"}));
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null && pfd.getStatSize() > MAX_BYTES) {
                    Snackbar.make(findViewById(android.R.id.content),
                            R.string.Route_TooLarge, Snackbar.LENGTH_LONG).show();
                    MockLogger.log("route_import_reject", "too_large=" + pfd.getStatSize());
                    return;
                }
            }
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return;
                GpxImporter.Route route = GpxImporter.parse(is);
                SharedPrefsUtil.saveRouteJson(this, GpxImporter.toJson(route));
                setResult(RESULT_OK);
                MockLogger.log("route_import_ok", "pts=" + route.points().size());
                final int n = route.points().size();
                Snackbar.make(findViewById(android.R.id.content),
                        getResources().getQuantityString(R.plurals.Route_Imported, n, n),
                        Snackbar.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            Log.e(TAG, "GPX parse failed", t);
            MockLogger.log("route_import_error", t.getClass().getSimpleName());
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.Route_ParseFailed, Snackbar.LENGTH_LONG).show();
        }
    }
}
