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
import android.widget.FrameLayout;
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
    private static final int BAR_DP = 56;
    private static final int HINT_DP = 24;

    private ZimWebView web;
    private View bar;
    private TextView hintView;
    private TextView titleView;
    private ImageButton btnBookmark;
    private ZimFile zim;
    private String bookPath;
    private String bookId;
    private String curPath;
    private String pendingFind;
    private boolean clearHist;
    private boolean legacyCss;
    private boolean barsShown = true;
    private boolean hideBarsOn;
    private boolean appliedDark;
    private boolean appliedHide;
    private boolean appliedKeys;
    private boolean appliedLegacy;
    private int appliedMargin;
    private int appliedFont;
    private int appliedLine;
    private il.zimreader.zim.CssCompat cssCompat = new il.zimreader.zim.CssCompat();

    @Override
    protected void onCreate(Bundle state) {
        ThemeUtil.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_reader);
        ThemeUtil.forceRtl(this);

        Library.init(this);
        appliedDark = ThemeUtil.isDark(this);
        bar = findViewById(R.id.bar);
        hintView = (TextView) findViewById(R.id.hint);
        titleView = (TextView) findViewById(R.id.title);
        btnBookmark = (ImageButton) findViewById(R.id.btnBookmark);
        web = (ZimWebView) findViewById(R.id.web);
        hintView.setText(R.string.hint_reader);

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
        web.setScrollListener(new ZimWebView.ScrollListener() {
            @Override
            public void onScroll(int y, int oldY) {
                onWebScroll(y, oldY);
            }
        });
        web.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (autoScrolling && e.getAction() == android.view.MotionEvent.ACTION_DOWN) stopAutoScroll();
                return false;
            }
        });
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return onWebLongClick();
            }
        });
        legacyCss = computeLegacy();
        appliedLegacy = legacyCss;
        appliedMargin = Prefs.margin(this);
        appliedFont = Prefs.font(this);
        appliedLine = Prefs.lineSpacing(this);
        appliedHide = hideWanted();
        appliedKeys = Prefs.showKeyUi(this);
        applyBars();

        click(R.id.btnTabs, new Runnable() { public void run() { showTabs(); } });
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
        if (hasFocus) applySystemUi();
    }

    private final android.os.Handler hintHandler = new android.os.Handler();

    private boolean computeLegacy() {
        return Prefs.forceLegacyCss(this) || il.zimreader.zim.CssCompat.isLegacyWebView(
                web.getSettings().getUserAgentString(), Build.VERSION.SDK_INT);
    }

    /**
     * סידור הסרגלים לפי ההגדרות. כברירת מחדל הדף מתחיל מתחת לסרגל העליון ומעל שורת הרמזים. כשהסרגלים
     * נעלמים בגלילה הדף תופס את כל המסך (ושוליים פנימיים בדף משאירים מקום לסרגלים בראשו) כדי שהוא
     * לא יקפוץ בכל הסתרה.
     */
    private void applyBars() {
        float d = getResources().getDisplayMetrics().density;
        boolean keys = Prefs.showKeyUi(this);
        hideBarsOn = hideWanted();
        hintView.setVisibility(keys ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnTabs).setVisibility(keys ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
        lp.topMargin = hideBarsOn ? 0 : (int) (BAR_DP * d);
        lp.bottomMargin = (hideBarsOn || !keys) ? 0 : (int) (HINT_DP * d);
        web.setLayoutParams(lp);
        bar.setTranslationY(0);
        hintView.setTranslationY(0);
        barsShown = true;
    }

    private void showBars(boolean show) {
        if (show == barsShown) return;
        barsShown = show;
        bar.animate().translationY(show ? 0 : -bar.getHeight()).setDuration(150).start();
        hintView.animate().translationY(show ? 0 : hintView.getHeight()).setDuration(150).start();
    }

    private void onWebScroll(int y, int oldY) {
        if (!hideBarsOn) return;
        if (y <= 0 || oldY - y > 8) showBars(true);
        else if (y - oldY > 8) showBars(false);
    }

    /** חיווי רגעי בשורת התחתית: איזה מקש נלחץ ואיזו פעולה הופעלה (עוזר לאבחן מכשירים). */
    private void showKeyHint(KeyEvent ev, int action) {
        final TextView hint = hintView;
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
        final String[] names = labels;
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("קישורים (" + labels.length + ") · לחיצה ארוכה: כרטיסייה חדשה")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        web.loadUrl(urls.get(which));
                    }
                })
                .create();
        dlg.show();
        dlg.getListView().setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                openInBackgroundTab(urls.get(pos), names[pos]);
                return true;
            }
        });
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
        labels.add("＋ כרטיסייה חדשה (חיפוש)");
        for (int i = 0; i < n; i++) {
            Prefs.Item t = tabs.get(i);
            labels.add((i == 0 ? "● " : (i + 1) + ". ") + t.title.replace('_', ' ') + "  ·  " + Library.nameOf(t.book));
        }
        List<String[]> rows = new ArrayList<String[]>();
        for (String l : labels) rows.add(new String[]{l});
        final AlertDialog[] holder = new AlertDialog[1];
        RowAdapter adapter = new RowAdapter(this, rows, new RowAdapter.Deleter() {
            @Override
            public boolean canDelete(int position) {
                return position > 0 && n > 1;
            }

            @Override
            public void delete(int position) {
                if (holder[0] != null) holder[0].dismiss();
                closeTab(position - 1);
            }
        });
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("כרטיסיות (" + n + "/10)" + (Prefs.showKeyUi(this) ? " · חץ שמאלה = סגירה" : ""))
                .setAdapter(adapter, null)
                .create();
        holder[0] = dlg;
        dlg.show();
        RowAdapter.attach(dlg.getListView(), new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int which, long id) {
                dlg.dismiss();
                if (which == 0) openSearch();
                else if (which > 1) switchToTab(which - 1);
            }
        });
        dlg.getListView().setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0 || n <= 1) return true;
                holder[0].dismiss();
                closeTab(pos - 1);
                return true;
            }
        });
    }

    /** פותח קישור בכרטיסייה חדשה ברקע: נכנסת מיד אחרי הנוכחית, בלי לעבור אליה. */
    private void openInBackgroundTab(String url, String label) {
        int hash = url.indexOf('#');
        String p = pathFromUrl(hash > 0 ? url.substring(0, hash) : url);
        if (p == null || bookPath == null) return;
        List<Prefs.Item> tabs = Prefs.tabs(this);
        if (!tabs.isEmpty() && tabs.get(0).book.equals(bookPath) && tabs.get(0).path.equals(p)) {
            Toast.makeText(this, "הדף כבר פתוח", Toast.LENGTH_SHORT).show();
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).book.equals(bookPath) && tabs.get(i).path.equals(p)) {
                tabs.remove(i);
                break;
            }
        }
        Prefs.Item it = new Prefs.Item();
        it.book = bookPath;
        it.path = p;
        it.title = (label == null || label.length() == 0) ? p.substring(p.lastIndexOf('/') + 1) : label;
        it.ts = System.currentTimeMillis();
        tabs.add(Math.min(1, tabs.size()), it);
        while (tabs.size() > 10) tabs.remove(tabs.size() - 1);
        Prefs.saveTabs(this, tabs);
        Toast.makeText(this, "נפתח בכרטיסייה חדשה", Toast.LENGTH_SHORT).show();
    }

    /** לחיצה ארוכה על קישור בדף (מגע). */
    private boolean onWebLongClick() {
        WebView.HitTestResult r = web.getHitTestResult();
        if (r == null) return false;
        int t = r.getType();
        if (t != WebView.HitTestResult.SRC_ANCHOR_TYPE && t != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false;
        final String url = r.getExtra();
        if (url == null || !url.startsWith(BASE)) return false;
        int hash = url.indexOf('#');
        String p = pathFromUrl(hash > 0 ? url.substring(0, hash) : url);
        if (p == null || p.equals(curPath)) return false;
        new AlertDialog.Builder(this)
                .setItems(new String[]{"פתח", "פתח בכרטיסייה חדשה"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) web.loadUrl(url);
                        else openInBackgroundTab(url, null);
                    }
                })
                .show();
        return true;
    }

    /** סוגר כרטיסייה לפי מיקום; סגירת הנוכחית עוברת לבאה. */
    private void closeTab(int index) {
        List<Prefs.Item> tabs = Prefs.tabs(this);
        if (tabs.size() <= 1 || index < 0 || index >= tabs.size()) return;
        tabs.remove(index);
        Prefs.saveTabs(this, tabs);
        if (index == 0) {
            tabBook = tabs.get(0).book;
            tabPath = tabs.get(0).path;
            openTab(tabs.get(0));
        } else {
            showTabsDialog();
        }
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

    private void openTab(Prefs.Item it) {
        tabSwitching = true;
        pendingScrollY = it.y;
        openBook(it.book, it.path);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeUtil.isDark(this) != appliedDark) {
            recreate();
            return;
        }
        applySystemUi();
        // הגדרות ששונו במסך ההגדרות
        web.getSettings().setTextZoom(Prefs.zoom(this));
        legacyCss = computeLegacy();
        boolean reload = Prefs.font(this) != appliedFont || Prefs.lineSpacing(this) != appliedLine
                || Prefs.margin(this) != appliedMargin || hideWanted() != appliedHide
                || Prefs.showKeyUi(this) != appliedKeys || legacyCss != appliedLegacy;
        appliedMargin = Prefs.margin(this);
        appliedFont = Prefs.font(this);
        appliedLine = Prefs.lineSpacing(this);
        appliedHide = hideWanted();
        appliedKeys = Prefs.showKeyUi(this);
        appliedLegacy = legacyCss;
        applyBars();
        if (reload && curPath != null) {
            pendingScrollY = web.getScrollY();
            web.reload();
        }
        pollHandler.removeCallbacks(scrollPoller);
        pollHandler.postDelayed(scrollPoller, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoScroll();
        pollHandler.removeCallbacks(scrollPoller);
        // שומר את מיקום הגלילה של הכרטיסייה הנוכחית
        withScrollY(new ValueCallback<Integer>() {
            @Override
            public void onReceiveValue(Integer y) {
                syncCurrentTab(y);
            }
        });
    }

    // ---------- ערך אקראי ----------

    /** ערך אקראי: בספר הפתוח, או מכל הספרים (בהסתברות יחסית לגודל הספר). */
    private void randomArticle(final boolean anyBook) {
        final List<Library.Book> books = Library.list();
        final String current = bookPath;
        Toast.makeText(this, "מחפש ערך אקראי…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String book = current;
                String path = null;
                try {
                    if (anyBook && !books.isEmpty()) {
                        long total = 0;
                        for (Library.Book b : books) total += Math.max(1, b.size);
                        long r = (long) (Math.random() * total);
                        for (Library.Book b : books) {
                            r -= Math.max(1, b.size);
                            if (r < 0) {
                                book = b.path;
                                break;
                            }
                        }
                    }
                    ZimFile z = Library.open(book);
                    ZimFile.Entry e = z.randomArticle();
                    if (e != null) path = z.fullPathOf(e);
                } catch (Throwable ignored) {
                }
                final String fb = book, fp = path;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (fp == null) {
                            Toast.makeText(ReaderActivity.this, "לא נמצא ערך", Toast.LENGTH_SHORT).show();
                        } else if (fb.equals(bookPath)) {
                            load(fp);
                        } else {
                            openBook(fb, fp);
                        }
                    }
                });
            }
        }).start();
    }

    // ---------- חיפוש בדף ----------

    private void showFind() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("הקלד מילה לחיפוש בדף");
        final String[] last = {null};
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("חיפוש בדף")
                .setView(input)
                .setPositiveButton("הבא", null)
                .setNeutralButton("הקודם", null)
                .setNegativeButton("סגור", null)
                .create();
        web.setFindListener(new WebView.FindListener() {
            @Override
            public void onFindResultReceived(int active, int total, boolean done) {
                if (done) dlg.setTitle(total == 0 ? "לא נמצא" : "חיפוש בדף · " + (active + 1) + "/" + total);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                web.clearMatches();
                web.setFindListener(null);
            }
        });
        dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findStep(input.getText().toString(), last, true);
            }
        });
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findStep(input.getText().toString(), last, false);
            }
        });
    }

    private void findStep(String q, String[] last, boolean forward) {
        if (q.length() == 0) return;
        if (!q.equals(last[0])) {
            last[0] = q;
            web.findAllAsync(q);        // מדלג לתוצאה הראשונה
        } else {
            web.findNext(forward);
        }
    }

    // ---------- שיתוף והעתקה ----------

    /** שיתוף או העתקה: של הטקסט המסומן (אם יש) או של כל טקסט הדף. */
    private void shareText() {
        js("(window.getSelection?window.getSelection().toString():'')", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                String sel = "";
                try {
                    if (v != null && !"null".equals(v)) sel = new org.json.JSONArray("[" + v + "]").getString(0).trim();
                } catch (Exception ignored) {
                }
                final List<String> labels = new ArrayList<String>();
                final List<String> texts = new ArrayList<String>();
                final List<Boolean> share = new ArrayList<Boolean>();
                String page = null;
                String html = currentHtml();
                if (html != null) page = FullText.htmlToText(html);
                if (sel.length() > 0) {
                    labels.add("שתף את הטקסט המסומן");
                    texts.add(sel);
                    share.add(true);
                    labels.add("העתק את הטקסט המסומן");
                    texts.add(sel);
                    share.add(false);
                }
                if (page != null && page.length() > 0) {
                    labels.add("שתף את כל טקסט הדף");
                    texts.add(pageTitle() + "\n\n" + page);
                    share.add(true);
                    labels.add("העתק את כל טקסט הדף");
                    texts.add(pageTitle() + "\n\n" + page);
                    share.add(false);
                }
                if (labels.isEmpty()) {
                    Toast.makeText(ReaderActivity.this, "אין טקסט", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(ReaderActivity.this)
                        .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                String t = texts.get(which);
                                if (share.get(which)) {
                                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, t);
                                    startActivity(Intent.createChooser(i, "שיתוף"));
                                } else {
                                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                                            .setPrimaryClip(android.content.ClipData.newPlainText("ZIM", t));
                                    Toast.makeText(ReaderActivity.this, "הועתק", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .show();
            }
        });
    }

    // ---------- מסך מלא, גופן ומרווח שורות ----------

    /** הסתרת הסרגלים בגלילה: לפי ההגדרה, או תמיד במסך מלא. */
    private boolean hideWanted() {
        return Prefs.hideBars(this) || Prefs.fullscreen(this);
    }

    private void applySystemUi() {
        if (Build.VERSION.SDK_INT < 19) return;
        int flags = View.SYSTEM_UI_FLAG_VISIBLE;
        if (Prefs.fullscreen(this)) {
            flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void toggleFullscreen() {
        boolean on = !Prefs.fullscreen(this);
        Prefs.setFullscreen(this, on);
        if (on) Toast.makeText(this, "מסך מלא · גלול למעלה כדי להחזיר את הסרגל", Toast.LENGTH_LONG).show();
        onResume();     // מחיל את ההגדרות החדשות (סרגלים, ריווח הדף, מסך המערכת)
    }

    private static final String[] FONT_FAMILIES = {null, "serif", "sans-serif", "monospace"};
    private static final String[] LINE_HEIGHTS = {null, "1.5", "1.8", "2.1"};

    private String fontCss() {
        StringBuilder sb = new StringBuilder();
        int f = Prefs.font(this), l = Prefs.lineSpacing(this);
        if (f > 0 && f < FONT_FAMILIES.length) {
            sb.append("body,body *{font-family:").append(FONT_FAMILIES[f]).append("!important}");
        }
        if (l > 0 && l < LINE_HEIGHTS.length) {
            sb.append("body,p,li,td,dd,div{line-height:").append(LINE_HEIGHTS[l]).append("!important}");
        }
        return sb.toString();
    }

    // ---------- גלילה אוטומטית ----------

    private static final int SCROLL_TICK_MS = 30;
    private boolean autoScrolling;
    private float scrollRemainder;
    private int stuckTicks;
    private final android.os.Handler scrollHandler = new android.os.Handler();
    private final Runnable scrollTick = new Runnable() {
        @Override
        public void run() {
            if (!autoScrolling) return;
            float d = getResources().getDisplayMetrics().density;
            scrollRemainder += Prefs.SCROLL_DP_PER_SEC[Prefs.autoScroll(ReaderActivity.this) - 1] * d
                    * SCROLL_TICK_MS / 1000f;
            int px = (int) scrollRemainder;
            if (px > 0) {
                scrollRemainder -= px;
                int before = web.getScrollY();
                web.scrollBy(0, px);
                stuckTicks = web.getScrollY() == before ? stuckTicks + 1 : 0;
                if (stuckTicks > 15) {            // הגענו לסוף הדף
                    stopAutoScroll();
                    return;
                }
            }
            scrollHandler.postDelayed(this, SCROLL_TICK_MS);
        }
    };

    private void toggleAutoScroll() {
        if (autoScrolling) stopAutoScroll();
        else startAutoScroll();
    }

    private void startAutoScroll() {
        autoScrolling = true;
        scrollRemainder = 0;
        stuckTicks = 0;
        web.setKeepScreenOn(true);
        showScrollSpeed("גלילה אוטומטית");
        scrollHandler.removeCallbacks(scrollTick);
        scrollHandler.postDelayed(scrollTick, SCROLL_TICK_MS);
    }

    private void stopAutoScroll() {
        if (!autoScrolling) return;
        autoScrolling = false;
        scrollHandler.removeCallbacks(scrollTick);
        web.setKeepScreenOn(false);
        Toast.makeText(this, "הגלילה האוטומטית נעצרה", Toast.LENGTH_SHORT).show();
    }

    private void changeScrollSpeed(int delta) {
        Prefs.setAutoScroll(this, Prefs.autoScroll(this) + delta);
        showScrollSpeed("מהירות");
    }

    private void showScrollSpeed(String prefix) {
        Toast.makeText(this, prefix + ": " + Prefs.autoScroll(this) + "/" + Prefs.SCROLL_DP_PER_SEC.length,
                Toast.LENGTH_SHORT).show();
    }

    // ---------- תפריט ----------

    private void showMenu() {
        final boolean keys = Prefs.showKeyUi(this);
        List<String> items = new ArrayList<String>();
        items.add("כרטיסיות");
        items.add("קישורים בדף");
        items.add("כותרות בדף");
        items.add("רשימת סימניות");
        items.add("היסטוריה");
        items.add("הגדרות");
        items.add("ערך אקראי בספר הנוכחי");
        items.add("ערך אקראי מכל הספרים");
        items.add(autoScrolling ? "עצור גלילה אוטומטית" : "גלילה אוטומטית");
        items.add("חיפוש בדף");
        items.add("שיתוף / העתקה של טקסט");
        items.add(Prefs.fullscreen(this) ? "יציאה ממסך מלא" : "מסך מלא");
        if (keys) items.add("עזרה: מקשים");
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: showTabs(); break;
                            case 1: showLinks(); break;
                            case 2: showToc(); break;
                            case 3: openList(Prefs.BOOKMARKS); break;
                            case 4: openList(Prefs.HISTORY); break;
                            case 5: startActivity(new Intent(ReaderActivity.this, SettingsActivity.class)); break;
                            case 6: randomArticle(false); break;
                            case 7: randomArticle(true); break;
                            case 8: toggleAutoScroll(); break;
                            case 9: showFind(); break;
                            case 10: shareText(); break;
                            case 11: toggleFullscreen(); break;
                            default: Keys.showHelp(ReaderActivity.this);
                        }
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
        String top = hideBarsOn ? BAR_DP + "px" : "0";
        String bottom = hideBarsOn && Prefs.showKeyUi(this) ? HINT_DP + "px" : "0";
        String side = m == 0 ? "1.5%" : (m == 2 ? "16.6%" : "8.3%");
        String low = html.toLowerCase();
        StringBuilder sb = new StringBuilder();
        if (!low.contains("name=\"viewport\"") && !low.contains("name='viewport'")) {
            sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        }
        sb.append("<style id=\"zr-layout\">")
                .append("html,body{margin:0!important;width:auto!important;max-width:none!important;min-width:0!important}")
                .append("body{padding-left:").append(side).append("!important;padding-right:").append(side)
                .append("!important;padding-top:").append(top).append("!important;padding-bottom:").append(bottom)
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
                .append(fontCss())
                .append("</style>");
        int at = low.indexOf("</head>");
        if (at < 0) return sb + html;
        return html.substring(0, at) + sb + html.substring(at);
    }

    private void openList(String mode) {
        startActivity(new Intent(this, ListScreenActivity.class).putExtra(ListScreenActivity.EX_MODE, mode));
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
        if (a == Keys.AUTOSCROLL) {
            if (down && ev.getRepeatCount() == 0) toggleAutoScroll();
            return true;
        }
        if (autoScrolling && down) {
            // בזמן גלילה אוטומטית: ▼ מהר יותר, ▲ לאט יותר, כל מקש אחר עוצר
            if (a == Keys.DOWN || a == Keys.UP) changeScrollSpeed(a == Keys.DOWN ? 1 : -1);
            else stopAutoScroll();
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
        if (autoScrolling) {
            stopAutoScroll();
            return;
        }
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
                if (!p.equals(curPath)) stopAutoScroll();
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
