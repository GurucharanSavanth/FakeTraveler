package cl.coders.faketraveler.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.FavoriteEntity;

/**
 * Bottom sheet that lists saved favorites and exposes swipe-to-delete + long-press-to-rename.
 * Tap on a row picks the favorite and notifies the host via {@link OnFavoritePicked}.
 *
 * <p>Database writes happen on a short-lived background thread — Room throws on the main
 * thread. Reads are observed through the DAO's {@link androidx.lifecycle.LiveData}.
 */
public class FavoritesBottomSheet extends BottomSheetDialogFragment implements FavoriteAdapter.Listener {

    public interface OnFavoritePicked {
        void onPicked(@NonNull FavoriteEntity fav);
    }

    @Nullable private OnFavoritePicked callback;
    @Nullable private FavoriteAdapter adapter;

    public void setCallback(@Nullable OnFavoritePicked cb) { this.callback = cb; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final TextView empty = view.findViewById(R.id.fav_empty);
        final RecyclerView list = view.findViewById(R.id.fav_list);

        adapter = new FavoriteAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        AppDatabase.get(requireContext()).favoriteDao().getAll().observe(
                getViewLifecycleOwner(),
                favs -> {
                    adapter.submit(favs);
                    empty.setVisibility(favs.isEmpty() ? View.VISIBLE : View.GONE);
                });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.START | ItemTouchHelper.END) {

            @Override public boolean onMove(@NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder a,
                                            @NonNull RecyclerView.ViewHolder b) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                if (adapter == null) return;
                final int pos = vh.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                final FavoriteEntity fav = adapter.itemAt(pos);
                if (fav == null) return;
                final Context appCtx = requireContext().getApplicationContext();
                runDb(() -> AppDatabase.get(appCtx).favoriteDao().delete(fav));
            }
        }).attachToRecyclerView(list);
    }

    @Override public void onTap(@NonNull FavoriteEntity fav) {
        if (callback != null) callback.onPicked(fav);
        dismiss();
    }

    @Override public void onLongPress(@NonNull FavoriteEntity fav) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(fav.name);
        final Context appCtx = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.Favorites_Rename_Title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    fav.name = newName;
                    runDb(() -> AppDatabase.get(appCtx).favoriteDao().update(fav));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Room enforces no-main-thread writes; the sheet only ever fires single mutations.
     *  Bg thread holds only application context (captured by caller) so it cannot leak the
     *  fragment past destruction. */
    private static void runDb(@NonNull Runnable r) {
        final Thread t = new Thread(r, "FavoritesIO");
        t.setDaemon(true);
        t.start();
    }
}
