package cl.coders.faketraveler.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cl.coders.faketraveler.R;
import cl.coders.faketraveler.db.AppDatabase;
import cl.coders.faketraveler.db.BookmarkEntity;

/**
 * Bottom sheet that lists saved bookmarks and exposes swipe-to-delete + long-press-to-rename.
 * Tap on a row picks the bookmark and notifies the host via {@link Host}.
 *
 * <p>Database writes happen on a short-lived background thread — Room throws on the main
 * thread. Reads are observed through the DAO's {@link androidx.lifecycle.LiveData}.
 */
public class BookmarksBottomSheet extends BottomSheetDialogFragment implements BookmarkAdapter.Listener {

    /**
     * Lifecycle-safe host interface. Resolved from the Activity in {@link #onAttach}.
     * Survives configuration changes (FragmentManager re-creation) without leaking callbacks.
     */
    public interface Host {
        void onBookmarkSelected(@NonNull BookmarkEntity fav);
        void onAddCurrentRequested();
    }

    @Nullable private Host host;
    @Nullable private BookmarkAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Host) {
            host = (Host) context;
        } else {
            throw new IllegalStateException(context.getClass().getSimpleName()
                    + " must implement BookmarksBottomSheet.Host");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_bookmarks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final View empty = view.findViewById(R.id.bookmark_empty);
        final RecyclerView list = view.findViewById(R.id.bookmark_list);
        final View addCurrent = view.findViewById(R.id.bookmark_add_current_btn);
        if (addCurrent != null) {
            addCurrent.setOnClickListener(v -> {
                if (host != null) host.onAddCurrentRequested();
            });
        }

        adapter = new BookmarkAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        AppDatabase.get(requireContext()).bookmarkDao().getAll().observe(
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
                final BookmarkEntity fav = adapter.itemAt(pos);
                if (fav == null) return;
                final Context appCtx = requireContext().getApplicationContext();
                runDb(() -> AppDatabase.get(appCtx).bookmarkDao().delete(fav));
            }
        }).attachToRecyclerView(list);
    }

    @Override public void onTap(@NonNull BookmarkEntity fav) {
        if (host != null) host.onBookmarkSelected(fav);
        dismiss();
    }

    @Override public void onLongPress(@NonNull BookmarkEntity fav) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(fav.name);
        final Context appCtx = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.Bookmark_Rename_Title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    fav.name = newName;
                    runDb(() -> AppDatabase.get(appCtx).bookmarkDao().update(fav));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Room enforces no-main-thread writes; the sheet only ever fires single mutations.
     *  Bg thread holds only application context (captured by caller) so it cannot leak the
     *  fragment past destruction. */
    private static void runDb(@NonNull Runnable r) {
        final Thread t = new Thread(r, "BookmarksIO");
        t.setDaemon(true);
        t.start();
    }
}
