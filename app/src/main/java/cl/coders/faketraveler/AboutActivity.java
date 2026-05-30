package cl.coders.faketraveler;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Presentation-only About / help surface for the trimmed global mock workflow. */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about);
        final View root = findViewById(R.id.about_root);
        final int startLeft = root.getPaddingLeft();
        final int startTop = root.getPaddingTop();
        final int startRight = root.getPaddingRight();
        final int startBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left + startLeft, bars.top + startTop,
                    bars.right + startRight, bars.bottom + startBottom);
            return WindowInsetsCompat.CONSUMED;
        });

        bindVersion();
        bindHowTo();
        bindPermissionRows();
        findViewById(R.id.about_license_button).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.About_LicensesTitle)
                        .setMessage(R.string.About_LicenseBody)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
        findViewById(R.id.about_issue_button).setOnClickListener(v ->
                openUrl(getString(R.string.About_IssueUrl)));
        findViewById(R.id.about_source_button).setOnClickListener(v ->
                openUrl(getString(R.string.About_SourceUrl)));
    }

    private void bindVersion() {
        try {
            final PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ((TextView) findViewById(R.id.about_version)).setText(getString(
                    R.string.About_Version, pi.versionName, PackageInfoUtil.versionCode(pi)));
        } catch (Throwable ignored) {
        }
    }

    private void bindHowTo() {
        final TextView v = findViewById(R.id.about_step_3);
        if (v == null) return;
        v.setText(getString(R.string.About_HowTo_Step3, getString(R.string.ActivityMain_Apply)));
    }

    private void bindPermissionRows() {
        setPermissionText(R.id.about_permission_location, R.string.About_Perm_Location,
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED);
        setPermissionText(R.id.about_permission_mock, R.string.About_Perm_MockLocation,
                PermissionChecker.isMockLocationEnabled(this));
        setPermissionText(R.id.about_permission_background, R.string.About_Perm_Background,
                OemBatteryOptHelper.isWhitelisted(this));
    }

    private void setPermissionText(int viewId, int labelId, boolean granted) {
        final TextView row = findViewById(viewId);
        row.setText(getString(R.string.About_Perm_Row, getString(labelId), getString(granted
                ? R.string.About_Perm_StatusGranted
                : R.string.About_Perm_StatusMissing)));
    }

    private void openUrl(@NonNull String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
        }
    }
}
