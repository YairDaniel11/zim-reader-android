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

    /** שלבי גודל הגופן (באחוזים). */
    public static final int[] ZOOMS = {60, 80, 100, 120, 150, 180, 220};

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

    /** הסתרת הסרגלים בגלילה למטה (ובחזרה בגלילה למעלה). */
    public static boolean hideBars(Context c) {
        return sp(c).getBoolean("hideBars", false);
    }

    public static void setHideBars(Context c, boolean v) {
        sp(c).edit().putBoolean("hideBars", v).apply();
    }

    /** פתיחה בהפעלה במקום האחרון שנקרא (ולא ברשימת הספרים). */
    public static boolean resumeLast(Context c) {
        return sp(c).getBoolean("resumeLast", true);
    }

    public static void setResumeLast(Context c, boolean v) {
        sp(c).edit().putBoolean("resumeLast", v).apply();
    }

    /** רמזי מקשים: 0 = אוטומטי (רק במכשיר עם מקשים), 1 = תמיד, 2 = אף פעם. */
    public static int keyHints(Context c) {
        return sp(c).getInt("keyHints", 0);
    }

    public static void setKeyHints(Context c, int v) {
        sp(c).edit().putInt("keyHints", v).apply();
    }

    /** מהירות הגלילה האוטומטית: 1..10 (ראו SCROLL_DP_PER_SEC). */
    public static final int[] SCROLL_DP_PER_SEC = {8, 15, 25, 40, 60, 90, 130, 180, 250, 350};

    public static int autoScroll(Context c) {
        return Math.max(1, Math.min(SCROLL_DP_PER_SEC.length, sp(c).getInt("autoScroll", 4)));
    }

    public static void setAutoScroll(Context c, int v) {
        sp(c).edit().putInt("autoScroll", Math.max(1, Math.min(SCROLL_DP_PER_SEC.length, v))).apply();
    }

    // ---------- חיפושים אחרונים ----------

    private static final int SEARCHES_MAX = 10;

    public static synchronized List<String> searches(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("searches", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void saveSearches(Context c, List<String> l) {
        sp(c).edit().putString("searches", new JSONArray(l).toString()).apply();
    }

    public static synchronized void addSearch(Context c, String q) {
        q = q == null ? "" : q.trim();
        if (q.length() == 0) return;
        List<String> l = searches(c);
        l.remove(q);
        l.add(0, q);
        while (l.size() > SEARCHES_MAX) l.remove(l.size() - 1);
        saveSearches(c, l);
    }

    public static synchronized void removeSearch(Context c, String q) {
        List<String> l = searches(c);
        if (l.remove(q)) saveSearches(c, l);
    }

    /** גופן הדף: 0 = כמו בדף, 1 = סריף, 2 = סאנס, 3 = מונוספייס. */
    public static int font(Context c) {
        return sp(c).getInt("font", 0);
    }

    public static void setFont(Context c, int v) {
        sp(c).edit().putInt("font", v).apply();
    }

    /** מרווח שורות: 0 = כמו בדף, 1 = קטן, 2 = בינוני, 3 = גדול. */
    public static int lineSpacing(Context c) {
        return sp(c).getInt("lineSpacing", 0);
    }

    public static void setLineSpacing(Context c, int v) {
        sp(c).edit().putInt("lineSpacing", v).apply();
    }

    /** מסך מלא: גם שורת המערכת מוסתרת, והסרגלים נעלמים בגלילה. */
    public static boolean fullscreen(Context c) {
        return sp(c).getBoolean("fullscreen", false);
    }

    public static void setFullscreen(Context c, boolean v) {
        sp(c).edit().putBoolean("fullscreen", v).apply();
    }

    public static void setForceLegacyCss(Context c, boolean v) {
        sp(c).edit().putBoolean("legacyCss", v).apply();
    }

    /** האם להציג רכיבי ממשק שקשורים למקשים (שורת רמזים, עזרת מקשים). */
    public static boolean showKeyUi(Context c) {
        int m = keyHints(c);
        if (m == 1) return true;
        if (m == 2) return false;
        android.content.res.Configuration cfg = c.getResources().getConfiguration();
        return cfg.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
                || cfg.navigation == android.content.res.Configuration.NAVIGATION_DPAD
                || cfg.navigation == android.content.res.Configuration.NAVIGATION_TRACKBALL
                || cfg.navigation == android.content.res.Configuration.NAVIGATION_WHEEL;
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
