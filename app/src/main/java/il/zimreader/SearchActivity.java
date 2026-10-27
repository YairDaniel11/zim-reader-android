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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import il.zimreader.zim.ZimFile;

/** חיפוש כותרות (תחילית) בספר הנוכחי, עם השלמה תוך כדי הקלדה. */
public class SearchActivity extends Activity {
    private ZimFile zim;
    private String bookPath;
    private EditText query;
    private ListView list;
    private TextView status;
    private final Handler handler = new Handler();
    private int seq;
    private final List<ZimFile.Entry> results = new ArrayList<ZimFile.Entry>();

    @Override
    protected void onCreate(Bundle s) {
        ThemeUtil.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_search);
        ThemeUtil.forceRtl(this);

        bookPath = getIntent().getStringExtra(ReaderActivity.EX_BOOK);
        try {
            zim = Library.open(bookPath);
        } catch (Exception e) {
            finish();
            return;
        }
        query = (EditText) findViewById(R.id.query);
        list = (ListView) findViewById(R.id.list);
        status = (TextView) findViewById(R.id.status);

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence c, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                schedule(e.toString());
            }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                    if (!results.isEmpty()) openResult(0);
                    return true;
                }
                return false;
            }
        });
        // אפשר לפתוח את החיפוש עם טקסט מוכן (שימושי גם לבדיקות)
        String q0 = getIntent().getStringExtra("q");
        if (q0 != null) query.setText(q0);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                openResult(pos);
            }
        });
    }

    private void schedule(final String q) {
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
                        } catch (Exception e) {
                            r = new ArrayList<ZimFile.Entry>();
                        }
                        final List<ZimFile.Entry> fr = r;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (my == seq && !isFinishing()) show(q, fr);
                            }
                        });
                    }
                }).start();
            }
        }, 200);
    }

    private void show(String q, List<ZimFile.Entry> r) {
        results.clear();
        results.addAll(r);
        List<String[]> rows = new ArrayList<String[]>();
        for (ZimFile.Entry e : r) rows.add(new String[]{ZimFile.displayTitle(e)});
        list.setAdapter(new RowAdapter(this, rows));
        if (r.isEmpty() && q.trim().length() > 0) {
            status.setText(R.string.no_results);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void openResult(int pos) {
        if (pos < 0 || pos >= results.size()) return;
        startActivity(new Intent(this, ReaderActivity.class)
                .putExtra(ReaderActivity.EX_BOOK, bookPath)
                .putExtra(ReaderActivity.EX_PATH, zim.fullPathOf(results.get(pos)))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
