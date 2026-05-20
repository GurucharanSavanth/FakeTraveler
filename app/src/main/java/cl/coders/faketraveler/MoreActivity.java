package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.DECIMAL_FORMAT;
import static cl.coders.faketraveler.MainActivity.sharedPrefKey;
import static cl.coders.faketraveler.SharedPrefsUtil.getDouble;
import static cl.coders.faketraveler.SharedPrefsUtil.putDouble;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.util.Locale;

public class MoreActivity extends AppCompatActivity {

    @NonNull
    private static final String TAG = MoreActivity.class.getSimpleName();

    @NonNull
    private final ActivityResultLauncher<String[]> gpxPicker =                           // FIX-012
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onGpxPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.more_layout), (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        final Context context = getApplicationContext();
        SharedPrefsUtil.migrateToV2(context);                                            // FIX-014
        final SharedPreferences sharedPref = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);

        wireLeafletLicense();
        wireDoubleField(R.id.et_DMockLat, "dLat", sharedPref);
        wireDoubleField(R.id.et_DMockLon, "dLng", sharedPref);
        wireIntField(R.id.et_MockCount, "mockCount", 0, sharedPref, /*minClamp*/ 0);
        wireIntField(R.id.et_MockFrequency, "mockFrequency", 10, sharedPref, /*minClamp*/ 1);
        wireCheckBox(R.id.cb_MockSpeed, "mockSpeed", true);
        wireMapProvider(sharedPref);
        wireRestoreAfterBoot();                                                          // FIX-005
        wireGpxButtons();                                                                // FIX-012
        wireOemHelper();                                                                 // FIX-011
    }

    private void wireLeafletLicense() {
        final TextView tv = findViewById(R.id.tv_LeafletLicense);
        tv.setText(fromHtml(getString(R.string.ActivityMore_LeafletLicense)));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void wireDoubleField(int id, @NonNull String key, @NonNull SharedPreferences sp) {
        final EditText et = findViewById(id);
        et.setText(DECIMAL_FORMAT.format(getDouble(sp, key, 0)));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                final SharedPreferences.Editor e =
                        getApplicationContext().getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
                if (et.getText().toString().isBlank()) {
                    putDouble(e, key, 0);
                } else {
                    try {
                        putDouble(e, key, Double.parseDouble(et.getText().toString()));
                    } catch (Throwable t) {
                        Log.e(TAG, "Could not parse " + key + "!", t);
                    }
                }
                e.apply();
            }
        });
    }

    private void wireIntField(int id, @NonNull String key, int dflt,
                              @NonNull SharedPreferences sp, int minClamp) {
        final EditText et = findViewById(id);
        et.setText(String.format(Locale.ROOT, "%d", sp.getInt(key, dflt)));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                final SharedPreferences.Editor e =
                        getApplicationContext().getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
                int value = dflt;
                if (!et.getText().toString().isBlank()) {
                    try {
                        value = Integer.parseInt(et.getText().toString());
                    } catch (Throwable t) {
                        Log.e(TAG, "Could not parse " + key + "!", t);
                    }
                }
                if (value < minClamp) value = minClamp;                                  // FIX-014
                e.putInt(key, value).apply();
            }
        });
    }

    private void wireCheckBox(int id, @NonNull String key, boolean dflt) {
        final CheckBox cb = findViewById(id);
        final SharedPreferences sp = getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        cb.setChecked(sp.getBoolean(key, dflt));
        cb.setOnCheckedChangeListener((b, checked) -> getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit()
                .putBoolean(key, checked).apply());
    }

    private void wireMapProvider(@NonNull SharedPreferences sharedPref) {
        final EditText etMap = findViewById(R.id.et_MapProvider);
        etMap.setText(sharedPref.getString("mapProvider",
                MapProviderUtil.getDefaultMapProvider(Locale.getDefault())));
        etMap.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                final SharedPreferences.Editor e =
                        getApplicationContext().getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();
                final String text = etMap.getText().toString();
                e.putString("mapProvider", text.isBlank()
                        ? MapProviderUtil.getDefaultMapProvider(Locale.getDefault())
                        : text);
                e.apply();
            }
        });
    }

    private void wireRestoreAfterBoot() {                                                // FIX-005
        final CheckBox cb = findViewById(R.id.cb_RestoreAfterBoot);
        cb.setChecked(SharedPrefsUtil.isRestoreAfterBoot(getApplicationContext()));
        cb.setOnCheckedChangeListener((b, checked) ->
                SharedPrefsUtil.setRestoreAfterBoot(getApplicationContext(), checked));
    }

    private void wireGpxButtons() {                                                      // FIX-012
        final MaterialButton btnImport = findViewById(R.id.btn_ImportGpx);
        btnImport.setOnClickListener(v -> {
            try {
                gpxPicker.launch(new String[]{"application/gpx+xml", "application/octet-stream", "text/xml"});
            } catch (Throwable t) {
                Log.e(TAG, "GPX picker launch failed", t);
                snackbar(R.string.Gpx_ImportError);
            }
        });
        final MaterialButton btnPlay = findViewById(R.id.btn_PlayRoute);
        btnPlay.setOnClickListener(v -> playRouteIfAvailable());
    }

    private void onGpxPicked(@androidx.annotation.Nullable Uri uri) {
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) { snackbar(R.string.Gpx_ImportError); return; }
            final GpxImporter.Route route = GpxImporter.parse(in);
            SharedPrefsUtil.saveRouteJson(getApplicationContext(), GpxImporter.toJson(route));
            final int n = route.points().size();
            snackbar(getResources().getQuantityString(R.plurals.Gpx_ImportSuccess, n, n));
        } catch (java.io.IOException ioe) {
            Log.e(TAG, "GPX too large or unreadable", ioe);
            snackbar(getResources().getQuantityString(R.plurals.Gpx_TooLarge,
                    GpxImporter.MAX_POINTS, GpxImporter.MAX_POINTS));
        } catch (Throwable t) {
            Log.e(TAG, "GPX parse failed", t);
            snackbar(R.string.Gpx_ImportError);
        }
    }

    private void playRouteIfAvailable() {                                                // FIX-012
        if (!PermissionChecker.isMockLocationEnabled(getApplicationContext())) {
            PermissionChecker.showDevSettingsDialog(this);
            return;
        }
        final String json = SharedPrefsUtil.loadRouteJson(getApplicationContext());
        if (json == null) { snackbar(R.string.Gpx_NoRoute); return; }
        final SharedPreferences sp = getApplicationContext().getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        int freq = sp.getInt("mockFrequency", 10);
        if (freq <= 0) freq = 1;
        final Intent svc = new Intent(this, MockedLocationService.class)
                .setAction(MockedLocationService.ACTION_PLAY_ROUTE)
                .putExtra(MockedLocationService.EXTRA_ROUTE_JSON, json)
                .putExtra(MockedLocationService.EXTRA_FREQUENCY, (long) freq * 1000L);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, svc);
            } else {
                startService(svc);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start route playback", t);
        }
    }

    private void wireOemHelper() {                                                       // FIX-011
        final MaterialButton btn = findViewById(R.id.btn_OemHelper);
        btn.setOnClickListener(v -> OemBatteryOptHelper.showDialog(this));
    }

    private void snackbar(@androidx.annotation.StringRes int res) {
        final View root = findViewById(R.id.more_layout);
        Snackbar.make(root, res, Snackbar.LENGTH_SHORT).show();
    }

    private void snackbar(@NonNull String s) {
        final View root = findViewById(R.id.more_layout);
        Snackbar.make(root, s, Snackbar.LENGTH_SHORT).show();
    }

    private static Spanned fromHtml(@NonNull String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        return fromLegacyHtml(html);
    }

    @SuppressWarnings("deprecation")
    private static Spanned fromLegacyHtml(@NonNull String html) {
        return Html.fromHtml(html);
    }
}
