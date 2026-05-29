package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.GeoFenceEntity;

/** RecyclerView adapter for {@link GeoFenceLabBottomSheet} (Module 3). */
public class GeoFenceAdapter extends RecyclerView.Adapter<GeoFenceAdapter.VH> {

    public interface Listener {
        void onToggleActive(@NonNull GeoFenceEntity fence, boolean active);
        void onEdit(@NonNull GeoFenceEntity fence);
        void onDelete(@NonNull GeoFenceEntity fence);
    }

    @NonNull private final List<GeoFenceEntity> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public GeoFenceAdapter(@NonNull Listener listener) { this.listener = listener; }

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<GeoFenceEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_geofence, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        GeoFenceEntity f = items.get(pos);
        h.name.setText(f.name);
        if (f.type == 0) {
            h.meta.setText(String.format(Locale.US, "%.5f, %.5f · r=%.0fm",
                    f.centerLat, f.centerLng, f.radiusMeters));
        } else {
            h.meta.setText(h.itemView.getContext().getString(R.string.GeoFence_Type_Polygon));
        }
        h.active.setOnCheckedChangeListener(null);
        h.active.setChecked(f.active);
        h.active.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) listener.onToggleActive(f, checked);
        });
        h.itemView.setOnClickListener(v -> listener.onEdit(f));
        h.delete.setOnClickListener(v -> listener.onDelete(f));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final MaterialSwitch active;
        final View delete;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.geofence_name);
            meta = v.findViewById(R.id.geofence_meta);
            active = v.findViewById(R.id.geofence_active);
            delete = v.findViewById(R.id.geofence_delete_btn);
        }
    }
}
