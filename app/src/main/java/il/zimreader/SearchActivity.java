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
 * חיפוש בספר הנוכחי. כותרות: השלמה תוך כדי הקלדה (עם התאמה עברית חכמה).
 * בתוכן: סריקה של כל המאמרים (איטית יותר) עם קטעי הקשר; מתחילה בלחיצה על אישור/חיפוש.
 */
public class SearchActivity extends Activity {
    private ZimFile zim;
    private String bookPath;
    private EditText query;
    private ListView list;
    private TextView status;
    private CheckBox chkContent;
    private final Handler handler = new Handler();
    private int seq;
    private volatile boolean cancel;
    private final List<ZimFile.Entry> titleResults = new ArrayList<ZimFile.Entry>();
    private final List<FullText.Hit> contentResults = new ArrayList<FullText.Hit>();
    private boolean showingContent;

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
        chkContent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                cancel = true;
                clearResults();
                if (!on) scheduleTitles(query.getText().toString());
                else status("הקלד מילים ולחץ אישור כדי לחפש בתוכן");
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                openResult(pos);
            }
        });

        String q0 = getIntent().getStringExtra("q");
        if (q0 != null) query.setText(q0);
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
        titleResults.clear();
        contentResults.clear();
        list.setAdapter(new RowAdapter(this, new ArrayList<String[]>()));
        status(null);
    }

    private void submit() {
        if (chkContent.isChecked()) {
            runContentSearch(query.getText().toString());
        } else if (!titleResults.isEmpty()) {
            openResult(0);
        }
    }

    // ---------- חיפוש כותרות ----------

    private void scheduleTitles(final String q) {
        final int my = ++seq;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<ZimFile.Entry> r;
                        try {
                            r = zim.suggest(q, 60);
                        } catch (Throwable e) {
                            r = new ArrayList<ZimFile.Entry>();
                        }
                        final List<ZimFile.Entry> fr = r;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (my == seq && !isFinishing() && !chkContent.isChecked()) showTitles(q, fr);
                            }
                        });
                    }
                }).start();
            }
        }, 200);
    }

    private void showTitles(String q, List<ZimFile.Entry> r) {
        showingContent = false;
        titleResults.clear();
        titleResults.addAll(r);
        List<String[]> rows = new ArrayList<String[]>();
        for (ZimFile.Entry e : r) rows.add(new String[]{ZimFile.displayTitle(e)});
        list.setAdapter(new RowAdapter(this, rows));
        status(r.isEmpty() && q.trim().length() > 0 ? getString(R.string.no_results) : null);
    }

    // ---------- חיפוש בתוכן ----------

    private void runContentSearch(final String q) {
        if (q.trim().length() == 0) return;
        final int my = ++seq;
        cancel = false;
        clearResults();
        status("מחפש בתוכן… 0%");
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<FullText.Hit> hits;
                try {
                    hits = FullText.search(zim, q, 60, new FullText.Listener() {
                        @Override
                        public boolean progress(final int percent, final int found) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (my == seq && !isFinishing()) {
                                        status("מחפש בתוכן… " + percent + "%  (נמצאו " + found + ")");
                                    }
                                }
                            });
                            return cancel || my != seq;
                        }
                    });
                } catch (Throwable e) {
                    hits = new ArrayList<FullText.Hit>();
                }
                final List<FullText.Hit> fh = hits;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (my == seq && !isFinishing()) showContent(fh);
                    }
                });
            }
        }).start();
    }

    private void showContent(List<FullText.Hit> hits) {
        showingContent = true;
        contentResults.clear();
        contentResults.addAll(hits);
        List<String[]> rows = new ArrayList<String[]>();
        for (FullText.Hit h : hits) rows.add(new String[]{h.title, h.snippet});
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
        String path;
        String find = null;
        if (showingContent) {
            if (pos < 0 || pos >= contentResults.size()) return;
            path = zim.fullPathOf(contentResults.get(pos).entry);
            String[] words = query.getText().toString().trim().split("\\s+");
            if (words.length > 0) find = words[0];
        } else {
            if (pos < 0 || pos >= titleResults.size()) return;
            path = zim.fullPathOf(titleResults.get(pos));
        }
        Intent i = new Intent(this, ReaderActivity.class)
                .putExtra(ReaderActivity.EX_BOOK, bookPath)
                .putExtra(ReaderActivity.EX_PATH, path)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (find != null) i.putExtra(ReaderActivity.EX_FIND, find);
        startActivity(i);
        finish();
    }
}
