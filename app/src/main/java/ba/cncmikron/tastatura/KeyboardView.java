package ba.cncmikron.tastatura;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tastatura nacrtana rucno: QWERTZ, kvacice na dugi pritisak, dva sloja znakova. */
public class KeyboardView extends View {

    public interface Listener {
        void onText(String text);
        void onDelete();
        void onEnter();
        void onSpace();
    }

    static final int T_CHAR = 0, T_SHIFT = 1, T_DEL = 2, T_MODE = 3, T_ENTER = 4, T_SPACE = 5;
    static final int LAYER_LETTERS = 0, LAYER_SYM1 = 1, LAYER_SYM2 = 2;

    static final class Key {
        final String label;
        final int type;
        final float weight;
        final String alts;   // znakovi na dugi pritisak
        final int target;    // za T_MODE: sloj
        final RectF rect = new RectF();

        Key(String label, int type, float weight, String alts, int target) {
            this.label = label;
            this.type = type;
            this.weight = weight;
            this.alts = alts;
            this.target = target;
        }
    }

    private static Key ch(String s, String alts) {
        return new Key(s, T_CHAR, 1f, alts, 0);
    }

    private static Key ch(String s) {
        return ch(s, null);
    }

    private static Key gap(float w) {
        return new Key("", -1, w, null, 0);
    }

    private final List<List<Key>> letters = new ArrayList<>();
    private final List<List<Key>> sym1 = new ArrayList<>();
    private final List<List<Key>> sym2 = new ArrayList<>();
    private List<List<Key>> rows;
    private int layer = LAYER_LETTERS;

    private boolean shift, capsLock;
    private long lastShiftTap;
    private String enterLabel = "↵";

