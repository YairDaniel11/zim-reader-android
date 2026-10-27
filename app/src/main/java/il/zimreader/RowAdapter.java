package il.zimreader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/** שורת רשימה פשוטה: כותרת + שורת משנה (מוסתרת כשריקה). */
public class RowAdapter extends ArrayAdapter<String[]> {
    public RowAdapter(Context c, List<String[]> rows) {
        super(c, 0, rows);
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
        return v;
    }
}
