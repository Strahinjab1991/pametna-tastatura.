package ba.cncmikron.tastatura;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ba.cncmikron.tastatura.engine.Corrector;

/** Podesavanja i naucene stvari; sve ostaje lokalno na telefonu. */
public class UserStore {
    public static final int MODE_OFF = 0;
    public static final int MODE_SUGGEST = 1;
    public static final int MODE_AUTO = 2;

    private static final String SEED_WORDS = "fanuc,g71,g70,g72,mikronko,cnc,mikron";

    private final SharedPreferences prefs;

    public UserStore(Context ctx) {
        prefs = ctx.getSharedPreferences("tastatura", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("seeded", false)) {
            Set<String> s = new HashSet<>(getWords());
            Collections.addAll(s, SEED_WORDS.split(","));
            prefs.edit().putStringSet("words", s).putBoolean("seeded", true).apply();
        }
    }

    public int correctionMode() {
        return prefs.getInt("mode", MODE_AUTO);
    }

    public void setCorrectionMode(int m) {
        prefs.edit().putInt("mode", m).apply();
    }

    /** Visina tastature u procentima (80-140). */
    public int heightPercent() {
        return prefs.getInt("height", 100);
    }

    public void setHeightPercent(int p) {
        prefs.edit().putInt("height", p).apply();
    }

    public boolean vibrate() {
        return prefs.getBoolean("vibrate", true);
    }

    public void setVibrate(boolean v) {
        prefs.edit().putBoolean("vibrate", v).apply();
    }

    public List<String> getWords() {
        List<String> l = new ArrayList<>(prefs.getStringSet("words", new HashSet<String>()));
        Collections.sort(l);
        return l;
    }

    public void addWord(String w) {
        Set<String> s = new HashSet<>(prefs.getStringSet("words", new HashSet<String>()));
        s.add(w.toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet("words", s).apply();
    }

    public void removeWord(String w) {
        Set<String> s = new HashSet<>(prefs.getStringSet("words", new HashSet<String>()));
        s.remove(w.toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet("words", s).apply();
    }

    /** Broji koliko puta je korisnik ponistio ispravku iste rijeci; vraca novi broj. */
    public int addRejection(String w) {
        String key = "rej_" + w.toLowerCase(Locale.ROOT);
        int n = prefs.getInt(key, 0) + 1;
        prefs.edit().putInt(key, n).apply();
        return n;
    }

    public Map<String, Integer> getSubStats() {
        Map<String, Integer> m = new HashMap<>();
        String s = prefs.getString("subs", "");
        for (String part : s.split(";")) {
            int c = part.lastIndexOf(':');
            if (c <= 0) continue;
            try {
                m.put(part.substring(0, c), Integer.parseInt(part.substring(c + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return m;
    }

    public void saveSubStats(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        prefs.edit().putString("subs", sb.toString()).apply();
    }

    /** Brise licni rjecnik, statistiku i odbijanja (podesavanja ostaju). */
    public void clearLearned() {
        SharedPreferences.Editor e = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (k.startsWith("rej_")) e.remove(k);
        }
        e.remove("subs").putStringSet("words", new HashSet<String>()).putBoolean("seeded", true).apply();
    }

    /** Ucita licne rijeci i statistiku u mehanizam ispravke. */
    public void applyTo(Corrector c) {
        for (String w : getWords()) c.dictionary().add(w, Corrector.USER_WORD_FREQ);
        c.setSubStats(getSubStats());
    }
}
