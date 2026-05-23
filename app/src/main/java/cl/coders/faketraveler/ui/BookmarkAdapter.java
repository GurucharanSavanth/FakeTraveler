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
import cl.coders.faketraveler.db.BookmarkEntity;

/**
 * RecyclerView adapter for the bookmarks bottom sheet.
 *
 * <p>Holds a private list — it does NOT query the database. The hosting sheet observes
 * {@link cl.coders.faketraveler.db.BookmarkDao#getAll()} and pushes refreshed snapshots in
 * (V35); this keeps the data layer out of the view layer.
 */
public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.VH> {

    public interface Listener {
        void onTap(@NonNull BookmarkEntity fav);
        void onLongPress(@NonNull BookmarkEntity fav);
    }

    @NonNull private final List<BookmarkEntity> items = new ArrayList<>();
    @NonNull private final Listener listener;

    public BookmarkAdapter(@NonNull Listener listener) { this.listener = listener; }

    @android.annotation.SuppressLint("NotifyDataSetChanged")  // small list; full refresh is intentional
    public void submit(@NonNull List<BookmarkEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @androidx.annotation.Nullable
    public BookmarkEntity itemAt(int pos) {
        if (pos < 0 || pos >= items.size()) return null;
        return items.get(pos);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        BookmarkEntity f = items.get(pos);
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
            name = v.findViewById(R.id.bookmark_name);
            coords = v.findViewById(R.id.bookmark_coords);
        }
    }
}
