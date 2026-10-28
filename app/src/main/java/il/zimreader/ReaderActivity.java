package il.zimreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import il.zimreader.zim.FullText;
import il.zimreader.zim.ZimFile;

/** קורא המאמרים: WebView שמקבל את התוכן ישירות מקובץ ה-ZIM. */
public class ReaderActivity extends Activity {
    public static final String EX_BOOK = "book";
    public static final String EX_PATH = "path";
    public static final String EX_FIND = "find";

    private static final String BASE = "https://zim.local/";
    private static final int[] ZOOMS = {60, 80, 100, 120, 150, 180, 220};

    private ZimWebView web;
    private TextView titleView;
    private ImageButton btnBookmark;
    private ZimFile zim;
    private String bookPath;
    private String bookId;
    private String curPath;
    private String pendingFind;
    private boolean clearHist;
    private boolean legacyCss;
    private il.zimreader.zim.CssCompat cssCompat = new il.zimreader.zim.CssCompat();

    @Override
    protected void onCreate(Bundle state) {
        ThemeUtil.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_reader);
        ThemeUtil.forceRtl(this);

        Library.init(this);
        titleView = (TextView) findViewById(R.id.title);
        btnBookmark = (ImageButton) findViewById(R.id.btnBookmark);
        web = (ZimWebView) findViewById(R.id.web);
        ((TextView) findViewById(R.id.hint)).setText(R.string.hint_reader);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(Prefs.zoom(this));
        web.setBackgroundColor(ThemeUtil.isDark(this) ? 0xFF212121 : 0xFFFFFFFF);
        web.setWebViewClient(new Client());
        web.setPreImeHandler(new ZimWebView.PreImeHandler() {
            @Override
            public boolean handle(KeyEvent e) {
                return handleKey(e);
            }
        });
        legacyCss = Prefs.forceLegacyCss(this)
                || il.zimreader.zim.CssCompat.isLegacyWebView(s.getUserAgentString(), Build.VERSION.SDK_INT);

        click(R.id.btnHome, new Runnable() { public void run() { goHome(); } });
        click(R.id.btnSearch, new Runnable() { public void run() { openSearch(); } });
        click(R.id.btnBookmark, new Runnable() { public void run() { toggleBookmark(); } });
        click(R.id.btnLibrary, new Runnable() { public void run() { showBooks(); } });
        click(R.id.btnMenu, new Runnable() { public void run() { showMenu(); } });

