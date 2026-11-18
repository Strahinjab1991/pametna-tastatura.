package ba.cncmikron.tastatura;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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

    private final List<List<Key>> lettersBase = new ArrayList<>();
    private final List<List<Key>> letters = new ArrayList<>();
    private List<Key> numRow;
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

    // izgled
    private Theme theme = Theme.ALL[0];
    private int shape = Theme.SHAPE_ROUND;
    private float gapDp = 3f;
    private float fontScale = 1f;
    private boolean numberRow, keyPreview = true, altHints = true, shadows = true;
    private final RectF tmp = new RectF();

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
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        rebuildLetters();
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
        lettersBase.add(r1);
        lettersBase.add(r2);
        lettersBase.add(r3);
        lettersBase.add(bottomRow("?123", LAYER_SYM1));
        numRow = row("1 2 3 4 5 6 7 8 9 0");

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
        r.add(new Key("Pametna", T_SPACE, 5f, null, 0));
        r.add(ch(".", "?!"));
        r.add(new Key("↵", T_ENTER, 1.5f, null, 0));
        return r;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private void rebuildLetters() {
        letters.clear();
        if (numberRow) letters.add(numRow);
        letters.addAll(lettersBase);
    }

    /** Postavi izgled (tema, oblik, razmak, slova, red sa brojevima...). */
    public void setLook(Theme t, int shape, float gapDp, float fontScale, boolean numberRow,
                        boolean keyPreview, boolean altHints, boolean shadows) {
        this.theme = t;
        this.shape = shape;
        this.gapDp = gapDp;
        this.fontScale = fontScale;
        this.keyPreview = keyPreview;
        this.altHints = altHints;
        this.shadows = shadows;
        if (this.numberRow != numberRow) {
            this.numberRow = numberRow;
            rebuildLetters();
            if (layer == LAYER_LETTERS) rows = letters;
            requestLayout();
        }
        layoutKeys();
        invalidate();
    }

    public Theme getTheme() {
        return theme;
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

    /** Visina reda sa brojevima u odnosu na obican red. */
    private static final float NUM_ROW_W = 0.8f;

    private float rowUnits() {
        return 4f + (numberRow ? NUM_ROW_W : 0f);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (rowUnits() * 56 * dp * heightScale + 10 * dp);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys();
    }

    private void layoutKeys() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float pad = gapDp * dp;
        boolean num = rows == letters && numberRow;
        float units = num ? rowUnits() : 4f;
        float unitH = (h - 10 * dp) / units;
        float side = 2 * dp;
        float y = 5 * dp;
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float rowH = unitH * (num && r == 0 ? NUM_ROW_W : 1f);
            float total = 0;
            for (Key k : row) total += k.weight;
            // sirina jednog tastera: kao da red ima 10 tastera (svi redovi isto poravnati)
            float unit = (w - 2 * side) / Math.max(10f, total);
            float x = side + (w - 2 * side - unit * total) / 2f;
            for (Key k : row) {
                k.rect.set(x + pad, y + pad, x + unit * k.weight - pad, y + rowH - pad);
                x += unit * k.weight;
            }
            y += rowH;
        }
    }

    private float radiusFor(RectF r) {
        switch (shape) {
            case Theme.SHAPE_SQUARE: return 2 * dp;
            case Theme.SHAPE_PILL: return Math.min(r.width(), r.height()) / 2f;
            default: return 8 * dp;
        }
    }

    private int keyColor(Key k) {
        Theme t = theme;
        if (k.type == T_ENTER) return k == pressed ? t.accentPressed : t.accent;
        if (k.type == T_SHIFT && capsLock) return t.accent;
        if (k == pressed) return t.keyPressed;
        if (k.type == T_SHIFT && shift) return t.keyPressed;
        boolean special = k.type != T_CHAR && k.type != T_SPACE;
        return special ? t.keySpecial : t.key;
    }

    /** Je li boja svijetla (za izbor boje sjenke i teksta na akcentu). */
    private static boolean light(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return r * 299 + g * 587 + b * 114 > 150000;
    }

    @Override
    protected void onDraw(Canvas c) {
        Theme t = theme;
        c.drawColor(t.bg);
        boolean flat = shape == Theme.SHAPE_FLAT;
        for (List<Key> row : rows) {
            for (Key k : row) {
                if (k.type < 0) continue;
                int color = keyColor(k);
                float radius = radiusFor(k.rect);
                boolean filled = !flat || k.type == T_ENTER || k == pressed
                        || (k.type == T_SHIFT && (shift || capsLock));
                if (filled) {
                    if (shadows && !flat) {
                        // sjenka: tamnija kopija tastera malo nize
                        keyPaint.setColor(light(t.bg) ? 0x33000000 : 0x66000000);
                        tmp.set(k.rect.left, k.rect.top + 1.5f * dp, k.rect.right, k.rect.bottom + 1.5f * dp);
                        c.drawRoundRect(tmp, radius, radius, keyPaint);
                    }
                    keyPaint.setColor(color);
                    c.drawRoundRect(k.rect, radius, radius, keyPaint);
                    if (t.border != 0 && !flat && k.type != T_ENTER) {
                        keyPaint.setStyle(Paint.Style.STROKE);
                        keyPaint.setStrokeWidth(1 * dp);
                        keyPaint.setColor(t.border);
                        c.drawRoundRect(k.rect, radius, radius, keyPaint);
                        keyPaint.setStyle(Paint.Style.FILL);
                    }
                }

                String label = labelOf(k);
                boolean small = k.type == T_SPACE || k.type == T_MODE || label.length() > 2;
                float size = small ? 14 : 21;
                if (k.type == T_SHIFT || k.type == T_DEL || k.type == T_ENTER) size = 20;
                textPaint.setTextSize(size * dp * fontScale * Math.min(1.1f, heightScale));
                int tc = k.type == T_SPACE ? t.textDim : t.text;
                if (k.type == T_ENTER || (k.type == T_SHIFT && capsLock)) tc = light(t.accent) ? 0xFF000000 : 0xFFFFFFFF;
                textPaint.setColor(tc);
                float ty = k.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
                c.drawText(label, k.rect.centerX(), ty, textPaint);
                if (altHints && k.alts != null && k.type == T_CHAR && layer == LAYER_LETTERS
                        && Character.isLetter(k.label.charAt(0))) {
                    textPaint.setTextSize(10 * dp * fontScale);
                    textPaint.setColor(t.textDim);
                    c.drawText(caseOf(k.alts.substring(0, 1)), k.rect.right - 7 * dp, k.rect.top + 12 * dp, textPaint);
                }
            }
        }
        if (longPressShown && pressed != null) drawPopup(c);
        else if (keyPreview && pressed != null && pressed.type == T_CHAR) drawPreview(c);
    }

    private String labelOf(Key k) {
        if (k.type == T_ENTER) return enterLabel;
        if (k.type == T_CHAR) return caseOf(k.label);
        if (k.type == T_SHIFT) return capsLock ? "⇪" : "⇧";
        return k.label;
    }

    private String caseOf(String s) {
        return isShifted() ? s.toUpperCase(Locale.ROOT) : s;
    }

    /** Uvecano slovo iznad prsta dok je taster pritisnut. */
    private void drawPreview(Canvas c) {
        RectF k = pressed.rect;
        float w = k.width() * 1.35f, h = k.height() * 1.25f;
        float left = Math.max(2 * dp, Math.min(k.centerX() - w / 2, getWidth() - w - 2 * dp));
        float top = k.top - h - 4 * dp;
        if (top < 0) {
            // nema mjesta iznad (gornji red): uvecan taster preko samog tastera
            top = 0;
            h = Math.max(h, k.bottom + 2 * dp);
        }
        tmp.set(left, top, left + w, top + h);
        drawBubble(c, tmp);
        textPaint.setTextSize(30 * dp * fontScale);
        textPaint.setColor(theme.text);
        float ty = tmp.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
        c.drawText(caseOf(pressed.label), tmp.centerX(), ty, textPaint);
    }

    private void drawBubble(Canvas c, RectF r) {
        float rad = shape == Theme.SHAPE_SQUARE ? 3 * dp : 10 * dp;
        popupPaint.setColor(light(theme.bg) ? 0x40000000 : 0x80000000);
        tmp2.set(r.left, r.top + 2 * dp, r.right, r.bottom + 2 * dp);
        c.drawRoundRect(tmp2, rad, rad, popupPaint);
        popupPaint.setColor(theme.popup);
        c.drawRoundRect(r, rad, rad, popupPaint);
        if (theme.border != 0) {
            popupPaint.setStyle(Paint.Style.STROKE);
            popupPaint.setStrokeWidth(1 * dp);
            popupPaint.setColor(theme.border);
            c.drawRoundRect(r, rad, rad, popupPaint);
            popupPaint.setStyle(Paint.Style.FILL);
        }
    }

    private final RectF tmp2 = new RectF();

    private void layoutPopup() {
        float kw = pressed.rect.width() * 1.1f;
        float w = kw * pressed.alts.length();
        float left = Math.max(2 * dp, Math.min(pressed.rect.centerX() - kw / 2, getWidth() - w - 2 * dp));
        float h = pressed.rect.height() * 1.15f;
        float topY = Math.max(0, pressed.rect.top - h - 4 * dp);
        popupRect.set(left, topY, left + w, topY + h);
    }

    private void drawPopup(Canvas c) {
        float kw = popupRect.width() / pressed.alts.length();
        drawBubble(c, popupRect);
        float in = 3 * dp;
        for (int i = 0; i < pressed.alts.length(); i++) {
            RectF r = new RectF(popupRect.left + i * kw + in, popupRect.top + in,
                    popupRect.left + (i + 1) * kw - in, popupRect.bottom - in);
            boolean sel = i == popupIndex;
            if (sel) {
                float rad = shape == Theme.SHAPE_SQUARE ? 2 * dp : 8 * dp;
                popupPaint.setColor(theme.accent);
                c.drawRoundRect(r, rad, rad, popupPaint);
            }
            textPaint.setTextSize(24 * dp * fontScale);
            textPaint.setColor(sel ? (light(theme.accent) ? 0xFF000000 : 0xFFFFFFFF) : theme.text);
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
