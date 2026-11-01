package il.zimreader;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

/**
 * WebView שלא נחשב עורך טקסט (אין שדות הקלדה בעמודי ZIM), ושמקבל את המקשים לפני ה-IME.
 * במכשירי מקשים ה-IME יכול לבלוע מקשי ספרות כשה-WebView בפוקוס; כך הם תמיד מגיעים לאפליקציה.
 */
public class ZimWebView extends WebView {
    public interface PreImeHandler {
        boolean handle(KeyEvent e);
    }

    public interface ScrollListener {
        void onScroll(int y, int oldY);
    }

    private PreImeHandler handler;
    private ScrollListener scrollListener;

    public ZimWebView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setPreImeHandler(PreImeHandler h) {
        handler = h;
    }

    public void setScrollListener(ScrollListener l) {
        scrollListener = l;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (scrollListener != null) scrollListener.onScroll(t, oldt);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent e) {
        if (handler != null && handler.handle(e)) return true;
        return super.dispatchKeyEventPreIme(e);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return null;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return false;
    }
}