        String restore = state != null ? state.getString(EX_PATH) : null;
        handleIntent(getIntent(), restore);
        web.setFocusableInTouchMode(true);
        web.requestFocus();
        web.post(new Runnable() {
            @Override
            public void run() {
                if (!toolbarHasFocus()) web.requestFocus();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !toolbarHasFocus()) web.requestFocus();
    }

    private final android.os.Handler hintHandler = new android.os.Handler();

    /** חיווי רגעי בשורת התחתית: איזה מקש נלחץ ואיזו פעולה הופעלה (עוזר לאבחן מכשירים). */
    private void showKeyHint(KeyEvent ev, int action) {
        final TextView hint = (TextView) findViewById(R.id.hint);
        hint.setText(Keys.name(action));
        hintHandler.removeCallbacksAndMessages(null);
        hintHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hint.setText(R.string.hint_reader);
            }
        }, 1800);
    }

    private void click(int id, final Runnable r) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (curPath != null) out.putString(EX_PATH, curPath);
    }

    private void handleIntent(Intent intent, String restorePath) {
        String book = intent.getStringExtra(EX_BOOK);
        if (book == null) {
            finish();
            return;
        }
        pendingFind = intent.getStringExtra(EX_FIND);
        openBook(book, restorePath != null ? restorePath : intent.getStringExtra(EX_PATH));
    }

    /** פותח ספר: במקום שנבחר, אחרת במקום האחרון שנקרא בו, אחרת בדף הראשי. */
    private void openBook(String book, String path) {
        try {
            if (!book.equals(bookPath)) {
                boolean switching = bookPath != null;
                zim = Library.open(book);
                bookPath = book;
                bookId = Library.idOf(book);
                // היסטוריית הדפים של הספר הקודם לא רלוונטית לספר החדש
                clearHist = switching;
                cssCompat = new il.zimreader.zim.CssCompat();
            }
            Prefs.setLastBook(this, book);
            if (path == null) path = Prefs.position(this, book);
            if (path == null || zim.findByFullPath(path) == null) {
                ZimFile.Entry m = zim.mainEntry();
                if (m == null) throw new java.io.IOException("no main page");
                path = zim.fullPathOf(m);
            }
            load(path);
        } catch (Throwable e) {
            Toast.makeText(this, getString(R.string.open_failed) + ": " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            if (zim == null) finish();
        }
    }

    private void load(String fullPath) {
        web.loadUrl(pageUrl(fullPath));
    }

    private String pageUrl(String fullPath) {
        return BASE + bookId + "/" + Uri.encode(fullPath, "/");
    }

    private void goHome() {
        try {
            ZimFile.Entry m = zim.mainEntry();
            if (m != null) load(zim.fullPathOf(m));
        } catch (Throwable ignored) {
        }
    }

    private void openSearch() {
        startActivity(new Intent(this, SearchActivity.class).putExtra(EX_BOOK, bookPath));
    }

    private String pageTitle() {
        String t = titleView.getText().toString();
        return t.length() > 0 ? t : (curPath != null ? curPath : "");
    }

    private void toggleBookmark() {
        if (curPath == null) return;
        boolean on = Prefs.toggleBookmark(this, bookPath, curPath, pageTitle());
        updateBookmarkIcon();
        Toast.makeText(this, on ? "נוספה סימנייה" : "הסימנייה הוסרה", Toast.LENGTH_SHORT).show();
    }

    private void updateBookmarkIcon() {
        boolean on = curPath != null && Prefs.isBookmarked(this, bookPath, curPath);
        btnBookmark.setColorFilter(on ? 0xFFFFD54F : 0xFFFFFFFF);
    }

    // ---------- בחירת ספר ----------

    private void showBooks() {
        final List<Library.Book> books = Library.list();
        if (books.isEmpty()) {
            finish();
            return;
        }
        String[] names = new String[books.size() + 1];
        int cur = 0;
        for (int i = 0; i < books.size(); i++) {
            boolean isCur = books.get(i).path.equals(bookPath);
            names[i] = (isCur ? "✓ " : "") + books.get(i).name;
            if (isCur) cur = i;
        }
        names[books.size()] = "« חזרה לרשימת הספרים";
        new AlertDialog.Builder(this)
                .setTitle("ספרים")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which >= books.size()) {
                            finish();
                        } else if (!books.get(which).path.equals(bookPath)) {
                            openBook(books.get(which).path, null);
                        }
                    }
                })
                .show();
    }

    // ---------- קישורים וכותרות בדף (בלי JavaScript) ----------

    private String currentHtml() {
        try {
            ZimFile.Entry e = zim.resolve(zim.findByFullPath(curPath));
            return e == null ? null : new String(zim.content(e), ZimFile.UTF8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final Pattern LINK = Pattern.compile(
            "(?is)<a\\s[^>]*?href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')[^>]*>(.*?)</a>");
    private static final Pattern HEADING = Pattern.compile("(?is)<h([1-6])\\b([^>]*)>(.*?)</h\\1>");
    private static final Pattern ID = Pattern.compile("(?is)\\bid\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private static String grp(Matcher m, int a, int b) {
        return m.group(a) != null ? m.group(a) : m.group(b);
    }

    private void showLinks() {
        String html = currentHtml();
        if (html == null || curPath == null) return;
        final Map<String, String> found = new LinkedHashMap<String, String>();
        URI base;
        try {
            base = new URI(pageUrl(curPath));
        } catch (Exception e) {
            return;
        }
        Matcher m = LINK.matcher(html);
        while (m.find() && found.size() < 1000) {
            String href = grp(m, 1, 2).trim();
            if (href.length() == 0 || href.startsWith("#") || href.toLowerCase().startsWith("javascript:")) continue;
            try {
                String abs = base.resolve(href.replace(" ", "%20")).toString();
                if (!abs.startsWith(BASE)) continue;
                if (found.containsKey(abs)) continue;
                String label = FullText.htmlToText(m.group(3));
                if (label.length() == 0) label = Uri.decode(href);
                found.put(abs, label.replace('_', ' '));
            } catch (Exception ignored) {
            }
        }
        if (found.isEmpty()) {
            Toast.makeText(this, "אין קישורים בדף", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> urls = new ArrayList<String>(found.keySet());
        String[] labels = found.values().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("קישורים בדף (" + labels.length + ")")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        web.loadUrl(urls.get(which));
                    }
                })
                .show();
    }

    private static String jsString(String x) {
        return "'" + x.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ") + "'";
    }

    private static String unescapeHtml(String x) {
        return x.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private void showToc() {
        String html = currentHtml();
        if (html == null || curPath == null) return;
        final List<String> ids = new ArrayList<String>();
        final List<Integer> positions = new ArrayList<Integer>();
        List<String> labels = new ArrayList<String>();
        Matcher m = HEADING.matcher(html);
        int n = -1;
        while (m.find() && labels.size() < 500) {
            n++;
            String label = FullText.htmlToText(m.group(3));
            if (label.length() == 0) continue;
            Matcher im = ID.matcher(m.group(2));
            String id = null;
            if (im.find()) id = grp(im, 1, 2);
            else {
                Matcher inner = ID.matcher(m.group(3));
                if (inner.find()) id = grp(inner, 1, 2);
            }
            int level = m.group(1).charAt(0) - '1';
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < level; i++) sb.append("  ");
            labels.add(sb + label);
            ids.add(id == null ? "" : unescapeHtml(id));
            positions.add(n);
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "אין כותרות בדף", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("כותרות בדף")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        final String id = ids.get(which);
                        // קודם לפי מזהה, ואם אין - לפי מספר הכותרת בדף (כולל כותרות שדולגו בתצוגה)
                        scrollToElement("(" + (id.length() > 0 ? "document.getElementById(" + jsString(id) + ")" : "null")
                                + "||document.querySelectorAll('h1,h2,h3,h4,h5,h6')[" + positions.get(which) + "])");
                    }
                })
                .show();
    }

    // ---------- כרטיסיות ----------

    private int pendingScrollY;
    private int lastY;                    // מיקום הגלילה האחרון הידוע בעמוד הנוכחי
    private boolean tabSwitching;         // מעבר יזום בין כרטיסיות (לא ליצור כרטיסייה חדשה)
    private boolean tabInitialized;
    private String tabBook, tabPath;      // העמוד שהכרטיסייה הנוכחית מציגה
    private final android.os.Handler pollHandler = new android.os.Handler();
    private final Runnable scrollPoller = new Runnable() {
        @Override
        public void run() {
            withScrollY(new ValueCallback<Integer>() {
                @Override
                public void onReceiveValue(Integer y) {
                    lastY = y;
                }
            });
            pollHandler.postDelayed(this, 1000);
        }
    };

    /** מכניס עמוד לראש הרשימה (מסיר כפילות), עד 10 כרטיסיות. מחזיר את הפריט. */
    private Prefs.Item pushTab(List<Prefs.Item> tabs, String book, String path) {
        Prefs.Item found = null;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).book.equals(book) && tabs.get(i).path.equals(path)) {
                found = tabs.remove(i);
                break;
            }
        }
        if (found == null) {
            found = new Prefs.Item();
            found.book = book;
            found.path = path;
            found.title = path;
            found.y = 0;
        }
        found.ts = System.currentTimeMillis();
        tabs.add(0, found);
        while (tabs.size() > 10) tabs.remove(tabs.size() - 1);
        tabBook = book;
        tabPath = path;
        return found;
    }

    /** נקרא בתחילת טעינת עמוד: שומר את העמוד הקודם (עם הגלילה שלו) ופותח כרטיסייה לעמוד החדש. */
    private void onNavigate(String p) {
        if (tabSwitching) return;
        List<Prefs.Item> tabs = Prefs.tabs(this);
        if (!tabInitialized) {
            tabInitialized = true;
            Prefs.Item it = pushTab(tabs, bookPath, p);
            pendingScrollY = it.y;           // חזרה לאותו עמוד: ממשיכים מהמקום המדויק
            Prefs.saveTabs(this, tabs);
            return;
        }
        if (p.equals(tabPath) && bookPath.equals(tabBook)) return;   // אותו עמוד (למשל קפיצה לעוגן)
        if (!tabs.isEmpty()) tabs.get(0).y = lastY;
        pushTab(tabs, bookPath, p).y = 0;
        Prefs.saveTabs(this, tabs);
        lastY = 0;
    }

    /** מעדכן את הכרטיסייה הנוכחית (הראשונה ברשימה) בעמוד ובמיקום הגלילה. */
    private void syncCurrentTab(int y) {
        if (bookPath == null || curPath == null) return;
        List<Prefs.Item> tabs = Prefs.tabs(this);
        Prefs.Item it = tabs.isEmpty() ? new Prefs.Item() : tabs.get(0);
        it.book = bookPath;
        it.path = curPath;
        it.title = pageTitle();
        it.ts = System.currentTimeMillis();
        if (y >= 0) it.y = y;
        if (tabs.isEmpty()) tabs.add(it);
        Prefs.saveTabs(this, tabs);
    }

    /** מיקום הגלילה האמיתי של ה-WebView (פיקסלים אמיתיים). */
    private void withScrollY(final ValueCallback<Integer> cb) {
        cb.onReceiveValue(web.getScrollY());
    }

    private float scale() {
        float sc = web.getScale();
        return sc > 0 ? sc : getResources().getDisplayMetrics().density;
    }

    /** גולל נייטיבית לאלמנט שנמצא ע"י הביטוי; המיקום מחושב מהפריסה ולא ממצב הגלילה של ה-JavaScript. */
    private void scrollToElement(String expr) {
        String code = "(function(){var e=" + expr + ";if(!e)return -1;var y=0,n=e;"
                + "while(n){y+=n.offsetTop||0;n=n.offsetParent;}return y;})()";
        js(code, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                try {
                    double y = Double.parseDouble(v.replace("\"", ""));
                    if (y >= 0) web.scrollTo(0, (int) (y * scale()));
                } catch (Exception ignored) {
                }
            }
        });
    }

    /** אחרי גלילה שבוצעה ע"י הדף עצמו (חיפוש, עוגן): מיישר את הגלילה הנייטיבית למצב האמיתי. */
    private void syncNative() {
        js("(window.pageYOffset||document.documentElement.scrollTop||document.body.scrollTop||0)",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        try {
                            double y = Double.parseDouble(v.replace("\"", ""));
                            if (y > 0) web.scrollTo(0, (int) (y * scale()));
                        } catch (Exception ignored) {
                        }
                    }
                });
    }

    private void showTabs() {
        withScrollY(new ValueCallback<Integer>() {
            @Override
            public void onReceiveValue(Integer y) {
                syncCurrentTab(y);
                showTabsDialog();
            }
        });
    }

    private void showTabsDialog() {
        final List<Prefs.Item> tabs = Prefs.tabs(this);
        final int n = tabs.size();
        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            Prefs.Item t = tabs.get(i);
            labels.add((i == 0 ? "● " : (i + 1) + ". ") + t.title.replace('_', ' ') + "  ·  " + Library.nameOf(t.book));
        }
        if (n > 1) labels.add("✕ סגור את הכרטיסייה הנוכחית");
        new AlertDialog.Builder(this)
                .setTitle("כרטיסיות (" + n + "/10)")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < n) {
                            if (which > 0) switchToTab(which);
                        } else {
                            closeCurrentTab();
                        }
                    }
                })
                .show();
    }

    private void switchToTab(int index) {
        List<Prefs.Item> tabs = Prefs.tabs(this);
        if (index <= 0 || index >= tabs.size()) return;
        Prefs.Item it = tabs.remove(index);
        tabs.add(0, it);
        Prefs.saveTabs(this, tabs);
        tabBook = it.book;
        tabPath = it.path;
        openTab(it);
    }

    private void closeCurrentTab() {
        List<Prefs.Item> tabs = Prefs.tabs(this);
        if (tabs.size() <= 1) return;
        tabs.remove(0);
        Prefs.saveTabs(this, tabs);
        tabBook = tabs.get(0).book;
        tabPath = tabs.get(0).path;
        openTab(tabs.get(0));
    }

    private void openTab(Prefs.Item it) {
        tabSwitching = true;
        pendingScrollY = it.y;
        openBook(it.book, it.path);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.removeCallbacks(scrollPoller);
        pollHandler.postDelayed(scrollPoller, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(scrollPoller);
        // שומר את מיקום הגלילה של הכרטיסייה הנוכחית
        withScrollY(new ValueCallback<Integer>() {
            @Override
            public void onReceiveValue(Integer y) {
                syncCurrentTab(y);
            }
        });
    }

    // ---------- תפריט ----------

    private void showMenu() {
        final boolean dark = ThemeUtil.isDark(this);
        final String[] items = {
                "כרטיסיות", "קישורים בדף", "כותרות בדף", "רשימת סימניות", "היסטוריה",
                "שוליים: " + marginName(Prefs.margin(this)), "הגדל גופן", "הקטן גופן",
                dark ? "מצב יום" : "מצב לילה", "עזרה: מקשים"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: showTabs(); break;
                            case 1: showLinks(); break;
                            case 2: showToc(); break;
                            case 3: openList(Prefs.BOOKMARKS); break;
                            case 4: openList(Prefs.HISTORY); break;
                            case 5: showMarginChoice(); break;
                            case 6: changeZoom(1); break;
                            case 7: changeZoom(-1); break;
                            case 8:
                                ThemeUtil.toggle(ReaderActivity.this);
                                recreate();
                                break;
                            default: showHelp();
                        }
                    }
                })
                .show();
    }

    private static String marginName(int m) {
        return m == 0 ? "מלא" : (m == 2 ? "רחבים" : "צרים");
    }

    private void showMarginChoice() {
        String[] names = {"רוחב מלא (קריאה בשוליים מינימליים)", "שוליים צרים (ברירת מחדל)", "שוליים רחבים"};
        new AlertDialog.Builder(this)
                .setTitle("שולי עמוד")
                .setSingleChoiceItems(names, Prefs.margin(this), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.setMargin(ReaderActivity.this, which);
                        d.dismiss();
                        web.reload();
                    }
                })
                .show();
    }

    /**
     * עיצוב עמוד אחיד: מבטל שוליים קבועים של העמוד (למשל תפריט צד חסר ב-Vector הישן, שהשאיר חצי מסך ריק)
     * ומציב שוליים לפי ההגדרה. סך השוליים: צרים = 1/6 מרוחב המסך, רחבים = 1/3 (מתחלק לשני הצדדים),
     * מלא = קצת פחות מכל הרוחב.
     */
    private String layoutHead(String html) {
        int m = Prefs.margin(this);
        String side = m == 0 ? "1.5%" : (m == 2 ? "16.6%" : "8.3%");
        String low = html.toLowerCase();
        StringBuilder sb = new StringBuilder();
        if (!low.contains("name=\"viewport\"") && !low.contains("name='viewport'")) {
            sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        }
        sb.append("<style id=\"zr-layout\">")
                .append("html,body{margin:0!important;width:auto!important;max-width:none!important;min-width:0!important}")
                .append("body{padding-left:").append(side).append("!important;padding-right:").append(side)
                .append("!important;box-sizing:border-box!important}")
                .append("#content,.mw-body,.mw-body-content,#bodyContent,#mw-content-text,.vector-body,main,article{")
                .append("margin-left:0!important;margin-right:0!important;max-width:none!important;")
                .append("border-width:0!important;float:none!important}")
                .append("#content,.mw-body{padding-left:0!important;padding-right:0!important}")
                .append("img,video{max-width:100%!important;height:auto!important}table{max-width:100%}")
                .append("pre,code,kbd,samp{white-space:pre-wrap!important;word-wrap:break-word!important}")
                .append("*{max-width:100%!important;box-sizing:border-box!important}")
                .append("div,section,article,aside,figure,center,form{width:auto!important;min-width:0!important}")
                .append("table{width:100%!important;table-layout:auto!important}")
                .append("td,th{min-width:0!important;word-break:break-word;overflow-wrap:break-word}")
                .append("</style>");
        int at = low.indexOf("</head>");
        if (at < 0) return sb + html;
        return html.substring(0, at) + sb + html.substring(at);
    }

    private void openList(String mode) {
        startActivity(new Intent(this, ListScreenActivity.class).putExtra(ListScreenActivity.EX_MODE, mode));
    }

    private void changeZoom(int dir) {
        int cur = Prefs.zoom(this);
        int idx = 2;
        for (int i = 0; i < ZOOMS.length; i++) if (ZOOMS[i] <= cur) idx = i;
        idx = Math.max(0, Math.min(ZOOMS.length - 1, idx + dir));
        Prefs.setZoom(this, ZOOMS[idx]);
        web.getSettings().setTextZoom(ZOOMS[idx]);
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("מקשים")
                .setMessage("חצים למעלה/למטה: גלילה (למעלה בראש הדף: מעבר לסרגל)\n"
                        + "חצים ימינה/שמאלה, או 4/6: קישור קודם / הבא\n"
                        + "אישור או 5: פתיחת הקישור המסומן (בלי סימון: רשימת קישורים)\n"
                        + "2 / 8: עמוד למעלה / למטה\n"
                        + "1: דף ראשי\n"
                        + "3: רשימת קישורים בדף\n"
                        + "7: כותרות בדף\n"
                        + "9 או 0: החלפת ספר / חזרה לספרייה\n"
                        + "* : חיפוש\n"
                        + "# או MENU או מקש רך שמאלי: תפריט\n"
                        + "חזרה או מקש רך ימני או C: אחורה / יציאה\n\n"
                        + "חצים ימינה/שמאלה בדף בלי קישורים: הזזת העמוד הצידה.\n"
                        + "תפריט > כרטיסיות: העמודים האחרונים (עד 10), כל אחד עם מיקום הגלילה המדויק שלו. "
                        + "מעבר לעמוד אחר שומר אוטומטית את הקודם.")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ---------- מקשים ----------

    private boolean toolbarHasFocus() {
        View f = getCurrentFocus();
        return f != null && f != web && f.getId() != View.NO_ID
                && (f instanceof ImageButton);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        return handleKey(ev) || super.dispatchKeyEvent(ev);
    }

    /** מטפל במקש; true = נצרך. נקרא גם לפני ה-IME (מ-ZimWebView), כדי שמקשי ספרות ו"חזרה" לא ייבלעו. */
    private boolean handleKey(KeyEvent ev) {
        int a = Keys.map(ev);
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (down && ev.getRepeatCount() == 0) onBackPressed();
            return true;
        }
        if (a == Keys.NONE) return false;
        if (down && ev.getRepeatCount() == 0) showKeyHint(ev, a);

        // פעולות כלליות
        if (a == Keys.MENU) {
            if (down) showMenu();
            return true;
        }
        if (a == Keys.BACK) {
            if (down) onBackPressed();
            return true;
        }
        if (a == Keys.SEARCH || a == Keys.LIBRARY || a == Keys.HOME || a == Keys.LINKS || a == Keys.TOC) {
            if (down && !ev.isLongPress()) {
                if (a == Keys.SEARCH) openSearch();
                else if (a == Keys.LIBRARY) showBooks();
                else if (a == Keys.HOME) goHome();
                else if (a == Keys.LINKS) showLinks();
                else showToc();
            }
            return true;
        }

        // המיקוד על הסרגל: המערכת מטפלת, חוץ מ"למטה" שמחזיר לתוכן
        int kc = ev.getKeyCode();
        boolean dpad = kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN
                || kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT
                || kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER;
        if (toolbarHasFocus() && dpad) {
            if (a == Keys.DOWN) {
                if (down) web.requestFocus();
                return true;
            }
            return false;
        }

        if (!down) return true;
        switch (a) {
            case Keys.UP: scrollPage(-1, false); break;
            case Keys.DOWN: scrollPage(1, false); break;
            case Keys.PAGE_UP: scrollPage(-1, true); break;
            case Keys.PAGE_DOWN: scrollPage(1, true); break;
            case Keys.PREV_LINK: navLink(-1); break;
            case Keys.NEXT_LINK: navLink(1); break;
            case Keys.OK: openSelected(); break;
            default: break;
        }
        return true;
    }

    /**
     * גלילה נייטיבית בלבד: ה-WebView הישן לא מסנכרן את מיקום ה-JavaScript עם הגלילה האמיתית, ולכן כל קפיצה
     * (כותרות, קישורים) מבוצעת גם היא נייטיבית. בראש הדף "למעלה" מעביר לסרגל.
     */
    private void scrollPage(final int dir, final boolean page) {
        int h = Math.max(1, web.getHeight());
        if (dir < 0 && web.getScrollY() <= 0) {
            if (!page) focusToolbar();
            return;
        }
        web.scrollBy(0, dir * (int) (h * (page ? 0.9 : 0.25)));
    }

    private void focusToolbar() {
        findViewById(R.id.btnHome).requestFocus();
    }

    private void navLink(final int dir) {
        final float sc = scale();
        js("window.zr?zr.nav(" + dir + "," + (web.getScrollY() / sc) + "," + (web.getHeight() / sc) + "):'-1'",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        String[] p = v == null ? new String[0] : v.replace("\"", "").split(";");
                        if (p.length < 3) {
                            // אין קישורים (או שה-JavaScript לא רץ): החצים מזיזים את העמוד הצידה
                            web.scrollBy(dir > 0 ? -web.getWidth() / 3 : web.getWidth() / 3, 0);
                            return;
                        }
                        try {
                            double top = Double.parseDouble(p[1]) * sc, bot = Double.parseDouble(p[2]) * sc;
                            int sy = web.getScrollY(), h = web.getHeight();
                            int pad = (int) (30 * getResources().getDisplayMetrics().density);
                            if (top < sy + pad || bot > sy + h - pad) {
                                web.scrollTo(web.getScrollX(), Math.max(0, (int) (top - h / 3)));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
    }

    private void openSelected() {
        js("window.zr?zr.open():-1", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                // אין קישור מסומן (או שה-JavaScript לא רץ): רשימת הקישורים בדף
                if (!"1".equals(v)) showLinks();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void js(String code, ValueCallback<String> cb) {
        try {
            web.evaluateJavascript(code, cb);
        } catch (Throwable t) {
            if (cb != null) cb.onReceiveValue(null);
        }
    }

    // ---------- WebView ----------

    private static final String NAV_JS =
            "(function(){try{if(!window.zr){var L=[],o=null;"
            + "function collect(){L=[];var as=document.getElementsByTagName('a');"
            + "for(var k=0;k<as.length;k++){var h=as[k].getAttribute('href');"
            + "if(h&&h.charAt(0)!=='#'&&as[k].offsetWidth>0&&as[k].offsetHeight>0)L.push(as[k]);}}"
            + "function top(el){var y=0,q=el;while(q){y+=q.offsetTop||0;q=q.offsetParent;}return y;}"
            + "function pick(n){if(o){o.style.outline='';o.style.backgroundColor=o.__zbg||'';}"
            + "o=L[n];o.__zbg=o.style.backgroundColor;o.style.outline='3px solid #304E9A';"
            + "o.style.backgroundColor='rgba(138,164,230,.35)';var t=top(o);return t+';'+(t+o.offsetHeight);}"
            + "window.zr={nav:function(d,vt,vh){collect();if(!L.length)return '0';var n=o?L.indexOf(o):-1;"
            + "if(n>=0){var t0=top(o);if(t0<vt-5||t0>vt+vh)n=-1;}"
            + "if(n>=0){n=Math.max(0,Math.min(L.length-1,n+d));}else{"
            + "var f=-1,l=-1;for(var k=0;k<L.length;k++){var t=top(L[k]);if(t>=vt&&t<vt+vh){if(f<0)f=k;l=k;}}"
            + "if(f>=0){n=d>0?f:l;}else{n=-1;for(var k=0;k<L.length;k++){if(top(L[k])>=vt){n=k;break;}}"
            + "if(n<0)n=L.length-1;if(d<0&&n>0)n=n-1;}}"
            + "return L.length+';'+pick(n);},"
            + "open:function(){if(o){location.href=o.href;return 1;}return 0;}};}"
            + "var s=document.getElementById('zr-night');"
            + "if(%NIGHT%){if(!s){s=document.createElement('style');s.id='zr-night';"
            + "s.textContent='html,body,body *{background-color:#212121!important;color:#e0e0e0!important;"
            + "border-color:#555!important;background-image:none!important}a,a *{color:#8aa4e6!important}img{opacity:.85}';"
            + "document.head.appendChild(s);}}else if(s){s.parentNode.removeChild(s);}}catch(e){}})();";

    private String pathFromUrl(String url) {
        if (url == null || !url.startsWith(BASE)) return null;
        Uri u = Uri.parse(url);
        String enc = u.getEncodedPath();
        if (enc == null) return null;
        enc = enc.substring(1);
        int slash = enc.indexOf('/');
        return slash < 0 ? null : Uri.decode(enc.substring(slash + 1));
    }

    private WebResourceResponse notFound() {
        ByteArrayInputStream empty = new ByteArrayInputStream(new byte[0]);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    new HashMap<String, String>(), empty);
        }
        return new WebResourceResponse("text/plain", "UTF-8", empty);
    }

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            try {
                Uri u = Uri.parse(url);
                if (!"zim.local".equals(u.getHost())) return notFound();
                String enc = u.getEncodedPath().substring(1);
                int slash = enc.indexOf('/');
                if (slash < 0 || !enc.substring(0, slash).equals(bookId)) return notFound();
                String rest = Uri.decode(enc.substring(slash + 1));
                ZimFile.Entry e = zim.resolve(zim.findByFullPath(rest));
                if (e == null) return notFound();
                String mime = e.mime;
                int semi = mime.indexOf(';');
                if (semi > 0) mime = mime.substring(0, semi);
                if (legacyCss && mime.equals("text/css")) {
                    String css = cssCompat.fixCss(new String(zim.content(e), ZimFile.UTF8));
                    return new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(css.getBytes(ZimFile.UTF8)));
                }
                if (mime.equals("text/html")) {
                    String html = new String(zim.content(e), ZimFile.UTF8);
                    if (legacyCss) html = cssCompat.fixHtml(html);
                    return new WebResourceResponse(mime, "UTF-8",
                            new ByteArrayInputStream(layoutHead(html).getBytes(ZimFile.UTF8)));
                }
                boolean text = mime.startsWith("text/") || mime.contains("javascript")
                        || mime.contains("json") || mime.contains("xml");
                return new WebResourceResponse(mime, text ? "UTF-8" : null, zim.open(e));
            } catch (Throwable ex) {
                // כולל OutOfMemoryError: משאב אחד שנכשל לא מפיל את האפליקציה
                return notFound();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(BASE)) {
                int hash = url.indexOf('#');
                if (hash > 0 && curPath != null && curPath.equals(pathFromUrl(url.substring(0, hash)))) {
                    String id = Uri.decode(url.substring(hash + 1));
                    scrollToElement("(document.getElementById(" + jsString(id) + ")||document.getElementsByName("
                            + jsString(id) + ")[0])");
                    return true;
                }
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            String p = pathFromUrl(url);
            if (p != null) {
                curPath = p;
                onNavigate(p);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            String p = pathFromUrl(url);
            if (p == null) return;
            if (clearHist) {
                clearHist = false;
                view.clearHistory();
            }
            curPath = p;
            String t = view.getTitle();
            if (t == null || t.length() == 0 || t.startsWith("https://zim.local")) t = p.replace('_', ' ');
            titleView.setText(t);
            Prefs.addHistory(ReaderActivity.this, bookPath, p, t);
            Prefs.setPosition(ReaderActivity.this, bookPath, p);
            updateBookmarkIcon();
            final int restoreY = pendingScrollY;
            pendingScrollY = 0;
            tabSwitching = false;
            lastY = restoreY;
            syncCurrentTab(restoreY > 0 ? restoreY : 0);
            Runnable restore = new Runnable() {
                @Override
                public void run() {
                    web.scrollTo(0, restoreY);
                }
            };
            if (restoreY > 0) {
                hintHandler.postDelayed(restore, 250);
                hintHandler.postDelayed(restore, 900);
            } else if (url.indexOf('#') > 0) {
                hintHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        syncNative();
                    }
                }, 400);
            }
            js(NAV_JS.replace("%NIGHT%", ThemeUtil.isDark(ReaderActivity.this) ? "true" : "false"), null);
            if (pendingFind != null) {
                String f = pendingFind.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
                pendingFind = null;
                js("try{window.find('" + f + "',false,false,true)}catch(e){}", null);
                hintHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        syncNative();
                    }
                }, 400);
            }
        }
    }
}
