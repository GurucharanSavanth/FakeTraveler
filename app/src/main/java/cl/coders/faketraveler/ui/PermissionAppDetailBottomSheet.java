package cl.coders.faketraveler.ui;

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

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;

/** Module 5: one app's permissions + an "acknowledge all alerts" action. */
public class PermissionAppDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PKG = "pkg";
    private static final String ARG_NAME = "name";

    @NonNull
    public static PermissionAppDetailBottomSheet newInstance(@NonNull String pkg, @Nullable String name) {
        final PermissionAppDetailBottomSheet f = new PermissionAppDetailBottomSheet();
        final Bundle b = new Bundle();
        b.putString(ARG_PKG, pkg);
        b.putString(ARG_NAME, name);
        f.setArguments(b);
        return f;
    }

    @Nullable private PermissionTimelineAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_permission_app_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final String pkg = getArguments() != null ? getArguments().getString(ARG_PKG, "") : "";
        final String name = getArguments() != null ? getArguments().getString(ARG_NAME, "") : "";

        ((TextView) view.findViewById(R.id.permission_detail_title))
                .setText(name == null || name.isEmpty() ? pkg : name);
        ((TextView) view.findViewById(R.id.permission_detail_pkg)).setText(pkg);

        final RecyclerView list = view.findViewById(R.id.permission_detail_list);
        adapter = new PermissionTimelineAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        final android.content.Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            final java.util.List<cl.coders.faketraveler.db.PermissionSnapshotEntity> rows =
                    AppDatabase.get(appCtx).permissionSnapshotDao().getSnapshotsForApp(pkg);
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                if (adapter != null) adapter.submit(rows);
            });
        }, "PermDetailIO").start();

        view.findViewById(R.id.permission_detail_ack_btn).setOnClickListener(v -> {
            new Thread(() -> AppDatabase.get(appCtx).permissionDriftAlertDao().acknowledgeForApp(pkg),
                    "PermAckIO").start();
            if (isAdded()) Toast.makeText(requireContext(),
                    R.string.PermissionDrift_Acknowledged, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }
}
