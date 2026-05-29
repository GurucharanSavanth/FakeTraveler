package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.GeoFenceEventEntity;

/** RecyclerView adapter for {@link GeoFenceEventBottomSheet} (Module 3). */
public class GeoFenceEventAdapter extends RecyclerView.Adapter<GeoFenceEventAdapter.VH> {

    @NonNull private final List<GeoFenceEventEntity> items = new ArrayList<>();
    @NonNull private final Map<Long, String> fenceNames = new HashMap<>();

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<GeoFenceEventEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setFenceNames(@NonNull Map<Long, String> names) {
        fenceNames.clear();
        fenceNames.putAll(names);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_geofence_event, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        GeoFenceEventEntity e = items.get(pos);
        final String name = fenceNames.get(e.geofenceId);
        h.title.setText((name != null ? name : "Fence #" + e.geofenceId) + " · " + typeLabel(h, e.eventType));
        h.meta.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(new Date(e.timestamp)));
        h.coords.setText(String.format(Locale.US, "%.5f, %.5f", e.triggeredLat, e.triggeredLng));
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    private static String typeLabel(@NonNull VH h, int type) {
        final int res = type == 0 ? R.string.GeoFence_Event_Entry
                : type == 1 ? R.string.GeoFence_Event_Exit
                : R.string.GeoFence_Event_Dwell;
        return h.itemView.getContext().getString(res);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView coords;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.geofence_event_title);
            meta = v.findViewById(R.id.geofence_event_meta);
            coords = v.findViewById(R.id.geofence_event_coords);
        }
    }
}
