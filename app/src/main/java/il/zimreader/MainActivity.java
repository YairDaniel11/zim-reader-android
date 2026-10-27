package il.zimreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** מסך הספרייה: רשימת קבצי ה-ZIM. הרשימה נשמרת בין כניסות, וסריקה מחדש רק לפי בקשה. */
public class MainActivity extends Activity {
    private ListView list;
    private TextView empty;
    private TextView header;
    private boolean asked;
    private boolean scanning;
    private List<Library.Book> shown = new ArrayList<Library.Book>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeUtil.forceRtl(this);
        Library.init(this);

        header = (TextView) findViewById(R.id.header);
        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);
        String ver = "";
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = "  ·  גרסה " + pi.versionCode;
        } catch (Exception ignored) {
        }
        ((TextView) findViewById(R.id.hint)).setText(getString(R.string.hint_library) + ver);

        if (Library.list().isEmpty()) Library.setFromCache(Prefs.loadLibrary(this));

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < shown.size()) {
                    startActivity(new Intent(MainActivity.this, ReaderActivity.class)
                            .putExtra(ReaderActivity.EX_BOOK, shown.get(pos).path));
                }
            }
        });
        findViewById(R.id.btnRescan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
            }
        });
        findViewById(R.id.btnBookmarks).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openList(Prefs.BOOKMARKS);
            }
        });
        findViewById(R.id.btnHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openList(Prefs.HISTORY);
            }
        });
        findViewById(R.id.btnMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        render();
        openExternal(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openExternal(intent);
    }

    /** פתיחת קובץ ZIM מאפליקציה אחרת (למשל מנהל קבצים). */
    private void openExternal(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        android.net.Uri u = intent.getData();
        String path = "file".equals(u.getScheme()) ? u.getPath() : u.toString();
        intent.setAction(Intent.ACTION_MAIN); // לא לפתוח שוב בכל חזרה למסך
        if (path != null) {
            startActivity(new Intent(this, ReaderActivity.class).putExtra(ReaderActivity.EX_BOOK, path));
        }
    }

    private long lastBack;

    /** לחיצה כפולה על חזרה בספרייה יוצאת מהאפליקציה. */
    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            finishAffinity();
        } else {
            lastBack = now;
            android.widget.Toast.makeText(this, "הקש שוב על מקש חזור כדי לצאת מהאפליקציה",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void openList(String mode) {
        startActivity(new Intent(this, ListScreenActivity.class).putExtra(ListScreenActivity.EX_MODE, mode));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionUtil.hasStorage(this)) {
            // סריקה אוטומטית רק כשאין רשימה שמורה בכלל
            if (Library.list().isEmpty()) startScan();
            else render();
        } else if (!asked) {
            asked = true;
            new AlertDialog.Builder(this)
                    .setMessage(R.string.need_storage)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            PermissionUtil.requestStorage(MainActivity.this);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (PermissionUtil.hasStorage(this)) startScan();
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        empty.setText(R.string.scanning);
        empty.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Library.scan();
                    Prefs.saveLibrary(MainActivity.this, Library.list());
                } catch (Throwable ignored) {
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        if (!isFinishing()) render();
                    }
                });
            }
        }).start();
    }

    private void render() {
        shown = Library.list();
        header.setText(shown.isEmpty() ? getString(R.string.library)
                : getString(R.string.library) + " (" + shown.size() + ")");
        List<String[]> rows = new ArrayList<String[]>();
        int selected = 0;
        String last = Prefs.lastBook(this);
        for (int i = 0; i < shown.size(); i++) {
            Library.Book b = shown.get(i);
            if (b.path.equals(last)) selected = i;
            String file = b.path.substring(b.path.lastIndexOf('/') + 1);
            rows.add(new String[]{b.name,
                    String.format(Locale.US, "%.1f MB  ·  %s", b.size / 1048576.0, file)});
        }
        list.setAdapter(new RowAdapter(this, rows));
        if (rows.isEmpty()) {
            empty.setText(R.string.no_books);
            empty.setVisibility(View.VISIBLE);
            findViewById(R.id.btnRescan).requestFocus();
        } else {
            empty.setVisibility(View.GONE);
            list.requestFocus();
            list.setSelection(selected);   // חיווי: הספר האחרון שנקרא מסומן
        }
    }

    private void showMenu() {
        final boolean dark = ThemeUtil.isDark(this);
        String[] items = {"סרוק מחדש", "סימניות", "היסטוריה", dark ? "מצב יום" : "מצב לילה"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: startScan(); break;
                            case 1: openList(Prefs.BOOKMARKS); break;
                            case 2: openList(Prefs.HISTORY); break;
                            default:
                                ThemeUtil.toggle(MainActivity.this);
                                recreate();
                        }
                    }
                })
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int a = Keys.map(ev);
        if (a == Keys.MENU) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) showMenu();
            return true;
        }
        if (a == Keys.BACK && ev.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }
}
