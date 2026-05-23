package cl.coders.faketraveler.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BookmarkDaoTest {

    @Rule public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private AppDatabase db;
    private BookmarkDao dao;

    @Before public void setUp() {
        db = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.bookmarkDao();
    }

    @After public void tearDown() { db.close(); }

    @Test public void insert_then_get_returns_entry() {
        BookmarkEntity e = new BookmarkEntity();
        e.name = "Home";
        e.lat = 12.34;
        e.lng = 56.78;
        e.zoom = 15;
        e.createdAt = 1_000_000L;

        long id = dao.insert(e);
        assertTrue(id > 0);

        List<BookmarkEntity> all = dao.getAllSync();
        assertEquals(1, all.size());
        assertEquals("Home", all.get(0).name);
        assertEquals(12.34, all.get(0).lat, 1e-9);
    }

    @Test public void delete_removes_entry() {
        BookmarkEntity e = new BookmarkEntity();
        e.name = "ToDelete";
        e.lat = 0;
        e.lng = 0;
        e.zoom = 12;
        e.createdAt = 1L;
        dao.insert(e);
        BookmarkEntity stored = dao.getAllSync().get(0);
        dao.delete(stored);
        assertEquals(0, dao.getAllSync().size());
    }

    @Test public void ordered_by_createdAt_desc() {
        BookmarkEntity a = new BookmarkEntity();
        a.name = "A"; a.createdAt = 100L;
        BookmarkEntity b = new BookmarkEntity();
        b.name = "B"; b.createdAt = 200L;
        dao.insert(a); dao.insert(b);
        assertEquals("B", dao.getAllSync().get(0).name);
        assertEquals("A", dao.getAllSync().get(1).name);
    }
}
