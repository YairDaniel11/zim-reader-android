package il.zimreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * סימניות או היסטוריה. MENU / # / לחיצה ארוכה: אפשרויות (מחיקה; בסימניות גם העברה לתיקייה).
 * בסימניות: תיקיות בראש הרשימה, ולחיצה על סימנייה חוזרת למקום הגלילה שבו נשמרה.
 */
public class ListScreenActivity extends Activity {
    public static final String EX_MODE = "mode";

    private static final int ITEM = 0, FOLDER = 1, BACK = 2;

    private String mode;
    private boolean bookmarks;
    private String curFolder = "";
    private ListView list;
    private TextView empty;
    private TextView header;
    private List<Prefs.Item> items = new ArrayList<Prefs.Item>();
    // לכל שורה מוצגת: סוג, מקום ב-items (לפריט), ושם התיקייה (לתיקייה)
    private final List<Integer> rowKind = new ArrayList<Integer>();
    private final List<Integer> rowIdx = new ArrayList<Integer>();
    private final List<String> rowFolder = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle s) {
        ThemeUtil.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_list);
        ThemeUtil.forceRtl(this);

        mode = getIntent().getStringExtra(EX_MODE);
        if (mode == null) mode = Prefs.HISTORY;
        bookmarks = Prefs.BOOKMARKS.equals(mode);
        header = (TextView) findViewById(R.id.header);
        ((TextView) findViewById(R.id.hint)).setText(R.string.hint_list);
        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= rowKind.size()) return;
                int kind = rowKind.get(pos);
                if (kind == FOLDER) {
                    curFolder = rowFolder.get(pos);
                    render();
                } else if (kind == BACK) {
                    curFolder = "";
                    render();
                } else {
                    Prefs.Item it = items.get(rowIdx.get(pos));
                    Intent i = new Intent(ListScreenActivity.this, ReaderActivity.class)
                            .putExtra(ReaderActivity.EX_BOOK, it.book)
                            .putExtra(ReaderActivity.EX_PATH, it.path)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    if (bookmarks && it.y > 0) i.putExtra(ReaderActivity.EX_Y, it.y);
                    startActivity(i);
                    finish();
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                showOptions(pos);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeUtil.applyHintBar(this);
        render();
    }

    private void render() {
        items = Prefs.load(this, mode);
        rowKind.clear();
        rowIdx.clear();
        rowFolder.clear();
        List<String[]> rows = new ArrayList<String[]>();
        String title = getString(bookmarks ? R.string.bookmarks : R.string.history);
        if (bookmarks && curFolder.length() > 0) {
            title += " · " + curFolder;
            rows.add(new String[]{"« חזרה לכל הסימניות"});
            rowKind.add(BACK);
            rowIdx.add(-1);
            rowFolder.add("");
        }
        header.setText(title);
        if (bookmarks && curFolder.length() == 0) {
            for (String f : Prefs.bookmarkFolders(this)) {
                int n = 0;
                for (Prefs.Item it : items) if (f.equals(it.folder)) n++;
                rows.add(new String[]{"📁 " + f, n + " סימניות"});
                rowKind.add(FOLDER);
                rowIdx.add(-1);
                rowFolder.add(f);
            }
        }
        for (int i = 0; i < items.size(); i++) {
            Prefs.Item it = items.get(i);
            String f = it.folder == null ? "" : it.folder;
            if (bookmarks && !f.equals(curFolder)) continue;
            rows.add(new String[]{it.title.replace('_', ' '), Library.nameOf(it.book)});
            rowKind.add(ITEM);
            rowIdx.add(i);
            rowFolder.add("");
        }
        list.setAdapter(new RowAdapter(this, rows));
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (!rows.isEmpty()) {
            list.requestFocus();
            list.setSelection(0);
        }
    }

    private void showOptions(final int pos) {
        int kind = pos >= 0 && pos < rowKind.size() ? rowKind.get(pos) : -1;
        if (kind == BACK) return;
        final List<String> labels = new ArrayList<String>();
        final List<Runnable> acts = new ArrayList<Runnable>();
        if (kind == ITEM) {
            final int idx = rowIdx.get(pos);
            labels.add("מחק את הפריט");
            acts.add(new Runnable() {
                public void run() {
                    Prefs.remove(ListScreenActivity.this, mode, idx);
                    render();
                }
            });
            if (bookmarks) {
                labels.add("העבר לתיקייה…");
                acts.add(new Runnable() {
                    public void run() {
                        chooseFolder(idx);
                    }
                });
            }
        } else if (kind == FOLDER) {
            final String f = rowFolder.get(pos);
            labels.add("שנה שם תיקייה");
            acts.add(new Runnable() {
                public void run() {
                    askName("שם חדש לתיקייה", f, new Named() {
                        public void on(String name) {
                            Prefs.renameBookmarkFolder(ListScreenActivity.this, f, name);
                            render();
                        }
                    });
                }
            });
            labels.add("מחק תיקייה (הסימניות יחזרו לראשי)");
            acts.add(new Runnable() {
                public void run() {
                    Prefs.renameBookmarkFolder(ListScreenActivity.this, f, "");
                    render();
                }
            });
        }
        labels.add("נקה הכול");
        acts.add(new Runnable() {
            public void run() {
                Prefs.clear(ListScreenActivity.this, mode);
                curFolder = "";
                render();
            }
        });
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        acts.get(which).run();
                    }
                })
                .show();
    }

    private interface Named {
        void on(String name);
    }

    private void askName(String title, String initial, final Named done) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (n.length() > 0) done.on(n);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseFolder(final int idx) {
        final List<String> folders = Prefs.bookmarkFolders(this);
        List<String> labels = new ArrayList<String>();
        labels.add("ללא תיקייה (ראשי)");
        for (String f : folders) labels.add("📁 " + f);
        labels.add("＋ תיקייה חדשה…");
        new AlertDialog.Builder(this)
                .setTitle("העבר לתיקייה")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Prefs.setBookmarkFolder(ListScreenActivity.this, idx, "");
                            render();
                        } else if (which <= folders.size()) {
                            Prefs.setBookmarkFolder(ListScreenActivity.this, idx, folders.get(which - 1));
                            render();
                        } else {
                            askName("שם התיקייה החדשה", "", new Named() {
                                public void on(String name) {
                                    Prefs.setBookmarkFolder(ListScreenActivity.this, idx, name);
                                    render();
                                }
                            });
                        }
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (bookmarks && curFolder.length() > 0) {
            curFolder = "";
            render();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int a = Keys.map(ev);
        if (a == Keys.MENU) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) showOptions(list.getSelectedItemPosition());
            return true;
        }
        if (a == Keys.BACK && ev.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }
}
