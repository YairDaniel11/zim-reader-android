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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import il.zimreader.zim.ZimFile;

/** קורא המאמרים: WebView שמקבל את התוכן ישירות מקובץ ה-ZIM. */
public class ReaderActivity extends Activity {
    public static final String EX_BOOK = "book";
    public static final String EX_PATH = "path";

    private static final String BASE = "https://zim.local/";
    private static final int[] ZOOMS = {60, 80, 100, 120, 150, 180, 220};

    private WebView web;
    private TextView titleView;
    private ImageButton btnBookmark;
    private ZimFile zim;
    private String bookPath;
    private String bookId;
    private String curPath;

    @Override
    protected void onCreate(Bundle state) {
        ThemeUtil.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_reader);
        ThemeUtil.forceRtl(this);

        titleView = (TextView) findViewById(R.id.title);
        btnBookmark = (ImageButton) findViewById(R.id.btnBookmark);
        web = (WebView) findViewById(R.id.web);
        // כפתורי הסרגל למגע בלבד - במקשים הפוקוס נשאר על התוכן
        int[] barIds = {R.id.btnHome, R.id.btnSearch, R.id.btnBookmark, R.id.btnMenu};
        for (int id : barIds) findViewById(id).setFocusable(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(Prefs.zoom(this));
        web.setBackgroundColor(ThemeUtil.isDark(this) ? 0xFF212121 : 0xFFFFFFFF);
        web.setWebViewClient(new Client());

        findViewById(R.id.btnHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goHome();
            }
        });
        findViewById(R.id.btnSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch();
            }
        });
        btnBookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBookmark();
            }
        });
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        String restore = state != null ? state.getString(EX_PATH) : null;
        handleIntent(getIntent(), restore);
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
        try {
            if (!book.equals(bookPath)) {
                zim = Library.open(book);
                bookPath = book;
                bookId = Library.idOf(book);
            }
            String path = restorePath != null ? restorePath : intent.getStringExtra(EX_PATH);
            if (path == null) {
                ZimFile.Entry m = zim.mainEntry();
                if (m == null) throw new java.io.IOException("no main page");
                path = zim.fullPathOf(m);
            }
            load(path);
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void load(String fullPath) {
        web.loadUrl(BASE + bookId + "/" + Uri.encode(fullPath, "/"));
    }

    private void goHome() {
        try {
            ZimFile.Entry m = zim.mainEntry();
            if (m != null) load(zim.fullPathOf(m));
        } catch (Exception ignored) {
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

    // ---------- תפריט ----------

    private void showMenu() {
        final boolean dark = ThemeUtil.isDark(this);
        final String[] items = {
                "חיפוש", "דף ראשי", "הוסף/הסר סימנייה", "סימניות", "היסטוריה", "ספרים",
                "הגדל גופן", "הקטן גופן", dark ? "מצב יום" : "מצב לילה", "עזרה: מקשים"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: openSearch(); break;
                            case 1: goHome(); break;
                            case 2: toggleBookmark(); break;
                            case 3: openList(Prefs.BOOKMARKS); break;
                            case 4: openList(Prefs.HISTORY); break;
                            case 5: finish(); break;
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
                .setMessage("חצים למעלה/למטה: גלילה\n"
                        + "חצים ימינה/שמאלה (או 4/6): קישור קודם/הבא\n"
                        + "אישור (או 5): פתיחת הקישור המסומן\n"
                        + "2 / 8: עמוד למעלה / למטה\n"
                        + "1: דף ראשי\n"
                        + "* : חיפוש\n"
                        + "# או MENU: תפריט\n"
                        + "חזרה: אחורה / יציאה")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ---------- מקשים ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean mine;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_POUND:
            case KeyEvent.KEYCODE_MENU:
                mine = true;
                break;
            default:
                mine = false;
        }
        if (!mine) return super.dispatchKeyEvent(ev);
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            int h = web.getHeight();
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: web.scrollBy(0, -h / 4); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: web.scrollBy(0, h / 4); break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_6: js("window.zr&&zr.nav(1)"); break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_4: js("window.zr&&zr.nav(-1)"); break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_5: js("window.zr&&zr.open()"); break;
                case KeyEvent.KEYCODE_2: web.pageUp(false); break;
                case KeyEvent.KEYCODE_8: web.pageDown(false); break;
                case KeyEvent.KEYCODE_1: goHome(); break;
                case KeyEvent.KEYCODE_STAR: openSearch(); break;
                default: showMenu();
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void js(String code) {
        web.evaluateJavascript(code, null);
    }

    // ---------- WebView ----------

    private static final String NAV_JS =
            "(function(){if(!window.zr){var L=[],o=null;"
            + "function collect(){L=[];var as=document.getElementsByTagName('a');"
            + "for(var k=0;k<as.length;k++){var h=as[k].getAttribute('href');"
            + "if(h&&h.charAt(0)!=='#'&&as[k].offsetWidth>0&&as[k].offsetHeight>0)L.push(as[k]);}}"
            + "function pick(n){if(o){o.style.outline='';o.style.backgroundColor=o.__zbg||'';}"
            + "o=L[n];o.__zbg=o.style.backgroundColor;o.style.outline='3px solid #304E9A';"
            + "o.style.backgroundColor='rgba(138,164,230,.35)';var r=o.getBoundingClientRect();"
            + "if(r.top<40||r.bottom>window.innerHeight-40)window.scrollTo(0,window.pageYOffset+r.top-window.innerHeight/3);}"
            + "window.zr={nav:function(d){collect();if(!L.length)return;var n=o?L.indexOf(o):-1;"
            + "if(n>=0){n=Math.max(0,Math.min(L.length-1,n+d));}else{"
            + "for(var k=0;k<L.length;k++){var r=L[k].getBoundingClientRect();"
            + "if(r.top>=0&&r.top<window.innerHeight){n=k;break;}}"
            + "if(n<0)n=d>0?0:L.length-1;}pick(n);},"
            + "open:function(){if(o)location.href=o.href;}};}"
            + "var s=document.getElementById('zr-night');"
            + "if(%NIGHT%){if(!s){s=document.createElement('style');s.id='zr-night';"
            + "s.textContent='html,body,body *{background-color:#212121!important;color:#e0e0e0!important;"
            + "border-color:#555!important;background-image:none!important}a,a *{color:#8aa4e6!important}img{opacity:.85}';"
            + "document.head.appendChild(s);}}else if(s){s.parentNode.removeChild(s);}})();";

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
            } catch (Exception ex) {
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
            updateBookmarkIcon();
            js(NAV_JS.replace("%NIGHT%", ThemeUtil.isDark(ReaderActivity.this) ? "true" : "false"));
        }
    }
}
