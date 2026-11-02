package il.zimreader;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * שורת רשימה פשוטה: כותרת + שורת משנה (מוסתרת כשריקה), ואופציונלית סמל X למחיקת השורה.
 * ה-X הוא רק תצוגה (לא רכיב לחיץ, כדי לא להפריע ללחיצה על השורה ולמקשים); את המחיקה מפעיל
 * {@link #attach}: נגיעה באזור ה-X, או במקשים: חץ שמאלה מעביר את המיקוד ל-X ואישור מוחק.
 */
public class RowAdapter extends ArrayAdapter<String[]> {
    public interface Deleter {
        boolean canDelete(int position);

        void delete(int position);
    }

    private final Deleter deleter;

    public RowAdapter(Context c, List<String[]> rows) {
        this(c, rows, null);
    }

    public RowAdapter(Context c, List<String[]> rows, Deleter deleter) {
        super(c, 0, rows);
        this.deleter = deleter;
    }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
        View v = convert != null ? convert
                : LayoutInflater.from(getContext()).inflate(R.layout.row_item, parent, false);
        String[] row = getItem(position);
        ((TextView) v.findViewById(R.id.rowTitle)).setText(row[0]);
        TextView sub = (TextView) v.findViewById(R.id.rowSub);
        if (row.length > 1 && row[1] != null && row[1].length() > 0) {
            sub.setText(row[1]);
            sub.setVisibility(View.VISIBLE);
        } else {
            sub.setVisibility(View.GONE);
        }
        v.findViewById(R.id.rowDelete).setVisibility(
                deleter != null && deleter.canDelete(position) ? View.VISIBLE : View.GONE);
        return v;
    }

    private static boolean canDelete(ListView lv, int pos) {
        if (!(lv.getAdapter() instanceof RowAdapter) || pos < 0) return false;
        RowAdapter a = (RowAdapter) lv.getAdapter();
        return a.deleter != null && pos < a.getCount() && a.deleter.canDelete(pos);
    }

    private static boolean tryDelete(ListView lv, int pos) {
        if (!canDelete(lv, pos)) return false;
        ((RowAdapter) lv.getAdapter()).deleter.delete(pos);
        return true;
    }

    /**
     * מחבר לרשימה לחיצה על שורה + מחיקה. במגע: נגיעה ב-X. במקשים: חץ שמאלה על שורה נבחרת מעביר את
     * המיקוד ל-X (מסומן במסגרת), אישור מוחק, וכל חץ אחר חוזר לרשימה.
     */
    public static void attach(final ListView lv, final AdapterView.OnItemClickListener click) {
        final boolean[] touched = {false};
        final float[] downX = {0};
        final int[] xPos = {-1};
        final View[] xView = {null};
        final float pad = 8 * lv.getResources().getDisplayMetrics().density;
        lv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    touched[0] = true;
                    downX[0] = e.getX();
                }
                return false;
            }
        });
        lv.setOnKeyListener(new View.OnKeyListener() {
            private void leaveX() {
                if (xView[0] != null) xView[0].setBackgroundResource(0);
                xView[0] = null;
                xPos[0] = -1;
            }

            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
                if (e.getAction() != KeyEvent.ACTION_DOWN) return xPos[0] >= 0 && ok;
                touched[0] = false;
                if (xPos[0] >= 0) {
                    int p = xPos[0];
                    leaveX();
                    if (ok) {
                        tryDelete(lv, p);
                        return true;
                    }
                    return code == KeyEvent.KEYCODE_DPAD_RIGHT;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    int pos = lv.getSelectedItemPosition();
                    View row = lv.getSelectedView();
                    View x = row == null ? null : row.findViewById(R.id.rowDelete);
                    if (x != null && canDelete(lv, pos)) {
                        xView[0] = x;
                        xPos[0] = pos;
                        x.setBackgroundResource(R.drawable.bar_focus);
                        return true;
                    }
                }
                return false;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                boolean t = touched[0];
                touched[0] = false;
                if (t) {
                    View del = v.findViewById(R.id.rowDelete);
                    float x = downX[0] - v.getLeft();
                    if (del != null && del.getVisibility() == View.VISIBLE
                            && x >= del.getLeft() - pad && x <= del.getRight() + pad && tryDelete(lv, pos)) {
                        return;
                    }
                }
                click.onItemClick(p, v, pos, id);
            }
        });
    }
}
