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
import java.util.List;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.ExifCleanedFileEntity;

/** RecyclerView adapter for {@link ExifCleanerBottomSheet} (Module 6). */
public class ExifCleanedAdapter extends RecyclerView.Adapter<ExifCleanedAdapter.VH> {

    @NonNull private final List<ExifCleanedFileEntity> items = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void submit(@NonNull List<ExifCleanedFileEntity> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exif_cleaned, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ExifCleanedFileEntity f = items.get(pos);
        h.name.setText(fileName(f.filePath));
        h.coords.setText(h.itemView.getContext().getString(
                R.string.ExifCleaner_Item_Coords, f.originalLat, f.originalLng));
        h.time.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(f.cleanedAt)));
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    private static String fileName(@NonNull String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView coords;
        final TextView time;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.exif_item_name);
            coords = v.findViewById(R.id.exif_item_coords);
            time = v.findViewById(R.id.exif_item_time);
        }
    }
}
