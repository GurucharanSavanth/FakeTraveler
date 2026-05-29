package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.PermissionSnapshotEntity;

/** Per-app permission list for {@link PermissionAppDetailBottomSheet} (Module 5). */
public class PermissionTimelineAdapter extends RecyclerView.Adapter<PermissionTimelineAdapter.VH> {

    @NonNull private final List<PermissionSnapshotEntity> items = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<PermissionSnapshotEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_permission_timeline, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        PermissionSnapshotEntity s = items.get(pos);
        h.perm.setText(shortName(s.permission));
        final int statusRes = s.status == 1
                ? R.string.PermissionDrift_StatusGranted
                : R.string.PermissionDrift_StatusDenied;
        h.status.setText(h.itemView.getContext().getString(statusRes));
        h.status.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                s.isDangerous && s.status == 1
                        ? androidx.appcompat.R.color.error_color_material_light
                        : android.R.color.darker_gray));
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    private static String shortName(@NonNull String perm) {
        final int dot = perm.lastIndexOf('.');
        return dot >= 0 && dot < perm.length() - 1 ? perm.substring(dot + 1) : perm;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView perm;
        final TextView status;
        VH(@NonNull View v) {
            super(v);
            perm = v.findViewById(R.id.permission_timeline_name);
            status = v.findViewById(R.id.permission_timeline_status);
        }
    }
}
