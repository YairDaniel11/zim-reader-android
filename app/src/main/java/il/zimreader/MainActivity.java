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

/** מסך הספרייה: רשימת קבצי ה-ZIM שנמצאו. */
public class MainActivity extends Activity {
    private ListView list;
    private TextView empty;
    private TextView header;
    private boolean asked;
    private boolean scanning;
    private boolean scannedOnce;
    private List<Library.Book> shown = new ArrayList<Library.Book>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeUtil.forceRtl(this);

        header = (TextView) findViewById(R.id.header);
        header.setText(R.string.library);
        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);

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
    }

    private void openList(String mode) {
        startActivity(new Intent(this, ListScreenActivity.class).putExtra(ListScreenActivity.EX_MODE, mode));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionUtil.hasStorage(this)) {
            if (!scannedOnce) startScan();
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
                Library.scan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        scannedOnce = true;
                        if (!isFinishing()) render();
                    }
                });
            }
        }).start();
    }

    private void render() {
        shown = Library.list();
        List<String[]> rows = new ArrayList<String[]>();
        for (Library.Book b : shown) {
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
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startScan();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
