package ba.cncmikron.tastatura;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Podesavanja tastature: ukljucivanje, jacina ispravke, visina, licni rjecnik, statistika. */
public class MainActivity extends Activity {

    private UserStore store;
    private LinearLayout root;
    private TextView status;
    private LinearLayout wordsBox;
    private TextView stats;
    private float dp;
    private KeyboardView preview;
    private LinearLayout swatches;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new UserStore(this);
        dp = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * dp);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        setContentView(scroll);

        title("Pametna tastatura");
        text("Ispravlja slova koja prst promaši (prqvimo → pravimo) i dodaje kvačice (mozemo → možemo). Radi bez interneta.");

        section("Uključivanje");
        status = text("");
        button("1. Uključi tastaturu u postavkama", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        button("2. Izaberi Pametnu tastaturu", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showInputMethodPicker();
            }
        });

        section("Probaj ovdje");
        EditText test = new EditText(this);
        test.setHint("Kucaj npr. prqvimo, mozemo, telwfonu");
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        test.setMinLines(3);
        root.addView(test);

        buildLookSection();

        section("Jačina ispravke");
        RadioGroup rg = new RadioGroup(this);
        String[] names = {"Isključeno", "Samo prijedlozi", "Automatski (preporučeno)"};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(100 + i);
            rg.addView(rb);
        }
        rg.check(100 + store.correctionMode());
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                store.setCorrectionMode(id - 100);
            }
        });
        root.addView(rg);

        section("Visina tastature");
        final TextView hLabel = text(store.heightPercent() + " %");
        SeekBar sb = new SeekBar(this);
        sb.setMax(60);
        sb.setProgress(store.heightPercent() - 80);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                store.setHeightPercent(80 + v);
                hLabel.setText((80 + v) + " %  (veći tasteri = manje promašaja)");
                if (user) refreshPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(sb);

        CheckBox vib = new CheckBox(this);
        vib.setText("Vibracija na dodir");
        vib.setChecked(store.vibrate());
        vib.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton c, boolean on) {
                store.setVibrate(on);
            }
        });
        root.addView(vib);

        section("Lični rječnik");
        text("Ove riječi se nikad ne ispravljaju. Riječ se doda i sama kad dva puta poništiš njenu ispravku.");
        LinearLayout addRow = new LinearLayout(this);
        final EditText newWord = new EditText(this);
        newWord.setHint("nova riječ");
        newWord.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        addRow.addView(newWord, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(this);
        add.setText("Dodaj");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String w = newWord.getText().toString().trim();
                if (w.isEmpty() || w.contains(" ")) return;
                store.addWord(w);
                Engine.reset();
                newWord.setText("");
                refreshWords();
            }
        });
        addRow.addView(add);
        root.addView(addRow);
        wordsBox = new LinearLayout(this);
        wordsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(wordsBox);

        section("Tvoje najčešće greške");
        stats = text("");

        section("Brisanje");
        button("Obriši naučeno (lični rječnik i statistiku)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Obrisati lični rječnik i statistiku grešaka?")
                        .setPositiveButton("Obriši", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                store.clearLearned();
                                Engine.reset();
                                refreshWords();
                                refreshStats();
                            }
                        })
                        .setNegativeButton("Odustani", null)
                        .show();
            }
        });
        text("Tastatura nema dozvolu za internet: ništa što kucaš ne napušta telefon.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshWords();
        refreshStats();
    }

    private void refreshStatus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList()) {
            if (i.getPackageName().equals(getPackageName())) enabled = true;
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = current != null && current.startsWith(getPackageName() + "/");
        if (selected) status.setText("✔ Tastatura je uključena i izabrana.");
        else if (enabled) status.setText("✔ Uključena. Još je izaberi (korak 2).");
        else status.setText("Tastatura još nije uključena (korak 1).");
    }

    private void refreshWords() {
        wordsBox.removeAllViews();
        for (final String w : store.getWords()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(this);
            tv.setText(w);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button del = new Button(this);
            del.setText("Obriši");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.removeWord(w);
                    Engine.reset();
                    refreshWords();
                }
            });
            row.addView(del);
            wordsBox.addView(row);
        }
    }

    private void refreshStats() {
        Map<String, Integer> m = store.getSubStats();
        if (m.isEmpty()) {
            stats.setText("Još nema podataka. Kucaj malo pa pogledaj ponovo.");
            return;
        }
        int total = 0;
        for (int v : m.values()) total += v;
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, new java.util.Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, l.size()); i++) {
            String[] k = l.get(i).getKey().split(">");
            int pct = Math.round(100f * l.get(i).getValue() / total);
            sb.append("otkucaš ").append(k[0]).append(" umjesto ").append(k[1])
                    .append(":  ").append(l.get(i).getValue()).append(" puta (").append(pct).append(" %)\n");
        }
        stats.setText(sb.toString().trim());
    }

    // ---- izgled ----

    private void buildLookSection() {
        section("Izgled");
        text("Pregled (promjene se vide odmah):");
        preview = new KeyboardView(this);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.topMargin = (int) (6 * dp);
        root.addView(preview, pl);
        refreshPreview();

        subtitle("Tema");
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        swatches = new LinearLayout(this);
        hs.addView(swatches);
        root.addView(hs);
        buildSwatches();

        subtitle("Oblik tastera");
        choice(Theme.SHAPES, store.shape(), new Pick() {
            @Override
            public void pick(int i) {
                store.setShape(i);
            }
        });

        subtitle("Razmak između tastera");
        choice(Theme.GAP_NAMES, store.gap(), new Pick() {
            @Override
            public void pick(int i) {
                store.setGap(i);
            }
        });

        subtitle("Veličina slova");
        final TextView fLabel = text(store.fontPercent() + " %");
        SeekBar fb = new SeekBar(this);
        fb.setMax(50);
        fb.setProgress(store.fontPercent() - 80);
        fb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                store.setFontPercent(80 + v);
                fLabel.setText((80 + v) + " %");
                if (user) refreshPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(fb);

        check("Red sa brojevima iznad slova", store.numberRow(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                store.setNumberRow(on);
                refreshPreview();
            }
        });
        check("Uvećano slovo pri pritisku", store.keyPreview(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                store.setKeyPreview(on);
                refreshPreview();
            }
        });
        check("Prikaži kvačice na tasterima (č, ž, š, đ)", store.altHints(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                store.setAltHints(on);
                refreshPreview();
            }
        });
        check("Sjenke ispod tastera", store.shadows(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                store.setShadows(on);
                refreshPreview();
            }
        });
    }

    private void refreshPreview() {
        if (preview != null) store.applyLook(preview);
    }

    private void buildSwatches() {
        swatches.removeAllViews();
        int sel = store.theme();
        for (int i = 0; i < Theme.ALL.length; i++) {
            final int idx = i;
            Theme t = Theme.ALL[i];
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            int pd = (int) (6 * dp);
            tile.setPadding(pd, pd, pd, pd);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(t.bg);
            bg.setCornerRadius(12 * dp);
            bg.setStroke((int) ((i == sel ? 3 : 1) * dp), i == sel ? t.accent : 0x40808080);
            LinearLayout box = new LinearLayout(this);
            box.setBackground(bg);
            box.setGravity(Gravity.CENTER);
            int bp = (int) (8 * dp);
            box.setPadding(bp, bp, bp, bp);
            box.addView(dot(t.key), dotParams());
            box.addView(dot(t.keySpecial), dotParams());
            box.addView(dot(t.accent), dotParams());
            tile.addView(box, new LinearLayout.LayoutParams((int) (84 * dp), (int) (48 * dp)));

            TextView name = new TextView(this);
            name.setText(t.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            name.setGravity(Gravity.CENTER);
            if (i == sel) name.setTypeface(Typeface.DEFAULT_BOLD);
            tile.addView(name);

            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.setTheme(idx);
                    buildSwatches();
                    refreshPreview();
                }
            });
            swatches.addView(tile);
        }
    }

    private View dot(int color) {
        View v = new View(this);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(4 * dp);
        v.setBackground(d);
        return v;
    }

    private LinearLayout.LayoutParams dotParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (16 * dp), (int) (20 * dp));
        lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
        return lp;
    }

    private interface Pick {
        void pick(int i);
    }

    private void choice(String[] names, int selected, final Pick p) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(500 + i);
            rb.setPadding(0, 0, (int) (8 * dp), 0);
            g.addView(rb);
        }
        g.check(500 + selected);
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup gr, int id) {
                p.pick(id - 500);
                refreshPreview();
            }
        });
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(g);
        root.addView(hs);
    }

    private void check(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setChecked(on);
        cb.setOnCheckedChangeListener(l);
        root.addView(cb);
    }

    private void subtitle(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int) (12 * dp), 0, (int) (4 * dp));
        root.addView(tv);
    }

    // ---- pomocne ----

    private void title(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tv);
    }

    private void section(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int) (22 * dp), 0, (int) (6 * dp));
        root.addView(tv);
    }

    private TextView text(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(tv);
        return tv;
    }

    private void button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b);
    }
}
