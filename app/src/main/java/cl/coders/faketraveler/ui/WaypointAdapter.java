package cl.coders.faketraveler.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.RouteWaypointEntity;

/**
 * RecyclerView adapter for the waypoint list inside {@link RouteEditorBottomSheet} (Module 2).
 * Backed directly by the editor's mutable list so drag-reorder ({@link #onMove}) and delete mutate
 * in place; the editor recomputes sequence on save.
 */
public class WaypointAdapter extends RecyclerView.Adapter<WaypointAdapter.VH> {

    public interface Listener {
        void onDelete(int position);
        void onDragStart(@NonNull RecyclerView.ViewHolder vh);
    }

    @NonNull private final List<RouteWaypointEntity> items;
    @NonNull private final Listener listener;

    public WaypointAdapter(@NonNull List<RouteWaypointEntity> items, @NonNull Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refresh() { notifyDataSetChanged(); }

    public void onMove(int from, int to) {
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_waypoint, parent, false);
        return new VH(v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        RouteWaypointEntity w = items.get(pos);
        h.seq.setText(String.valueOf(pos + 1));
        h.coords.setText(String.format(Locale.US, "%.5f, %.5f", w.lat, w.lng));
        h.detail.setText(h.itemView.getContext().getString(
                R.string.RouteEditor_Waypoint_Detail, w.speedKmh, w.dwellMs));
        h.delete.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onDelete(p);
        });
        h.drag.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onDragStart(h);
            return false;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView seq;
        final TextView coords;
        final TextView detail;
        final View delete;
        final View drag;
        VH(@NonNull View v) {
            super(v);
            seq = v.findViewById(R.id.waypoint_seq);
            coords = v.findViewById(R.id.waypoint_coords);
            detail = v.findViewById(R.id.waypoint_detail);
            delete = v.findViewById(R.id.waypoint_delete_btn);
            drag = v.findViewById(R.id.waypoint_drag_handle);
        }
    }
}
