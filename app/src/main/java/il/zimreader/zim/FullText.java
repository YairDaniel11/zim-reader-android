package il.zimreader.zim;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * חיפוש בתוכן. אינדקס ה-Xapian שבתוך קבצי ZIM לא נקרא ב-Java, ולכן סורקים את המאמרים בעצמנו,
 * עם התאמה עברית (בלי ניקוד, אותיות סופיות, אותיות שימוש) וקטע הקשר לכל תוצאה.
 */
public final class FullText {
    private FullText() {}

    public static final class Hit {
        public ZimFile.Entry entry;
        public String title;
        public String snippet;
    }

    public interface Listener {
        /** נקרא מדי פעם עם אחוז ההתקדמות; מחזיר true כדי להפסיק. */
        boolean progress(int percent, int found);
    }

    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public static String htmlToText(String html) {
        String t = SCRIPT.matcher(html).replaceAll(" ");
        t = TAGS.matcher(t).replaceAll(" ");
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'");
        return SPACES.matcher(t).replaceAll(" ").trim();
    }

    public static List<Hit> search(ZimFile zim, String query, final int maxHits, final Listener l) throws IOException {
        final List<Hit> hits = new ArrayList<Hit>();
        final List<String> terms = new ArrayList<String>();
        for (String w : query.trim().split("\\s+")) {
            String f = HebText.fold(w);
            if (f.length() >= 1) terms.add(f);
        }
        if (terms.isEmpty()) return hits;
        final ZimFile z = zim;
        z.forEachArticle(new ZimFile.ArticleVisitor() {
            int tick;

            @Override
            public boolean visit(ZimFile.Entry e, int done, int total) throws IOException {
                if ((tick++ & 31) == 0 && l != null && l.progress(total == 0 ? 0 : (int) (100L * done / total), hits.size())) {
                    return false;
                }
                String text = htmlToText(new String(z.content(e), ZimFile.UTF8));
                HebText.Folded f = HebText.foldWithMap(text);
                int first = -1;
                for (String t : terms) {
                    int p = HebText.firstWordMatch(f.text, t, 0);
                    if (p < 0) return true;
                    if (first < 0) first = p;
                }
                Hit h = new Hit();
                h.entry = e;
                h.title = ZimFile.displayTitle(e);
                int at = f.map[Math.min(first, f.map.length - 1)];
                int from = Math.max(0, at - 40);
                int to = Math.min(text.length(), at + 80);
                h.snippet = (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
                hits.add(h);
                return hits.size() < maxHits;
            }
        });
        return hits;
    }
}
