package cl.coders.faketraveler.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;

import cl.coders.faketraveler.ExifCleanWorker;
import cl.coders.faketraveler.MainActivity;
import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;

/**
 * Module 6: stats + recently-cleaned list + "scan &amp; clean" + auto-clean toggle. The storage
 * permission request and the {@code ContentObserver} that backs auto-clean are owned by MainActivity
 * (wired in the integration step); this sheet enqueues {@link ExifCleanWorker} and persists the
 * {@code exifAutoClean} preference.
 */
public class ExifCleanerBottomSheet extends BottomSheetDialogFragment {

    public static final String PREF_AUTO_CLEAN = "exifAutoClean";

    /** Implemented by MainActivity, which owns the photo-permission + Photo Picker launchers. */
    public interface Host {
        void onExifScanAll();
        void onExifPickPhotos();
    }

    @Nullable private Host host;
    @Nullable private ExifCleanedAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
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
        return inflater.inflate(R.layout.bottom_sheet_exif_cleaner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final TextView stats = view.findViewById(R.id.exif_stats);
        final View empty = view.findViewById(R.id.exif_empty);
        final RecyclerView list = view.findViewById(R.id.exif_list);
        adapter = new ExifCleanedAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        final Context appCtx = requireContext().getApplicationContext();

        final MaterialSwitch auto = view.findViewById(R.id.exif_auto_switch);
        auto.setChecked(appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_CLEAN, false));
        auto.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            appCtx.getSharedPreferences(MainActivity.sharedPrefKey, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_AUTO_CLEAN, checked).apply();
        });

        view.findViewById(R.id.exif_clean_btn).setOnClickListener(v -> {
            if (host != null) host.onExifScanAll();
        });
        view.findViewById(R.id.exif_pick_btn).setOnClickListener(v -> {
            if (host != null) host.onExifPickPhotos();
        });

        AppDatabase.get(requireContext()).exifCleanedFileDao().getAllCleanedFiles().observe(
                getViewLifecycleOwner(),
                files -> {
                    if (adapter != null) adapter.submit(files);
                    empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
                    stats.setText(getString(R.string.ExifCleaner_Stats, files.size()));
                });
    }
}
