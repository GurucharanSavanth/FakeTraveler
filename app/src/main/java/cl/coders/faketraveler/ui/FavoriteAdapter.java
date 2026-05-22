package cl.coders.faketraveler.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.FavoriteEntity;

/**
 * RecyclerView adapter for the favorites bottom sheet.
 *
 * <p>Holds a private list — it does NOT query the database. The hosting sheet observes
 * {@link cl.coders.faketraveler.db.FavoriteDao#getAll()} and pushes refreshed snapshots in
 * (V35); this keeps the data layer out of the view layer.
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.VH> {

    public interface Listener {
        void onTap(@NonNull FavoriteEntity fav);
        void onLongPress(@NonNull FavoriteEntity fav);
    }

    @NonNull private final List<FavoriteEntity> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public FavoriteAdapter(@NonNull Listener listener) { this.listener = listener; }

    @android.annotation.SuppressLint("NotifyDataSetChanged")  // small list; full refresh is intentional
    public void submit(@NonNull List<FavoriteEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @androidx.annotation.Nullable
    public FavoriteEntity itemAt(int pos) {
        if (pos < 0 || pos >= items.size()) return null;
        return items.get(pos);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        FavoriteEntity f = items.get(pos);
        h.name.setText(f.name);
        h.coords.setText(String.format(Locale.US, "%.5f, %.5f", f.lat, f.lng));
        h.itemView.setOnClickListener(v -> listener.onTap(f));
        h.itemView.setOnLongClickListener(v -> { listener.onLongPress(f); return true; });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView coords;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.fav_name);
            coords = v.findViewById(R.id.fav_coords);
        }
    }
}
