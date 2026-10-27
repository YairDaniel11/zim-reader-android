package il.zimreader;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

/** קובע איזה style להפעיל. בורר ערכת הנושא נשמר ב-Prefs; "לפי המערכת" נופל לבהיר כשאין מושג Dark Mode. */
public final class ThemeUtil {
    private ThemeUtil() {}

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    public static boolean isDark(Context c) {
        int pref = Prefs.theme(c);
        if (pref == 1) return false;
        if (pref == 2) return true;
        // UI_MODE_NIGHT קיים מ-API 8; במכשיר טרום Android 10 הדגל לא מוגדר - לקורא נוח יותר בהיר
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void toggle(Context c) {
        Prefs.setTheme(c, isDark(c) ? 1 : 2);
    }

    /** כל הממשק מימין לשמאל, גם כשהמכשיר מוגדר לשפה אחרת. API 17+ ולכן בטוח מ-minSdk 19. */
    public static void forceRtl(Activity a) {
        a.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }
}
