package cl.coders.faketraveler.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.SavedRouteEntity;

/** RecyclerView adapter for {@link cl.coders.faketraveler.RouteLabActivity} (Module 2). */
public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.VH> {

    public interface Listener {
        void onPlay(@NonNull SavedRouteEntity route);
        void onLongPress(@NonNull SavedRouteEntity route);
    }

    @NonNull private final List<SavedRouteEntity> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public RouteAdapter(@NonNull Listener listener) { this.listener = listener; }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<SavedRouteEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SavedRouteEntity rt = items.get(pos);
        h.name.setText(rt.name);
        h.meta.setText(h.itemView.getContext().getString(R.string.RouteLab_Item_Summary,
                rt.pointCount, rt.totalDistanceMeters, formatDuration(rt.estimatedDurationSeconds)));
        h.play.setOnClickListener(v -> listener.onPlay(rt));
        h.itemView.setOnLongClickListener(v -> { listener.onLongPress(rt); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    private static String formatDuration(int sec) {
        if (sec <= 0) return "0s";
        int m = sec / 60, s = sec % 60;
        return m > 0 ? String.format(Locale.US, "%dm %ds", m, s)
                     : String.format(Locale.US, "%ds", s);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final MaterialButton play;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.route_name);
            meta = v.findViewById(R.id.route_meta);
            play = v.findViewById(R.id.route_play_btn);
        }
    }
}
