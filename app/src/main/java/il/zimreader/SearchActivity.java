package il.zimreader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import il.zimreader.zim.FullText;
import il.zimreader.zim.ZimFile;

/**
 * חיפוש בספר הנוכחי או בכל הספרים. כותרות: השלמה תוך כדי הקלדה (עם התאמה עברית חכמה).
 * בתוכן: סריקה של כל המאמרים (איטית יותר) עם קטעי הקשר; מתחילה בלחיצה על אישור/חיפוש.
 */
public class SearchActivity extends Activity {
    private static final int MAX_RESULTS = 60;

    private ZimFile zim;
    private String bookPath;
    private EditText query;
    private ListView list;
    private TextView status;
    private CheckBox chkContent;
    private CheckBox chkAll;
    private final Handler handler = new Handler();
    private int seq;
    private volatile boolean cancel;
    private final List<ZimFile.Entry> titleResults = new ArrayList<ZimFile.Entry>();
    private final List<String> titleBooks = new ArrayList<String>();
    private final List<FullText.Hit> contentResults = new ArrayList<FullText.Hit>();
    private final List<String> contentBooks = new ArrayList<String>();
    private boolean showingContent;
    private boolean showingRecents;
    private final List<String> recents = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle s) {
        ThemeUtil.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_search);
        ThemeUtil.forceRtl(this);

        bookPath = getIntent().getStringExtra(ReaderActivity.EX_BOOK);
        try {
            zim = Library.open(bookPath);
        } catch (Throwable e) {
            finish();
            return;
        }
        query = (EditText) findViewById(R.id.query);
        list = (ListView) findViewById(R.id.list);
        status = (TextView) findViewById(R.id.status);
        chkContent = (CheckBox) findViewById(R.id.chkContent);
        chkAll = (CheckBox) findViewById(R.id.chkAll);
        if (Library.list().size() < 2) chkAll.setVisibility(View.GONE);

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (!chkContent.isChecked()) scheduleTitles(e.toString());
                else if (e.toString().trim().length() == 0) showRecents();
            }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                    submit();
                    return true;
                }
                return false;
            }
        });
        CompoundButton.OnCheckedChangeListener modeChanged = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                cancel = true;
                clearResults();
                if (!chkContent.isChecked()) {
                    scheduleTitles(query.getText().toString());
                } else {
                    status("הקלד מילים ולחץ אישור כדי לחפש בתוכן");
                    if (query.getText().toString().trim().length() == 0) showRecents();
                }
            }
        };
        chkContent.setOnCheckedChangeListener(modeChanged);
        chkAll.setOnCheckedChangeListener(modeChanged);
        RowAdapter.attach(list, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                openResult(pos);
            }
        });

        String q0 = getIntent().getStringExtra("q");
        if (q0 != null) query.setText(q0);
        else showRecents();
    }

    private void status(String text) {
        if (text == null) {
            status.setVisibility(View.GONE);
        } else {
            status.setText(text);
            status.setVisibility(View.VISIBLE);
        }
    }

    private void clearResults() {
        showingRecents = false;
        titleResults.clear();
        titleBooks.clear();
        contentResults.clear();
        contentBooks.clear();
        list.setAdapter(new RowAdapter(this, new ArrayList<String[]>()));
        status(null);
    }

    /** הספרים לחיפוש: הספר הנוכחי בלבד, או כל הספרים (הנוכחי ראשון). */
    private List<String> searchBooks() {
        List<String> out = new ArrayList<String>();
        out.add(bookPath);
        if (chkAll.isChecked()) {
            for (Library.Book b : Library.list()) {
                if (!b.path.equals(bookPath)) out.add(b.path);
            }
        }
        return out;
    }

    // ---------- חיפושים אחרונים ----------

    private void showRecents() {
        showingContent = false;
        showingRecents = true;
        titleResults.clear();
        titleBooks.clear();
        contentResults.clear();
        contentBooks.clear();
        recents.clear();
        recents.addAll(Prefs.searches(this));
        List<String[]> rows = new ArrayList<String[]>();
        for (String s : recents) rows.add(new String[]{s});
        list.setAdapter(new RowAdapter(this, rows, new RowAdapter.Deleter() {
            @Override
            public boolean canDelete(int position) {
                return true;
            }

            @Override
            public void delete(int position) {
                if (position >= 0 && position < recents.size()) {
                    Prefs.removeSearch(SearchActivity.this, recents.get(position));
                    showRecents();
                }
            }
        }));
        status(recents.isEmpty() ? null : "חיפושים אחרונים");
    }

    private void useRecent(int pos) {
        if (pos < 0 || pos >= recents.size()) return;
        String q = recents.get(pos);
        query.setText(q);
        query.setSelection(q.length());
        if (chkContent.isChecked()) runContentSearch(q);
    }

    private void submit() {
        if (chkContent.isChecked()) {
            Prefs.addSearch(this, query.getText().toString());
            runContentSearch(query.getText().toString());
        } else if (!titleResults.isEmpty()) {
            openResult(0);
        }
    }

    // ---------- חיפוש כותרות ----------

    private void scheduleTitles(final String q) {
        final int my = ++seq;
        handler.removeCallbacksAndMessages(null);
        final List<String> books = searchBooks();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final List<ZimFile.Entry> r = new ArrayList<ZimFile.Entry>();
                        final List<String> rb = new ArrayList<String>();
                        // בחיפוש בכמה ספרים: מנה מוגבלת לכל ספר, כדי שכולם ייוצגו
                        int perBook = books.size() == 1 ? MAX_RESULTS : Math.max(5, MAX_RESULTS / books.size());
                        for (String b : books) {
                            if (my != seq) return;
                            try {
                                for (ZimFile.Entry e : Library.open(b).suggest(q, perBook)) {
                                    if (r.size() >= MAX_RESULTS) break;
                                    r.add(e);
                                    rb.add(b);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (my == seq && !isFinishing() && !chkContent.isChecked()) showTitles(q, r, rb);
                            }
                        });
                    }
                }).start();
            }
        }, 200);
    }

    private void showTitles(String q, List<ZimFile.Entry> r, List<String> rb) {
        if (q.trim().length() == 0) {
            showRecents();
            return;
        }
        showingRecents = false;
        showingContent = false;
        titleResults.clear();
        titleResults.addAll(r);
        titleBooks.clear();
        titleBooks.addAll(rb);
        boolean multi = chkAll.isChecked();
        List<String[]> rows = new ArrayList<String[]>();
        for (int i = 0; i < r.size(); i++) {
            String title = ZimFile.displayTitle(r.get(i));
            rows.add(multi ? new String[]{title, Library.nameOf(rb.get(i))} : new String[]{title});
        }
        list.setAdapter(new RowAdapter(this, rows));
        status(r.isEmpty() ? getString(R.string.no_results) : null);
    }

    // ---------- חיפוש בתוכן ----------

    private void runContentSearch(final String q) {
        if (q.trim().length() == 0) return;
        final int my = ++seq;
        cancel = false;
        clearResults();
        status("מחפש בתוכן… 0%");
        final List<String> books = searchBooks();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<FullText.Hit> hits = new ArrayList<FullText.Hit>();
                final List<String> hitBooks = new ArrayList<String>();
                final int n = books.size();
                for (int i = 0; i < n && hits.size() < MAX_RESULTS; i++) {
                    final int idx = i;
                    final int found0 = hits.size();
                    try {
                        List<FullText.Hit> part = FullText.search(Library.open(books.get(i)), q,
                                MAX_RESULTS - hits.size(), new FullText.Listener() {
                            @Override
                            public boolean progress(final int percent, final int found) {
                                final int total = (idx * 100 + percent) / n;
                                final String name = n > 1 ? " · " + Library.nameOf(books.get(idx)) : "";
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (my == seq && !isFinishing()) {
                                            status("מחפש בתוכן… " + total + "%  (נמצאו " + (found0 + found) + ")" + name);
                                        }
                                    }
                                });
                                return cancel || my != seq;
                            }
                        });
                        for (FullText.Hit h : part) {
                            hits.add(h);
                            hitBooks.add(books.get(i));
                        }
                    } catch (Throwable ignored) {
                    }
                    if (cancel || my != seq) return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (my == seq && !isFinishing()) showContent(hits, hitBooks);
                    }
                });
            }
        }).start();
    }

    private void showContent(List<FullText.Hit> hits, List<String> hitBooks) {
        showingRecents = false;
        showingContent = true;
        contentResults.clear();
        contentResults.addAll(hits);
        contentBooks.clear();
        contentBooks.addAll(hitBooks);
        boolean multi = chkAll.isChecked();
        List<String[]> rows = new ArrayList<String[]>();
        for (int i = 0; i < hits.size(); i++) {
            FullText.Hit h = hits.get(i);
            rows.add(new String[]{multi ? h.title + "  ·  " + Library.nameOf(hitBooks.get(i)) : h.title, h.snippet});
        }
        list.setAdapter(new RowAdapter(this, rows));
        status(hits.isEmpty() ? getString(R.string.no_results) : "נמצאו " + hits.size() + " תוצאות");
        if (!hits.isEmpty()) list.requestFocus();
    }

    @Override
    protected void onDestroy() {
        cancel = true;
        super.onDestroy();
    }

    private void openResult(int pos) {
        if (showingRecents) {
            useRecent(pos);
            return;
        }
        Prefs.addSearch(this, query.getText().toString());
        String book;
        String path;
        String find = null;
        try {
            if (showingContent) {
                if (pos < 0 || pos >= contentResults.size()) return;
                book = contentBooks.get(pos);
                path = Library.open(book).fullPathOf(contentResults.get(pos).entry);
                String[] words = query.getText().toString().trim().split("\\s+");
                if (words.length > 0) find = words[0];
            } else {
                if (pos < 0 || pos >= titleResults.size()) return;
                book = titleBooks.get(pos);
                path = Library.open(book).fullPathOf(titleResults.get(pos));
            }
        } catch (Throwable e) {
            return;
        }
        Intent i = new Intent(this, ReaderActivity.class)
                .putExtra(ReaderActivity.EX_BOOK, book)
                .putExtra(ReaderActivity.EX_PATH, path)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (find != null) i.putExtra(ReaderActivity.EX_FIND, find);
        startActivity(i);
        finish();
    }
}
