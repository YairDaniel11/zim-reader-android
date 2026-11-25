package il.zimreader;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** חלון שנפתח מלמטה עם כותרת ורשימת פריטים (במקום דיאלוג אמצעי). */
public final class BottomSheet {
    private BottomSheet() {}

    public static Dialog show(Activity a, String title, String[] items,
                              final AdapterView.OnItemClickListener click,
                              AdapterView.OnItemLongClickListener longClick) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Holo_Light_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        float dp = a.getResources().getDisplayMetrics().density;

        TypedValue tv = new TypedValue();
        a.getTheme().resolveAttribute(R.attr.zrText, tv, true);
        final int textColor = tv.data;
        a.getTheme().resolveAttribute(R.attr.zrTextSecondary, tv, true);
        int subColor = tv.data;

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        // רקע מעוגל בצבע השורות של ערכת הנושא (?attr בתוך drawable לא נתמך לפני API 21)
        a.getTheme().resolveAttribute(R.attr.zrRow, tv, true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(tv.data);
        float r = 16 * dp;
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackgroundDrawable(bg);
        if (android.os.Build.VERSION.SDK_INT >= 17) root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // ידית קטנה
        View grip = new View(a);
        grip.setBackgroundColor((subColor & 0x00FFFFFF) | 0x66000000);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (4 * dp));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = (int) (8 * dp);
        root.addView(grip, gp);

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextColor(textColor);
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));
        root.addView(t);

        ListView lv = new ListView(a);
        lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int pos, View c, ViewGroup parent) {
                TextView v = (TextView) super.getView(pos, c, parent);
                v.setTextColor(textColor);
                v.setTextSize(16);
                v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                return v;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                d.dismiss();
                click.onItemClick(p, v, pos, id);
            }
        });
        if (longClick != null) lv.setOnItemLongClickListener(longClick);
        root.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.setContentView(root);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.setWindowAnimations(R.style.SheetAnim);
        int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.7f);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h);
        d.show();
        return d;
    }
}
