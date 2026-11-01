package il.zimreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** דף ההגדרות: כל ההגדרות במקום אחד. כל שורה מציגה את הערך הנוכחי; לחיצה משנה אותו. */
public class SettingsActivity extends Activity {
    private interface Pick {
        void on(int which);
    }

    private static final String[] THEMES = {"לפי המערכת", "בהיר", "כהה"};
    private static final String[] MARGINS = {"רוחב מלא", "צרים", "רחבים"};
    private static final String[] HINTS = {"אוטומטי", "תמיד", "אף פעם"};

    private ListView list;
    private final List<Runnable> actions = new ArrayList<Runnable>();

    @Override
    protected void onCreate(Bundle s) {
        ThemeUtil.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_list);
        ThemeUtil.forceRtl(this);

        ((TextView) findViewById(R.id.header)).setText("הגדרות");
        findViewById(R.id.hint).setVisibility(View.GONE);
        list = (ListView) findViewById(R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < actions.size() && actions.get(pos) != null) actions.get(pos).run();
            }
        });
        render();
    }

    private void add(List<String[]> rows, String title, String sub, Runnable action) {
        rows.add(new String[]{title, sub});
        actions.add(action);
    }

    private static String onOff(boolean on) {
        return on ? "פעיל" : "כבוי";
    }

    private void render() {
        final int sel = list.getFirstVisiblePosition();
        actions.clear();
        List<String[]> rows = new ArrayList<String[]>();

        add(rows, "מצב תצוגה", THEMES[Prefs.theme(this)], new Runnable() {
            public void run() {
                choose("מצב תצוגה", THEMES, Prefs.theme(SettingsActivity.this), new Pick() {
                    public void on(int w) {
                        Prefs.setTheme(SettingsActivity.this, w);
                        recreate();
                    }
                });
            }
        });

        add(rows, "גודל גופן", Prefs.zoom(this) + "%", new Runnable() {
            public void run() {
                String[] names = new String[Prefs.ZOOMS.length];
                int cur = 2;
                for (int i = 0; i < names.length; i++) {
                    names[i] = Prefs.ZOOMS[i] + "%";
                    if (Prefs.ZOOMS[i] <= Prefs.zoom(SettingsActivity.this)) cur = i;
                }
                choose("גודל גופן", names, cur, new Pick() {
                    public void on(int w) {
                        Prefs.setZoom(SettingsActivity.this, Prefs.ZOOMS[w]);
                        render();
                    }
                });
            }
        });

        add(rows, "שולי עמוד", MARGINS[Prefs.margin(this)], new Runnable() {
            public void run() {
                choose("שולי עמוד", new String[]{"רוחב מלא (שוליים מינימליים)", "שוליים צרים (ברירת מחדל)",
                        "שוליים רחבים"}, Prefs.margin(SettingsActivity.this), new Pick() {
                    public void on(int w) {
                        Prefs.setMargin(SettingsActivity.this, w);
                        render();
                    }
                });
            }
        });

        add(rows, "הסתר סרגלים בגלילה למטה",
                onOff(Prefs.hideBars(this)) + " · הסרגלים חוזרים בגלילה למעלה", new Runnable() {
            public void run() {
                Prefs.setHideBars(SettingsActivity.this, !Prefs.hideBars(SettingsActivity.this));
                render();
            }
        });

        add(rows, "פתיחה בהפעלה",
                Prefs.resumeLast(this) ? "במקום האחרון שנקרא" : "ברשימת הספרים", new Runnable() {
            public void run() {
                Prefs.setResumeLast(SettingsActivity.this, !Prefs.resumeLast(SettingsActivity.this));
                render();
            }
        });

        add(rows, "רמזי מקשים ועזרת מקשים",
                HINTS[Prefs.keyHints(this)] + " · כעת: " + (Prefs.showKeyUi(this) ? "מוצגים" : "מוסתרים"),
                new Runnable() {
            public void run() {
                choose("רמזי מקשים", new String[]{"אוטומטי (רק במכשיר עם מקשים)", "תמיד להציג", "אף פעם"},
                        Prefs.keyHints(SettingsActivity.this), new Pick() {
                    public void on(int w) {
                        Prefs.setKeyHints(SettingsActivity.this, w);
                        render();
                    }
                });
            }
        });

        add(rows, "מצב תאימות ל-WebView ישן", onOff(Prefs.forceLegacyCss(this)) + " · תיקון תצוגה במכשירים ישנים",
                new Runnable() {
            public void run() {
                Prefs.setForceLegacyCss(SettingsActivity.this, !Prefs.forceLegacyCss(SettingsActivity.this));
                render();
            }
        });

        add(rows, "נקה היסטוריה", Prefs.load(this, Prefs.HISTORY).size() + " דפים", new Runnable() {
            public void run() {
                confirm("למחוק את כל ההיסטוריה?", new Runnable() {
                    public void run() {
                        Prefs.clear(SettingsActivity.this, Prefs.HISTORY);
                        render();
                    }
                });
            }
        });

        add(rows, "סגור את כל הכרטיסיות", Prefs.tabs(this).size() + " כרטיסיות", new Runnable() {
            public void run() {
                confirm("לסגור את כל הכרטיסיות?", new Runnable() {
                    public void run() {
                        Prefs.clear(SettingsActivity.this, "tabs");
                        render();
                    }
                });
            }
        });

        if (Prefs.showKeyUi(this)) {
            add(rows, "עזרה: מקשים", "", new Runnable() {
                public void run() {
                    Keys.showHelp(SettingsActivity.this);
                }
            });
        }

        String ver = "";
        try {
            ver = "גרסה " + getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) {
        }
        add(rows, "קורא ZIM", ver, null);

        list.setAdapter(new RowAdapter(this, rows));
        list.setSelection(sel);
    }

    private void choose(String title, String[] names, int cur, final Pick pick) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        pick.on(which);
                    }
                })
                .show();
    }

    private void confirm(String msg, final Runnable yes) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        yes.run();
                        Toast.makeText(SettingsActivity.this, "בוצע", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int a = Keys.map(ev);
        if (a == Keys.BACK && ev.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) finish();
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }
}
