package il.zimreader;

import android.view.KeyEvent;

/**
 * מיפוי מקשים אחיד לכל המסכים. במכשירי מקשים מקש התפריט הוא לרוב "מקש רך שמאלי"
 * (SOFT_LEFT) ומקש החזרה/מחיקה יכול להגיע כ-SOFT_RIGHT / DEL / CLEAR, ולא כ-MENU / BACK,
 * ולכן מטפלים בכל האפשרויות. ספרות מזוהות גם לפי התו שהמקש מפיק.
 */
public final class Keys {
    private Keys() {}

    public static final int NONE = 0, UP = 1, DOWN = 2, PREV_LINK = 3, NEXT_LINK = 4, OK = 5, MENU = 6,
            BACK = 7, HOME = 8, SEARCH = 9, LIBRARY = 10, LINKS = 11, TOC = 12, PAGE_UP = 13, PAGE_DOWN = 14;

    public static int map(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DOWN;
            // כיוון הקריאה מימין לשמאל: שמאלה = קישור הבא
            case KeyEvent.KEYCODE_DPAD_LEFT: return NEXT_LINK;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return PREV_LINK;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return OK;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SOFT_LEFT:
            case KeyEvent.KEYCODE_POUND: return MENU;
            case KeyEvent.KEYCODE_SOFT_RIGHT:
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
            case KeyEvent.KEYCODE_CLEAR:
            case KeyEvent.KEYCODE_ESCAPE: return BACK;
            case KeyEvent.KEYCODE_PAGE_UP: return PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN: return PAGE_DOWN;
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
            case KeyEvent.KEYCODE_SEARCH: return SEARCH;
            default:
                break;
        }
        int digit = digitOf(e);
        switch (digit) {
            case 1: return HOME;
            case 2: return PAGE_UP;
            case 3: return LINKS;
            case 4: return PREV_LINK;
            case 5: return OK;
            case 6: return NEXT_LINK;
            case 7: return TOC;
            case 8: return PAGE_DOWN;
            case 9:
            case 0: return LIBRARY;
            default:
                break;
        }
        int ch = e.getUnicodeChar();
        if (ch == '*') return SEARCH;
        if (ch == '#') return MENU;
        return NONE;
    }

    public static String name(int a) {
        switch (a) {
            case UP: return "למעלה";
            case DOWN: return "למטה";
            case PREV_LINK: return "קישור קודם";
            case NEXT_LINK: return "קישור הבא";
            case OK: return "אישור";
            case MENU: return "תפריט";
            case BACK: return "חזרה";
            case HOME: return "דף ראשי";
            case SEARCH: return "חיפוש";
            case LIBRARY: return "ספרים";
            case LINKS: return "קישורים בדף";
            case TOC: return "כותרות בדף";
            case PAGE_UP: return "עמוד למעלה";
            case PAGE_DOWN: return "עמוד למטה";
            default: return "לא מוגדר";
        }
    }

    private static int digitOf(KeyEvent e) {
        int k = e.getKeyCode();
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return k - KeyEvent.KEYCODE_0;
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) return k - KeyEvent.KEYCODE_NUMPAD_0;
        int ch = e.getUnicodeChar();
        if (ch >= '0' && ch <= '9') return ch - '0';
        return -1;
    }
}
