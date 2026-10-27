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

    private WebView web;
    private TextView titleView;
    private ImageButton btnBookmark;
    private ZimFile zim;
    private String bookPath;
    private String bookId;
    private String curPath;
    private String pendingFind;

    @Override
    protected void onCreate(Bundle state) {
        ThemeUtil.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_reader);
        ThemeUtil.forceRtl(this);

        titleView = (TextView) findViewById(R.id.title);
        btnBookmark = (ImageButton) findViewById(R.id.btnBookmark);
        web = (WebView) findViewById(R.id.web);
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

        click(R.id.btnHome, new Runnable() { public void run() { goHome(); } });
        click(R.id.btnSearch, new Runnable() { public void run() { openSearch(); } });
        click(R.id.btnBookmark, new Runnable() { public void run() { toggleBookmark(); } });
        click(R.id.btnLibrary, new Runnable() { public void run() { showBooks(); } });
        click(R.id.btnMenu, new Runnable() { public void run() { showMenu(); } });

        String restore = state != null ? state.getString(EX_PATH) : null;
        handleIntent(getIntent(), restore);
        web.requestFocus();
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
                zim = Library.open(book);
                bookPath = book;
                bookId = Library.idOf(book);
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

    private void showToc() {
        String html = currentHtml();
        if (html == null || curPath == null) return;
        final List<String> ids = new ArrayList<String>();
        List<String> labels = new ArrayList<String>();
        Matcher m = HEADING.matcher(html);
        while (m.find() && labels.size() < 500) {
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
            ids.add(id == null ? "" : id);
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "אין כותרות בדף", Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] idx = {0};
        new AlertDialog.Builder(this)
                .setTitle("כותרות בדף")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String id = ids.get(which);
                        if (id.length() > 0) {
                            web.loadUrl(pageUrl(curPath) + "#" + Uri.encode(id));
                        } else {
                            js("var h=document.querySelectorAll('h1,h2,h3,h4,h5,h6');"
                                    + "if(h[" + which + "])h[" + which + "].scrollIntoView();", null);
                        }
                    }
                })
                .show();
    }

    // ---------- תפריט ----------

    private void showMenu() {
        final boolean dark = ThemeUtil.isDark(this);
        final String[] items = {
                "חיפוש", "דף ראשי", "הוסף/הסר סימנייה", "קישורים בדף", "כותרות בדף", "ספרים (החלפת ספר)",
                "סימניות", "היסטוריה", "הגדל גופן", "הקטן גופן", dark ? "מצב יום" : "מצב לילה",
                "בדיקת מקשים", "עזרה: מקשים"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: openSearch(); break;
                            case 1: goHome(); break;
                            case 2: toggleBookmark(); break;
                            case 3: showLinks(); break;
                            case 4: showToc(); break;
                            case 5: showBooks(); break;
                            case 6: openList(Prefs.BOOKMARKS); break;
                            case 7: openList(Prefs.HISTORY); break;
                            case 8: changeZoom(1); break;
                            case 9: changeZoom(-1); break;
                            case 10:
                                ThemeUtil.toggle(ReaderActivity.this);
                                recreate();
                                break;
                            case 11: startActivity(new Intent(ReaderActivity.this, KeyTestActivity.class)); break;
                            default: showHelp();
                        }
                    }
                })
                .show();
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
                        + "אם מקש לא עובד: תפריט > בדיקת מקשים.")
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
        int a = Keys.map(ev);
        if (a == Keys.NONE) return super.dispatchKeyEvent(ev);
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;

        // פעולות כלליות
        if (a == Keys.MENU) {
            if (down) showMenu();
            return true;
        }
        if (a == Keys.BACK) {
            if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(ev);
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
        if (toolbarHasFocus()) {
            if (a == Keys.DOWN) {
                if (down) web.requestFocus();
                return true;
            }
            return super.dispatchKeyEvent(ev);
        }

        if (!down) return true;
        int h = Math.max(1, web.getHeight());
        switch (a) {
            case Keys.UP:
                if (web.getScrollY() <= 0) findViewById(R.id.btnHome).requestFocus();
                else web.scrollBy(0, -h / 4);
                break;
            case Keys.DOWN: web.scrollBy(0, h / 4); break;
            case Keys.PAGE_UP: web.pageUp(false); break;
            case Keys.PAGE_DOWN: web.pageDown(false); break;
            case Keys.PREV_LINK: navLink(-1); break;
            case Keys.NEXT_LINK: navLink(1); break;
            case Keys.OK: openSelected(); break;
            default: break;
        }
        return true;
    }

    private void navLink(int dir) {
        js("window.zr?zr.nav(" + dir + "):-1", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if ("0".equals(v)) Toast.makeText(ReaderActivity.this, "אין קישורים בדף", Toast.LENGTH_SHORT).show();
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
            + "function pick(n){if(o){o.style.outline='';o.style.backgroundColor=o.__zbg||'';}"
            + "o=L[n];o.__zbg=o.style.backgroundColor;o.style.outline='3px solid #304E9A';"
            + "o.style.backgroundColor='rgba(138,164,230,.35)';var r=o.getBoundingClientRect();"
            + "if(r.top<40||r.bottom>window.innerHeight-40)window.scrollTo(0,window.pageYOffset+r.top-window.innerHeight/3);}"
            + "window.zr={nav:function(d){collect();if(!L.length)return 0;var n=o?L.indexOf(o):-1;"
            + "if(n>=0){n=Math.max(0,Math.min(L.length-1,n+d));}else{"
            + "for(var k=0;k<L.length;k++){var r=L[k].getBoundingClientRect();"
            + "if(r.top>=0&&r.top<window.innerHeight){n=k;break;}}"
            + "if(n<0)n=d>0?0:L.length-1;}pick(n);return L.length;},"
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
            if (url.startsWith(BASE)) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            String p = pathFromUrl(url);
            if (p != null) curPath = p;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            String p = pathFromUrl(url);
            if (p == null) return;
            curPath = p;
            String t = view.getTitle();
            if (t == null || t.length() == 0 || t.startsWith("https://zim.local")) t = p.replace('_', ' ');
            titleView.setText(t);
            Prefs.addHistory(ReaderActivity.this, bookPath, p, t);
            Prefs.setPosition(ReaderActivity.this, bookPath, p);
            updateBookmarkIcon();
            js(NAV_JS.replace("%NIGHT%", ThemeUtil.isDark(ReaderActivity.this) ? "true" : "false"), null);
            if (pendingFind != null) {
                String f = pendingFind.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
                pendingFind = null;
                js("try{window.find('" + f + "',false,false,true)}catch(e){}", null);
            }
        }
    }
}
