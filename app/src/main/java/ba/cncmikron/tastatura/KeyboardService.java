package ba.cncmikron.tastatura;

import android.graphics.Color;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ba.cncmikron.tastatura.engine.Corrector;

public class KeyboardService extends InputMethodService implements KeyboardView.Listener {

    private KeyboardView keyboard;
    private final TextView[] slots = new TextView[3];
    private UserStore store;

    /** Rijec koja se trenutno kuca (podvucena u polju). */
    private final StringBuilder composing = new StringBuilder();
    private boolean correctionAllowed;
    private int mode;

    /** Posljednja automatska ispravka, za ponistavanje Backspaceom. */
    private String lastOriginal, lastCorrected, lastSeparator;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new UserStore(this);
        Engine.get(this);
    }

    @Override
    public View onCreateInputView() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1C1D20);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            TextView tv = new TextView(this);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(0xFFE8E8E8);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            final int idx = i;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickSuggestion(idx);
                }
            });
            bar.addView(tv, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
            slots[i] = tv;
        }
        // srednji je onaj koji ce se primijeniti
        slots[1].setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        root.addView(keyboard, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        composing.setLength(0);
        clearLastCorrection();
        mode = store.correctionMode();
        keyboard.setVibrate(store.vibrate());
        keyboard.setHeightScale(store.heightPercent() / 100f);

        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean password = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean special = var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_URI
                || (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        correctionAllowed = cls == InputType.TYPE_CLASS_TEXT && !password && !special && mode != UserStore.MODE_OFF;

        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME) {
            keyboard.setLayer(KeyboardView.LAYER_SYM1);
        } else {
            keyboard.setLayer(KeyboardView.LAYER_LETTERS);
        }

        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multiLine = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        String label = "↵";
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && !multiLine) {
            if (action == EditorInfo.IME_ACTION_SEND) label = "➤";
            else if (action == EditorInfo.IME_ACTION_SEARCH) label = "⌕";
            else if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_NEXT) label = "→";
            else if (action == EditorInfo.IME_ACTION_DONE) label = "✓";
        }
        keyboard.setEnterLabel(label);
        updateShift();
        showSuggestions(new ArrayList<String>(), null);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        commitComposing(false, "");
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        // korisnik je pomjerio kursor negdje drugo: zaboravi rijec koja se kucala
        if (composing.length() > 0 && (newSelStart != candEnd || newSelEnd != candEnd)) {
            composing.setLength(0);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            showSuggestions(new ArrayList<String>(), null);
        }
    }

    // ---- tasteri ----

    @Override
    public void onText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        char c = text.charAt(0);
        if (Character.isLetter(c) && correctionAllowed) {
            clearLastCorrection();
            composing.append(text);
            ic.setComposingText(composing, 1);
            refreshSuggestions();
        } else if (Character.isLetter(c) || Character.isDigit(c)) {
            clearLastCorrection();
            if (composing.length() > 0 && Character.isDigit(c)) {
                // rijec sa brojem (G71): bez ispravke
                composing.append(text);
                ic.setComposingText(composing, 1);
                refreshSuggestions();
            } else {
                ic.commitText(text, 1);
            }
        } else {
            boolean correct = ".,!?;:".indexOf(c) >= 0;
            commitComposing(correct, text);
        }
        updateShift();
    }

    @Override
    public void onSpace() {
        commitComposing(true, " ");
        updateShift();
    }

    @Override
    public void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing(true, "");
        clearLastCorrection();
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multiLine = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && !multiLine
                && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            ic.commitText("\n", 1);
        }
        updateShift();
    }

    @Override
    public void onDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (lastCorrected != null && undoCorrection(ic)) {
            updateShift();
            return;
        }
        if (composing.length() > 0) {
            int n = Character.charCount(composing.codePointBefore(composing.length()));
            composing.setLength(composing.length() - n);
            ic.setComposingText(composing, 1);
            refreshSuggestions();
        } else {
            CharSequence sel = ic.getSelectedText(0);
            if (!TextUtils.isEmpty(sel)) ic.commitText("", 1);
            else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
        updateShift();
    }

    // ---- ispravka ----

    /** Zavrsava rijec (sa ili bez ispravke) i dodaje separator (razmak, zarez...). */
    private void commitComposing(boolean correct, String separator) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String word = composing.toString();
        composing.setLength(0);
        clearLastCorrection();
        if (word.isEmpty()) {
            if (!separator.isEmpty()) ic.commitText(separator, 1);
            showSuggestions(new ArrayList<String>(), null);
            return;
        }
        String fixed = null;
        Corrector c = Engine.get(this);
        if (correct && correctionAllowed && mode == UserStore.MODE_AUTO && c != null) {
            fixed = c.autoCorrect(word);
        }
        if (fixed != null && !fixed.equals(word)) {
            ic.commitText(fixed + separator, 1);
            lastOriginal = word;
            lastCorrected = fixed;
            lastSeparator = separator;
            c.learn(word, fixed);
            store.saveSubStats(c.subStats());
            // traka nudi vracanje originala
            List<String> l = new ArrayList<>();
            l.add("„" + word + "“");
            showSuggestions(l, null);
        } else {
            ic.commitText(word + separator, 1);
            showSuggestions(new ArrayList<String>(), null);
        }
    }

    /** Backspace odmah poslije ispravke vraca ono sto je otkucano. */
    private boolean undoCorrection(InputConnection ic) {
        String expected = lastCorrected + lastSeparator;
        CharSequence before = ic.getTextBeforeCursor(expected.length(), 0);
        if (before == null || !before.toString().equals(expected)) {
            clearLastCorrection();
            return false;
        }
        String original = lastOriginal;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(expected.length(), 0);
        ic.commitText(original + lastSeparator, 1);
        ic.endBatchEdit();
        rejected(original);
        clearLastCorrection();
        showSuggestions(new ArrayList<String>(), null);
        return true;
    }

    /** Dva puta ponistena ispravka iste rijeci = rijec ide u licni rjecnik. */
    private void rejected(String original) {
        if (store.addRejection(original) >= 2) {
            store.addWord(original);
            Corrector c = Engine.get(this);
            if (c != null) c.dictionary().add(original, Corrector.USER_WORD_FREQ);
        }
    }

    private void clearLastCorrection() {
        lastOriginal = lastCorrected = lastSeparator = null;
    }

    private void refreshSuggestions() {
        Corrector c = Engine.get(this);
        String w = composing.toString();
        if (c == null || w.isEmpty() || !correctionAllowed) {
            showSuggestions(new ArrayList<String>(), w.isEmpty() ? null : w);
            return;
        }
        showSuggestions(c.suggestions(w, 2), w);
    }

    /**
     * Raspored trake: lijevo = otkucano, sredina = najbolji prijedlog, desno = drugi prijedlog.
     */
    private void showSuggestions(List<String> s, String typed) {
        if (slots[0] == null) return;
        String left = "", mid = "", right = "";
        if (typed == null) {
            if (!s.isEmpty()) mid = s.get(0);
        } else {
            left = "„" + typed + "“";
            if (s.size() > 0) mid = s.get(0);
            if (s.size() > 1) right = s.get(1);
            if (mid.equals(typed)) left = "";
        }
        slots[0].setText(left);
        slots[1].setText(mid);
        slots[2].setText(right);
        slots[1].setTextColor(lastCorrected != null ? 0xFF8FB4FF : Color.WHITE);
    }

    private void pickSuggestion(int idx) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String text = slots[idx].getText().toString();
        if (text.isEmpty()) return;
        boolean quoted = text.startsWith("„") && text.endsWith("“");
        if (lastCorrected != null && quoted) {
            // vrati original poslije automatske ispravke
            undoCorrection(ic);
            return;
        }
        String typed = composing.toString();
        String word = quoted ? text.substring(1, text.length() - 1) : text;
        composing.setLength(0);
        ic.commitText(word + " ", 1);
        Corrector c = Engine.get(this);
        if (c != null) {
            if (quoted && !c.isKnown(word)) {
                // korisnik je namjerno ostavio svoju rijec: zapamti je
                store.addWord(word);
                c.dictionary().add(word, Corrector.USER_WORD_FREQ);
            } else if (!quoted && !typed.isEmpty()) {
                c.learn(typed, word);
                store.saveSubStats(c.subStats());
            }
        }
        clearLastCorrection();
        showSuggestions(new ArrayList<String>(), null);
        updateShift();
    }

    private void updateShift() {
        if (keyboard == null) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        boolean caps = false;
        if (ic != null && info != null && composing.length() == 0 && info.inputType != InputType.TYPE_NULL) {
            caps = ic.getCursorCapsMode(info.inputType) != 0;
        }
        keyboard.setAutoShift(caps);
    }
}
