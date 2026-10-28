package il.zimreader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** מקור אמת יחיד להגדרות, סימניות והיסטוריה (SharedPreferences). */
public final class Prefs {
    private Prefs() {}

    public static final String BOOKMARKS = "bookmarks";
    public static final String HISTORY = "history";
    private static final int HISTORY_MAX = 200;

    public static final class Item {
        public String book;
        public String path;
        public String title;
        public long ts;
        /** מיקום גלילה (פיקסלי CSS) - לכרטיסיות. */
        public int y;
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("zr", Context.MODE_PRIVATE);
    }

    /** 0 = לפי המערכת, 1 = בהיר, 2 = כהה */
    public static int theme(Context c) {
        return sp(c).getInt("theme", 0);
    }

    public static void setTheme(Context c, int v) {
        sp(c).edit().putInt("theme", v).apply();
    }

    public static int zoom(Context c) {
        return sp(c).getInt("zoom", 100);
    }

    public static void setZoom(Context c, int v) {
        sp(c).edit().putInt("zoom", v).apply();
    }

    public static synchronized List<Item> tabs(Context c) {
        return load(c, "tabs");
    }

    public static synchronized void saveTabs(Context c, List<Item> t) {
        save(c, "tabs", t);
    }

    /** בדיקות: מכריח מצב תאימות CSS ל-WebView ישן (משתני CSS) גם במכשיר חדש. */
    public static boolean forceLegacyCss(Context c) {
        return sp(c).getBoolean("legacyCss", false);
    }

    /** שולי עמוד: 0 = מלא, 1 = צרים (ברירת מחדל), 2 = רחבים. */
    public static int margin(Context c) {
        return sp(c).getInt("margin", 1);
    }

    public static void setMargin(Context c, int v) {
        sp(c).edit().putInt("margin", v).apply();
    }

    public static synchronized List<Item> load(Context c, String key) {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.book = o.getString("b");
                it.path = o.getString("p");
                it.title = o.optString("t", it.path);
                it.ts = o.optLong("ts");
                it.y = o.optInt("y");
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, String key, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("b", it.book);
                o.put("p", it.path);
                o.put("t", it.title);
                o.put("ts", it.ts);
                o.put("y", it.y);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString(key, a.toString()).apply();
    }

    private static int indexOf(List<Item> l, String book, String path) {
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).book.equals(book) && l.get(i).path.equals(path)) return i;
        }
        return -1;
    }

    public static synchronized void addHistory(Context c, String book, String path, String title) {
        List<Item> l = load(c, HISTORY);
        int i = indexOf(l, book, path);
        if (i >= 0) l.remove(i);
        Item it = new Item();
        it.book = book;
        it.path = path;
        it.title = title;
        it.ts = System.currentTimeMillis();
        l.add(0, it);
        while (l.size() > HISTORY_MAX) l.remove(l.size() - 1);
        save(c, HISTORY, l);
    }

    public static synchronized boolean isBookmarked(Context c, String book, String path) {
        return indexOf(load(c, BOOKMARKS), book, path) >= 0;
    }

    /** מחזיר true אם אחרי הפעולה הערך מסומן. */
    public static synchronized boolean toggleBookmark(Context c, String book, String path, String title) {
        List<Item> l = load(c, BOOKMARKS);
        int i = indexOf(l, book, path);
        if (i >= 0) {
            l.remove(i);
            save(c, BOOKMARKS, l);
            return false;
        }
        Item it = new Item();
        it.book = book;
        it.path = path;
        it.title = title;
        it.ts = System.currentTimeMillis();
        l.add(0, it);
        save(c, BOOKMARKS, l);
        return true;
    }

    public static synchronized void remove(Context c, String key, int index) {
        List<Item> l = load(c, key);
        if (index >= 0 && index < l.size()) {
            l.remove(index);
            save(c, key, l);
        }
    }

    public static synchronized void clear(Context c, String key) {
        sp(c).edit().remove(key).apply();
    }

    // ---------- ספרייה שמורה (כדי לא לסרוק מחדש בכל כניסה) ----------

    public static synchronized void saveLibrary(Context c, List<Library.Book> books) {
        JSONArray a = new JSONArray();
        try {
            for (Library.Book b : books) {
                JSONObject o = new JSONObject();
                o.put("p", b.path);
                o.put("n", b.name);
                o.put("s", b.size);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString("library", a.toString()).apply();
    }

    public static synchronized List<Library.Book> loadLibrary(Context c) {
        List<Library.Book> out = new ArrayList<Library.Book>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("library", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Library.Book b = new Library.Book();
                b.path = o.getString("p");
                b.name = o.optString("n", b.path);
                b.size = o.optLong("s");
                out.add(b);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------- הספר האחרון והמיקום האחרון בכל ספר ----------

    public static String lastBook(Context c) {
        return sp(c).getString("lastBook", null);
    }

    public static void setLastBook(Context c, String path) {
        sp(c).edit().putString("lastBook", path).apply();
    }

    public static String position(Context c, String book) {
        return sp(c).getString("pos:" + book, null);
    }

    public static void setPosition(Context c, String book, String path) {
        sp(c).edit().putString("pos:" + book, path).apply();
    }
}
