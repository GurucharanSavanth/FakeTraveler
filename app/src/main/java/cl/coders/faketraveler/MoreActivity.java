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

import cl.coders.faketraveler.util.Inputs;

public class MoreActivity extends AppCompatActivity {

    @NonNull
    private static final String TAG = MoreActivity.class.getSimpleName();

    @NonNull
    private final ActivityResultLauncher<String[]> gpxPicker =                           // FIX-012
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onGpxPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge must be enabled before setContentView so insets resolve correctly.
        androidx.activity.EdgeToEdge.enable(this);
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
        wireOemCard();
        wireDebugUnlock();
    }

    private long lastTapTimeMs = 0L;
    private int consecutiveTaps = 0;

    /** Hidden debug-console gate: seven taps on the version footer with < 2 s gap. */
    private void wireDebugUnlock() {
        final TextView v = findViewById(R.id.tv_AppVersion);
        if (v == null) return;
        try {
            final android.content.pm.PackageInfo pi =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            v.setText(getString(R.string.More_VersionFooter,
                    pi.versionName, cl.coders.faketraveler.PackageInfoUtil.versionCode(pi)));
        } catch (Throwable ignored) {}
        v.setOnClickListener(view -> {
            final long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastTapTimeMs > 2_000L) consecutiveTaps = 0;
            lastTapTimeMs = now;
            consecutiveTaps++;
            if (consecutiveTaps >= 7) {
                consecutiveTaps = 0;
                startActivity(new Intent(this,
                        cl.coders.faketraveler.debug.DebugConsoleActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOemCard();
        renderPerAppApproaches();
    }

    /** Builds the list of per-app workaround buttons for the detected OEM (V44 — public
     *  Intents only, no reflection or private APIs). */
    private void renderPerAppApproaches() {
        // perapp_list is androidx.appcompat.widget.LinearLayoutCompat in the layout; cast
        // to ViewGroup so the code tolerates either LinearLayout or LinearLayoutCompat.
        final android.view.ViewGroup host = findViewById(R.id.perapp_list);
        if (host == null) return;
        host.removeAllViews();
        for (cl.coders.faketraveler.perapp.PerAppMockHelper.Approach a
                : cl.coders.faketraveler.perapp.PerAppMockHelper.getApproachesFor(this)) {
            final MaterialButton btn = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(a.title);
            btn.setOnClickListener(v -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(a.title)
                        .setMessage(a.body)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            if (a.deepLink == null) return;
                            try { startActivity(a.deepLink); }
                            catch (Throwable t) {
                                Snackbar.make(host,
                                        R.string.Error_NoActivityForIntent,
                                        Snackbar.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
            host.addView(btn);
        }
    }

    /** Binds the static parts of the OEM whitelist card. Status text refreshes per
     *  {@link #onResume()} so the card reflects changes after the user visits Settings. */
    private void wireOemCard() {
        final MaterialButton fix = findViewById(R.id.oem_card_fix_button);
        if (fix != null) fix.setOnClickListener(v -> OemBatteryOptHelper.showDialog(this));
    }

    private void refreshOemCard() {
        final TextView status = findViewById(R.id.oem_card_status);
        final TextView instr = findViewById(R.id.oem_card_instructions);
        final MaterialButton fix = findViewById(R.id.oem_card_fix_button);
        if (status == null || instr == null || fix == null) return;
        final boolean whitelisted = OemBatteryOptHelper.isWhitelisted(this);
        status.setText(whitelisted ? R.string.Oem_Card_Status_OK : R.string.Oem_Card_Status_Need);
        instr.setText(OemBatteryOptHelper.getInstructions(this));
        fix.setEnabled(!whitelisted);
    }

    private void wireLeafletLicense() {
        final TextView tv = Inputs.requireView(this, R.id.tv_LeafletLicense, "tv_LeafletLicense");
        tv.setText(fromHtml(getString(R.string.ActivityMore_LeafletLicense)));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void wireDoubleField(int id, @NonNull String key, @NonNull SharedPreferences sp) {
        final EditText et = Inputs.requireView(this, id, "wireDoubleField:" + key);
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
        final EditText et = Inputs.requireView(this, id, "wireIntField:" + key);
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
        final CheckBox cb = Inputs.requireView(this, id, "wireCheckBox:" + key);
        final SharedPreferences sp = getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        cb.setChecked(sp.getBoolean(key, dflt));
        cb.setOnCheckedChangeListener((b, checked) -> getApplicationContext()
                .getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit()
                .putBoolean(key, checked).apply());
    }

    private void wireMapProvider(@NonNull SharedPreferences sharedPref) {
        final EditText etMap = Inputs.requireView(this, R.id.et_MapProvider, "et_MapProvider");
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
        final CheckBox cb = Inputs.requireView(this, R.id.cb_RestoreAfterBoot, "cb_RestoreAfterBoot");
        cb.setChecked(SharedPrefsUtil.isRestoreAfterBoot(getApplicationContext()));
        cb.setOnCheckedChangeListener((b, checked) ->
                SharedPrefsUtil.setRestoreAfterBoot(getApplicationContext(), checked));
    }

    private void wireGpxButtons() {                                                      // FIX-012
        final MaterialButton btnImport = Inputs.requireView(this, R.id.btn_ImportGpx, "btn_ImportGpx");
        btnImport.setOnClickListener(v -> {
            try {
                gpxPicker.launch(new String[]{"application/gpx+xml", "application/octet-stream", "text/xml"});
            } catch (Throwable t) {
                Log.e(TAG, "GPX picker launch failed", t);
                snackbar(R.string.Gpx_ImportError);
            }
        });
        final MaterialButton btnPlay = Inputs.requireView(this, R.id.btn_PlayRoute, "btn_PlayRoute");
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
        final MaterialButton btn = Inputs.requireView(this, R.id.btn_OemHelper, "btn_OemHelper");
        btn.setOnClickListener(v -> OemBatteryOptHelper.showDialog(this));
    }

    private void snackbar(@androidx.annotation.StringRes int res) {
        final View root = Inputs.requireView(this, R.id.more_layout, "more_layout");
        Snackbar.make(root, res, Snackbar.LENGTH_SHORT).show();
    }

    private void snackbar(@NonNull String s) {
        final View root = Inputs.requireView(this, R.id.more_layout, "more_layout");
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
