package il.zimreader.zim;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * תאימות CSS ל-WebView ישן (אנדרואיד 4.4 = Chrome 30): משתני CSS (var(--x)) לא נתמכים שם, ולכן אתרים מודרניים
 * מאבדים את כל הצבעים. כאן מחליפים כל var(--x) בערך שהוגדר ב-:root / html / body / *, וממירים
 * מאפיינים לוגיים (padding-inline-start וכד') לגרסה עם קידומת webkit.
 */
public final class CssCompat {
    private final Map<String, String> vars = new HashMap<String, String>();

    private static final Pattern ROOT_BLOCK = Pattern.compile("(?:^|[}\\s,])(?::root|html|body|\\*)\\s*\\{([^}]*)\\}");
    private static final Pattern DECL = Pattern.compile("--([A-Za-z0-9_-]+)\\s*:\\s*([^;}]+)");
    private static final Pattern USE = Pattern.compile(
            "var\\(\\s*--([A-Za-z0-9_-]+)\\s*(?:,\\s*((?:[^()]|\\([^()]*\\))*))?\\)");
    private static final Pattern STYLE_TAG = Pattern.compile("(?is)(<style[^>]*>)(.*?)(</style>)");

    /** מעדכן את מפת המשתנים מהטקסט (ההגדרה הראשונה של כל משתנה גוברת). */
    private void collect(String css) {
        Matcher b = ROOT_BLOCK.matcher(css);
        while (b.find()) {
            Matcher d = DECL.matcher(b.group(1));
            while (d.find()) {
                String name = d.group(1);
                if (!vars.containsKey(name)) vars.put(name, d.group(2).trim());
            }
        }
    }

    private String substitute(String css) {
        String cur = css;
        for (int pass = 0; pass < 6 && cur.contains("var(--"); pass++) {
            Matcher m = USE.matcher(cur);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (m.find()) {
                String val = vars.get(m.group(1));
                if (val == null) val = m.group(2);
                if (val == null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                } else {
                    changed = true;
                    m.appendReplacement(sb, Matcher.quoteReplacement(val.trim()));
                }
            }
            m.appendTail(sb);
            cur = sb.toString();
            if (!changed) break;
        }
        return cur;
    }

    private static String logical(String css) {
        return css
                .replace("padding-inline-start", "-webkit-padding-start")
                .replace("padding-inline-end", "-webkit-padding-end")
                .replace("margin-inline-start", "-webkit-margin-start")
                .replace("margin-inline-end", "-webkit-margin-end")
                .replace("border-inline-start", "-webkit-border-start")
                .replace("border-inline-end", "-webkit-border-end");
    }

    /** קובץ CSS שלם. */
    public synchronized String fixCss(String css) {
        collect(css);
        return logical(substitute(css));
    }

    /** מעבד את בלוקי ה-style שבתוך ה-HTML (ומוסיף את משתניהם למפה). */
    public synchronized String fixHtml(String html) {
        if (!html.contains("<style") && !html.contains("<STYLE")) return html;
        Matcher m = STYLE_TAG.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String body = fixCss(m.group(2));
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + body + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** האם ה-WebView ישן (Chrome לפני 49, שבו אין משתני CSS)? לפי מחרוזת ה-User-Agent. */
    public static boolean isLegacyWebView(String userAgent, int sdkInt) {
        if (userAgent != null) {
            Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(userAgent);
            if (m.find()) return Integer.parseInt(m.group(1)) < 49;
        }
        return sdkInt < 21;
    }
}
