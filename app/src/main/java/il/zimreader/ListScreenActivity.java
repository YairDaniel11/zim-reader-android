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

/** סימניות או היסטוריה. MENU / לחיצה ארוכה: מחיקה. */
public class ListScreenActivity extends Activity {
    public static final String EX_MODE = "mode";

    private String mode;
    private ListView list;
    private TextView empty;
    private List<Prefs.Item> items = new ArrayList<Prefs.Item>();

    @Override
    protected void onCreate(Bundle s) {
        ThemeUtil.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_list);
        ThemeUtil.forceRtl(this);

        mode = getIntent().getStringExtra(EX_MODE);
        if (mode == null) mode = Prefs.HISTORY;
        ((TextView) findViewById(R.id.header)).setText(
                Prefs.BOOKMARKS.equals(mode) ? R.string.bookmarks : R.string.history);
        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Prefs.Item it = items.get(pos);
                startActivity(new Intent(ListScreenActivity.this, ReaderActivity.class)
                        .putExtra(ReaderActivity.EX_BOOK, it.book)
                        .putExtra(ReaderActivity.EX_PATH, it.path)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                showDelete(pos);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        items = Prefs.load(this, mode);
        List<String[]> rows = new ArrayList<String[]>();
        for (Prefs.Item it : items) {
            rows.add(new String[]{it.title.replace('_', ' '), Library.nameOf(it.book)});
        }
        list.setAdapter(new RowAdapter(this, rows));
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (!rows.isEmpty()) list.requestFocus();
    }

    private void showDelete(final int pos) {
        final String[] opts = pos >= 0 ? new String[]{"מחק את הפריט", "נקה הכול"} : new String[]{"נקה הכול"};
        new AlertDialog.Builder(this)
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (pos >= 0 && which == 0) Prefs.remove(ListScreenActivity.this, mode, pos);
                        else Prefs.clear(ListScreenActivity.this, mode);
                        render();
                    }
                })
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showDelete(list.getSelectedItemPosition());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
