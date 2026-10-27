package il.zimreader;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

import java.util.LinkedList;

/** בדיקת מקשים: מציג את קוד כל מקש שנלחץ, כדי לדעת מה המכשיר באמת שולח. יציאה: חזרה פעמיים ברצף. */
public class KeyTestActivity extends Activity {
    private final LinkedList<String> lines = new LinkedList<String>();
    private TextView log;
    private long lastBack;

    @Override
    protected void onCreate(Bundle s) {
        ThemeUtil.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_keytest);
        ThemeUtil.forceRtl(this);
        ((TextView) findViewById(R.id.header)).setText("בדיקת מקשים");
        log = (TextView) findViewById(R.id.keylog);
        log.setText("לחץ על מקשים וראה את הקוד שלהם.\nליציאה: לחץ על חזרה פעמיים ברצף.");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                long now = System.currentTimeMillis();
                if (now - lastBack < 1500) {
                    finish();
                    return true;
                }
                lastBack = now;
            }
            int u = ev.getUnicodeChar();
            String line = KeyEvent.keyCodeToString(ev.getKeyCode()) + " (" + ev.getKeyCode() + ")"
                    + (u > 32 ? "  תו: " + (char) u : "")
                    + "  ← " + actionName(Keys.map(ev));
            lines.addFirst(line);
            while (lines.size() > 12) lines.removeLast();
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            log.setText(sb.toString());
        }
        return true;
    }

    private static String actionName(int a) {
        switch (a) {
            case Keys.UP: return "למעלה";
            case Keys.DOWN: return "למטה";
            case Keys.PREV_LINK: return "קישור קודם";
            case Keys.NEXT_LINK: return "קישור הבא";
            case Keys.OK: return "אישור";
            case Keys.MENU: return "תפריט";
            case Keys.BACK: return "חזרה";
            case Keys.HOME: return "דף ראשי";
            case Keys.SEARCH: return "חיפוש";
            case Keys.LIBRARY: return "ספרים";
            case Keys.LINKS: return "קישורים בדף";
            case Keys.TOC: return "כותרות בדף";
            case Keys.PAGE_UP: return "עמוד למעלה";
            case Keys.PAGE_DOWN: return "עמוד למטה";
            default: return "לא מוגדר";
        }
    }
}