    private Listener listener;
    private boolean vibrate = true;
    private float heightScale = 1f;

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private Key pressed;
    private int activePointer = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longPressShown;
    private final RectF popupRect = new RectF();
    private int popupIndex;
    private boolean repeating;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (pressed != null && pressed.alts != null) {
                longPressShown = true;
                popupIndex = 0;
                layoutPopup();
                haptic();
                invalidate();
            }
        }
    };

    private final Runnable repeatDelete = new Runnable() {
        @Override
        public void run() {
            if (pressed != null && pressed.type == T_DEL && listener != null) {
                repeating = true;
                listener.onDelete();
                handler.postDelayed(this, 60);
            }
        }
    };

    public KeyboardView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        buildLayers();
        rows = letters;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF2F2F2);
    }

    private void buildLayers() {
        // slova
        List<Key> r1 = new ArrayList<>();
        for (char c : "qwertzuiop".toCharArray()) r1.add(ch(String.valueOf(c), alt(c)));
        List<Key> r2 = new ArrayList<>();
        r2.add(gap(0.5f));
        for (char c : "asdfghjkl".toCharArray()) r2.add(ch(String.valueOf(c), alt(c)));
        r2.add(gap(0.5f));
        List<Key> r3 = new ArrayList<>();
        r3.add(new Key("⇧", T_SHIFT, 1.5f, null, 0));
        for (char c : "yxcvbnm".toCharArray()) r3.add(ch(String.valueOf(c), alt(c)));
        r3.add(new Key("⌫", T_DEL, 1.5f, null, 0));
        letters.add(r1);
        letters.add(r2);
        letters.add(r3);
        letters.add(bottomRow("?123", LAYER_SYM1));

        // znakovi 1
        sym1.add(row("1 2 3 4 5 6 7 8 9 0"));
        sym1.add(row("@ # € & _ - ( ) / +"));
        List<Key> s3 = new ArrayList<>();
        s3.add(new Key("=\\<", T_MODE, 1.5f, null, LAYER_SYM2));
        for (String s : "* \" ' : ; ! ?".split(" ")) s3.add(ch(s));
        s3.add(new Key("⌫", T_DEL, 1.5f, null, 0));
        sym1.add(s3);
        sym1.add(bottomRow("ABC", LAYER_LETTERS));

        // znakovi 2
        sym2.add(row("~ ` | • √ π ÷ × ° %"));
        sym2.add(row("[ ] { } < > ^ = $ \\"));
        List<Key> t3 = new ArrayList<>();
        t3.add(new Key("?123", T_MODE, 1.5f, null, LAYER_SYM1));
        for (String s : "„ “ … – § £ ©".split(" ")) t3.add(ch(s));
        t3.add(new Key("⌫", T_DEL, 1.5f, null, 0));
        sym2.add(t3);
        sym2.add(bottomRow("ABC", LAYER_LETTERS));
    }

    private static String alt(char c) {
        switch (c) {
            case 'c': return "čć";
            case 'z': return "ž";
            case 's': return "š";
            case 'd': return "đ";
            case 'e': return "€";
            default: return null;
        }
    }

    private static List<Key> row(String s) {
        List<Key> r = new ArrayList<>();
        for (String k : s.split(" ")) r.add(ch(k));
        return r;
    }

    private List<Key> bottomRow(String modeLabel, int target) {
        List<Key> r = new ArrayList<>();
        r.add(new Key(modeLabel, T_MODE, 1.5f, null, target));
        r.add(ch(",", ";:"));
        r.add(new Key("ijekavica", T_SPACE, 5f, null, 0));
        r.add(ch(".", "?!"));
        r.add(new Key("↵", T_ENTER, 1.5f, null, 0));
        return r;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setVibrate(boolean v) {
        vibrate = v;
    }

    public void setHeightScale(float s) {
        if (s != heightScale) {
            heightScale = s;
            requestLayout();
        }
    }

    public void setEnterLabel(String label) {
        enterLabel = label;
        invalidate();
    }

    public void setLayer(int l) {
        layer = l;
        rows = l == LAYER_LETTERS ? letters : l == LAYER_SYM1 ? sym1 : sym2;
        layoutKeys();
        invalidate();
    }

    public int getLayer() {
        return layer;
    }

    /** Automatsko veliko slovo (pocetak recenice); ne gasi Caps Lock. */
    public void setAutoShift(boolean on) {
        if (capsLock) return;
        if (shift != on) {
            shift = on;
            invalidate();
        }
    }

    public boolean isShifted() {
        return shift || capsLock;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (4 * 56 * dp * heightScale + 8 * dp);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys();
    }

    private void layoutKeys() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float pad = 3 * dp;
        float rowH = (h - 8 * dp) / 4f;
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0;
            for (Key k : row) total += k.weight;
            // sirina jednog tastera: kao da red ima 10 tastera (svi redovi isto poravnati)
            float unit = w / Math.max(10f, total);
            float x = (w - unit * total) / 2f;
            float y = 4 * dp + r * rowH;
            for (Key k : row) {
                k.rect.set(x + pad, y + pad, x + unit * k.weight - pad, y + rowH - pad);
                x += unit * k.weight;
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF1C1D20);
        float radius = 7 * dp;
        for (List<Key> row : rows) {
            for (Key k : row) {
                if (k.type < 0) continue;
                boolean special = k.type != T_CHAR && k.type != T_SPACE;
                int color = special ? 0xFF33363C : 0xFF4A4E56;
                if (k == pressed) color = 0xFF6B7180;
                if (k.type == T_SHIFT && capsLock) color = 0xFF2F6FD6;
                else if (k.type == T_SHIFT && shift) color = 0xFF5A6478;
                if (k.type == T_ENTER) color = k == pressed ? 0xFF5C8EE0 : 0xFF2F6FD6;
                keyPaint.setColor(color);
                c.drawRoundRect(k.rect, radius, radius, keyPaint);

                String label = labelOf(k);
                boolean small = k.type == T_SPACE || k.type == T_MODE || label.length() > 2;
                textPaint.setTextSize((small ? 14 : 22) * dp * Math.min(1.1f, heightScale));
                textPaint.setColor(k.type == T_SPACE ? 0xFF9AA0AA : 0xFFF2F2F2);
                float ty = k.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
                c.drawText(label, k.rect.centerX(), ty, textPaint);
                if (k.alts != null && k.type == T_CHAR && layer == LAYER_LETTERS && Character.isLetter(k.label.charAt(0))) {
                    textPaint.setTextSize(10 * dp);
                    textPaint.setColor(0xFF9AA0AA);
                    c.drawText(caseOf(k.alts.substring(0, 1)), k.rect.right - 7 * dp, k.rect.top + 12 * dp, textPaint);
                }
            }
        }
        if (longPressShown && pressed != null) drawPopup(c);
    }

    private String labelOf(Key k) {
        if (k.type == T_ENTER) return enterLabel;
        if (k.type == T_CHAR) return caseOf(k.label);
        return k.label;
    }

    private String caseOf(String s) {
        return isShifted() ? s.toUpperCase(Locale.ROOT) : s;
    }

    private void layoutPopup() {
        float kw = pressed.rect.width();
        float w = kw * pressed.alts.length();
        float left = Math.max(2 * dp, Math.min(pressed.rect.left, getWidth() - w - 2 * dp));
        float topY = Math.max(0, pressed.rect.top - pressed.rect.height() - 4 * dp);
        popupRect.set(left, topY, left + w, topY + pressed.rect.height());
    }

    private void drawPopup(Canvas c) {
        float kw = popupRect.width() / pressed.alts.length();
        popupPaint.setColor(0xFF2A2D33);
        c.drawRoundRect(popupRect, 8 * dp, 8 * dp, popupPaint);
        for (int i = 0; i < pressed.alts.length(); i++) {
            RectF r = new RectF(popupRect.left + i * kw, popupRect.top, popupRect.left + (i + 1) * kw, popupRect.bottom);
            if (i == popupIndex) {
                popupPaint.setColor(0xFF2F6FD6);
                c.drawRoundRect(r, 8 * dp, 8 * dp, popupPaint);
            }
            textPaint.setTextSize(22 * dp);
            textPaint.setColor(0xFFFFFFFF);
            float ty = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
            c.drawText(caseOf(pressed.alts.substring(i, i + 1)), r.centerX(), ty, textPaint);
        }
    }

    /** Taster najblizi tacki (promasaj izmedju tastera ide najblizem). */
    private Key keyAt(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : rows) {
            for (Key k : row) {
                if (k.type < 0) continue;
                float dx = Math.max(0, Math.max(k.rect.left - x, x - k.rect.right));
                float dy = Math.max(0, Math.max(k.rect.top - y, y - k.rect.bottom));
                float d = dx * dx + dy * dy * 2;
                if (d < bestD) {
                    bestD = d;
                    best = k;
                }
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                down(e.getPointerId(0), e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN: {
                // drugi prst prije nego sto je prvi pusten: zavrsi prvi taster odmah
                if (pressed != null && !longPressShown) release();
                int i = e.getActionIndex();
                down(e.getPointerId(i), e.getX(i), e.getY(i));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(activePointer);
                if (i < 0 || pressed == null) return true;
                float x = e.getX(i), y = e.getY(i);
                if (longPressShown) {
                    float kw = popupRect.width() / pressed.alts.length();
                    int idx = (int) ((x - popupRect.left) / kw);
                    idx = Math.max(0, Math.min(pressed.alts.length() - 1, idx));
                    if (idx != popupIndex) {
                        popupIndex = idx;
                        invalidate();
                    }
                } else if (pressed.type == T_CHAR) {
                    Key k = keyAt(x, y);
                    if (k != pressed && k != null && k.type == T_CHAR) {
                        handler.removeCallbacks(longPress);
                        pressed = k;
                        if (k.alts != null) handler.postDelayed(longPress, 350);
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (e.getPointerId(e.getActionIndex()) == activePointer) release();
                return true;
            }
            case MotionEvent.ACTION_UP:
                release();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancel();
                return true;
            default:
                return true;
        }
    }

    private void down(int pointer, float x, float y) {
        activePointer = pointer;
        pressed = keyAt(x, y);
        longPressShown = false;
        repeating = false;
        if (pressed == null) return;
        haptic();
        if (pressed.alts != null) handler.postDelayed(longPress, 350);
        if (pressed.type == T_DEL) handler.postDelayed(repeatDelete, 400);
        invalidate();
    }

    private void cancel() {
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(repeatDelete);
        pressed = null;
        longPressShown = false;
        activePointer = -1;
        invalidate();
    }

    private void release() {
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(repeatDelete);
        Key k = pressed;
        boolean popup = longPressShown;
        boolean rep = repeating;
        pressed = null;
        longPressShown = false;
        activePointer = -1;
        invalidate();
        if (k == null || listener == null) return;
        if (popup) {
            typeChar(k.alts.substring(popupIndex, popupIndex + 1));
            return;
        }
        switch (k.type) {
            case T_CHAR:
                typeChar(k.label);
                break;
            case T_SHIFT:
                long now = System.currentTimeMillis();
                if (capsLock) {
                    capsLock = false;
                    shift = false;
                } else if (shift && now - lastShiftTap < 400) {
                    capsLock = true;
                } else {
                    shift = !shift;
                }
                lastShiftTap = now;
                invalidate();
                break;
            case T_DEL:
                if (!rep) listener.onDelete();
                break;
            case T_MODE:
                setLayer(k.target);
                break;
            case T_ENTER:
                listener.onEnter();
                break;
            case T_SPACE:
                listener.onSpace();
                break;
            default:
                break;
        }
    }

    private void typeChar(String s) {
        String out = caseOf(s);
        if (shift && !capsLock && layer == LAYER_LETTERS && Character.isLetter(s.charAt(0))) {
            shift = false;
            invalidate();
        }
        listener.onText(out);
    }

    private void haptic() {
        if (vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }
}
