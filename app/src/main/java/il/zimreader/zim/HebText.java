package il.zimreader.zim;

import java.util.ArrayList;
import java.util.List;

/**
 * התאמה עברית חכמה (בהשראת תוסף קורא ה-ZIM של אוצריא):
 * קיפול ניקוד/טעמים/גרשיים, איחוד אותיות סופיות, התאמת אותיות שימוש (ה/ו/ב/כ/ל/מ/ש/ד),
 * תיקון אות סופית בסוף מילה, וניחוש ראשי תיבות (רמבם -> רמב"ם).
 */
public final class HebText {
    private HebText() {}

    private static final String PREFIX_LETTERS = "הובכלמשד";

    private static char fin(char c) {
        switch (c) {
            case 'ך': return 'כ';
            case 'ם': return 'מ';
            case 'ן': return 'נ';
            case 'ף': return 'פ';
            case 'ץ': return 'צ';
            default: return c;
        }
    }

    private static char toFinal(char c) {
        switch (c) {
            case 'כ': return 'ך';
            case 'מ': return 'ם';
            case 'נ': return 'ן';
            case 'פ': return 'ף';
            case 'צ': return 'ץ';
            default: return c;
        }
    }

    /** תו מקופל, או 0 אם צריך להשמיט אותו. */
    private static char foldChar(char c) {
        if (c >= 0x0591 && c <= 0x05C7) return 0;
        if (c == 0x05F3 || c == 0x05F4) return 0;
        if (c == '\'' || c == '"' || c == '`' || c == '’' || c == '”' || c == '‘') return 0;
        return Character.toLowerCase(fin(c));
    }

    /** הטקסט המקופל, בלי מיפוי חזרה. */
    public static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char f = foldChar(s.charAt(i));
            if (f != 0) sb.append(f);
        }
        return sb.toString();
    }

    /** טקסט מקופל + מיפוי: map[k] = האינדקס במקור של התו המקופל ה-k. */
    public static final class Folded {
        public final String text;
        public final int[] map;

        Folded(String t, int[] m) {
            text = t;
            map = m;
        }
    }

    public static Folded foldWithMap(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int[] map = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            char f = foldChar(s.charAt(i));
            if (f != 0) {
                map[sb.length()] = i;
                sb.append(f);
            }
        }
        return new Folded(sb.toString(), map);
    }

    private static boolean isWordChar(char c) {
        return (c >= 'א' && c <= 'ת') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** מיקומי התאמה (במחרוזת המקופלת): סוף במילה שלמה, ותחילה בגבול מילה או אחרי אות שימוש. */
    public static int firstWordMatch(String norm, String q, int from) {
        if (q.length() == 0) return -1;
        int i;
        while ((i = norm.indexOf(q, from)) != -1) {
            char before = i > 0 ? norm.charAt(i - 1) : 0;
            char after = i + q.length() < norm.length() ? norm.charAt(i + q.length()) : 0;
            boolean startOk = !isWordChar(before) || PREFIX_LETTERS.indexOf(before) >= 0;
            boolean endOk = !isWordChar(after);
            if (startOk && endOk) return i;
            from = i + 1;
        }
        return -1;
    }

    /** אות סופית בסוף כל מילה: "סתומימ" -> "סתומים". */
    public static String finalizeWord(String q) {
        StringBuilder sb = new StringBuilder(q);
        for (int i = 0; i < sb.length(); i++) {
            boolean endOfWord = i == sb.length() - 1 || sb.charAt(i + 1) == ' ' || sb.charAt(i + 1) == '_';
            if (endOfWord) sb.setCharAt(i, toFinal(sb.charAt(i)));
        }
        return sb.toString();
    }

    private static String stripMarks(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0591 && c <= 0x05C7) continue;
            sb.append(c);
        }
        return sb.toString();
    }

    /** גרסאות של השאילתה לחיפוש תחילית בכותרות, לפי סדר חשיבות (ללא כפילויות). */
    public static List<String> prefixVariants(String raw) {
        String q = stripMarks(raw.trim()).replace(' ', '_');
        List<String> out = new ArrayList<String>();
        if (q.length() == 0) return out;
        add(out, q);
        add(out, finalizeWord(q));
        // אותיות שימוש: "ריבית" -> "הריבית"
        if (q.length() >= 2 && isHebrew(q.charAt(0))) {
            for (int i = 0; i < PREFIX_LETTERS.length(); i++) {
                String p = PREFIX_LETTERS.charAt(i) + q;
                add(out, p);
                add(out, finalizeWord(p));
            }
        }
        // ראשי תיבות: "רמבם" -> רמב"ם
        if (q.matches("[א-ת]{2,6}")) {
            String a = finalizeWord(q.substring(0, q.length() - 1)) + "\"" + q.substring(q.length() - 1);
            add(out, a);
            add(out, a.replace('"', '״'));
        }
        return out;
    }

    private static boolean isHebrew(char c) {
        return c >= 'א' && c <= 'ת';
    }

    private static void add(List<String> l, String s) {
        if (!l.contains(s)) l.add(s);
    }
}
