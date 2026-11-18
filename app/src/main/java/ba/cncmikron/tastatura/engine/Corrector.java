package ba.cncmikron.tastatura.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mehanizam ispravke. Radi sa oblicima bez kvacica, pa iz rjecnika vraca prave rijeci
 * sa kvacicama (mozwmo -> mozemo -> možemo).
 */
public class Corrector {
    /** Ucestalost koju dobija rijec iz licnog rjecnika (jaka kao cesta rijec). */
    public static final int USER_WORD_FREQ = 2000;

    private static final String ALPHA = "abcdefghijklmnoprstuvz";
    private static final double COST_NEAR = 1.0;   // pogodjen susjedni taster
    private static final double COST_FAR = 2.2;    // potpuno drugo slovo
    private static final double COST_SWAP = 1.2;   // dva slova zamijenjena
    private static final double COST_DOUBLE = 1.1; // slovo vise/manje uz isto slovo (ss, ll)
    private static final double COST_INDEL = 1.6;  // slovo vise ili manje
    private static final double MAX_COST = 2.4;    // dalje od ovoga se ne ispravlja
    private static final double WEIGHT = 3.0;      // koliko jedna greska vrijedi u odnosu na ucestalost
    private static final double AUTO_MARGIN = 0.7; // koliko najbolji mora biti ispred drugog

    public static final class Candidate {
        public final String word;
        public final double score;
        public final double cost;

        Candidate(String word, double score, double cost) {
            this.word = word;
            this.score = score;
            this.cost = cost;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s(%.2f c=%.1f)", word, score, cost);
        }
    }

    private final Dictionary dict;
    /** Brojac licnih gresaka: "q>a" = otkucao q, a htio a. */
    private final Map<String, Integer> subStats = new HashMap<>();

    public Corrector(Dictionary dict) {
        this.dict = dict;
    }

    public Dictionary dictionary() {
        return dict;
    }

    public synchronized Map<String, Integer> subStats() {
        return new HashMap<>(subStats);
    }

    public synchronized void setSubStats(Map<String, Integer> stats) {
        subStats.clear();
        subStats.putAll(stats);
    }

    private synchronized double bonus(char typed, char intended) {
        Integer n = subStats.get(typed + ">" + intended);
        return n == null ? 0 : Math.min(0.5, 0.1 * n);
    }

    /** Da li je rijec poznata (rjecnik ili licni rjecnik). */
    public boolean isKnown(String word) {
        return dict.contains(word);
    }

    /** Kandidati sortirani od najboljeg. */
    public List<Candidate> candidates(String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        String ts = Dictionary.strip(t);
        Map<String, Double> edits = new HashMap<>();
        edits.put(ts, 0.0);
        edits1(ts, 0.0, edits, false);

        // druga greska: samo susjedni tasteri, na jeftine prve ispravke
        List<Map.Entry<String, Double>> first = new ArrayList<>(edits.entrySet());
        for (Map.Entry<String, Double> e : first) {
            if (e.getValue() > 0 && e.getValue() <= COST_NEAR + 0.2) {
                edits1(e.getKey(), e.getValue(), edits, true);
            }
        }

        Map<String, Candidate> best = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : edits.entrySet()) {
            if (e.getValue() > MAX_COST) continue;
            for (String w : dict.byStripped(e.getKey())) {
                double cost = e.getValue() + diacriticPenalty(t, w);
                int f = dict.freq(w);
                if (f <= 0) continue;
                double score = Math.log(f) - WEIGHT * cost;
                Candidate old = best.get(w);
                if (old == null || old.score < score) best.put(w, new Candidate(w, score, cost));
            }
        }
        List<Candidate> list = new ArrayList<>(best.values());
        Collections.sort(list, new java.util.Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(b.score, a.score);
            }
        });
        return list;
    }

    /** Ako je korisnik sam otkucao kvacicu, a rijec je nema, to je mala greska. */
    private static double diacriticPenalty(String typed, String word) {
        if (typed.length() != word.length()) return 0;
        double p = 0;
        for (int i = 0; i < typed.length(); i++) {
            char a = typed.charAt(i), b = word.charAt(i);
            if (a != b && Dictionary.stripChar(a) != a) p += 0.5;
        }
        return p;
    }

    private void edits1(String s, double base, Map<String, Double> out, boolean nearOnly) {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            String pre = s.substring(0, i), post = s.substring(i + 1);
            // zamjena slova
            for (int k = 0; k < ALPHA.length(); k++) {
                char l = ALPHA.charAt(k);
                if (l == ch) continue;
                boolean near = Neighbors.areNear(ch, l);
                if (nearOnly && !near) continue;
                double c = near ? COST_NEAR - bonus(ch, l) : COST_FAR;
                put(out, pre + l + post, base + c);
            }
            if (nearOnly) continue;
            // slovo vise
            if (n > 1) {
                boolean dbl = (i > 0 && s.charAt(i - 1) == ch) || (i + 1 < n && s.charAt(i + 1) == ch);
                boolean nearAdj = (i > 0 && Neighbors.areNear(ch, s.charAt(i - 1)))
                        || (i + 1 < n && Neighbors.areNear(ch, s.charAt(i + 1)));
                put(out, pre + post, base + (dbl ? COST_DOUBLE : nearAdj ? COST_NEAR + 0.2 : COST_INDEL));
            }
            // zamijenjena dva slova
            if (i + 1 < n && s.charAt(i + 1) != ch) {
                put(out, pre + s.charAt(i + 1) + ch + s.substring(i + 2), base + COST_SWAP);
            }
        }
        if (nearOnly) return;
        // slovo manje
        for (int i = 0; i <= n; i++) {
            String pre = s.substring(0, i), post = s.substring(i);
            for (int k = 0; k < ALPHA.length(); k++) {
                char l = ALPHA.charAt(k);
                boolean dbl = (i > 0 && s.charAt(i - 1) == l) || (i < n && s.charAt(i) == l);
                put(out, pre + l + post, base + (dbl ? COST_DOUBLE : COST_INDEL));
            }
        }
    }

    private static void put(Map<String, Double> m, String s, double c) {
        if (s.isEmpty()) return;
        Double old = m.get(s);
        if (old == null || c < old) m.put(s, c);
    }

    /**
     * Automatska ispravka: vraca ispravljenu rijec (sa ocuvanim velikim slovima)
     * ili null ako rijec treba ostaviti kakva jeste.
     */
    public String autoCorrect(String typed) {
        if (typed.length() < 2 || !isWord(typed)) return null;
        String t = typed.toLowerCase(Locale.ROOT);
        if (dict.contains(t)) return null;
        List<Candidate> c = candidates(t);
        if (c.isEmpty()) return null;
        Candidate best = c.get(0);
        if (best.cost > MAX_COST) return null;
        // kratke rijeci (2-3 slova) samo za jednu sitnu gresku
        if (t.length() <= 3 && best.cost > COST_NEAR + 0.2) return null;
        if (c.size() > 1 && best.cost > 0.01) {
            if (best.score - c.get(1).score < AUTO_MARGIN) return null;
        }
        return applyCase(typed, best.word);
    }

    /** Do max prijedloga za traku; prvi je onaj koji bi se primijenio. */
    public List<String> suggestions(String typed, int max) {
        List<String> out = new ArrayList<>();
        if (typed.isEmpty() || !isWord(typed)) return out;
        String t = typed.toLowerCase(Locale.ROOT);
        String auto = autoCorrect(typed);
        if (auto != null) out.add(auto);
        else if (dict.contains(t)) out.add(typed);
        for (Candidate cand : candidates(t)) {
            if (out.size() >= max) break;
            String w = applyCase(typed, cand.word);
            if (!out.contains(w)) out.add(w);
        }
        // dopuna: rijeci koje pocinju otkucanim (za nedovrsene rijeci)
        return out;
    }

    /** Zapamti koja slova je korisnik promasio (otkucano -> ispravljeno). */
    public synchronized void learn(String typed, String corrected) {
        String a = Dictionary.strip(typed.toLowerCase(Locale.ROOT));
        String b = Dictionary.strip(corrected.toLowerCase(Locale.ROOT));
        if (a.length() != b.length()) return;
        for (int i = 0; i < a.length(); i++) {
            char x = a.charAt(i), y = b.charAt(i);
            if (x != y && Neighbors.areNear(x, y)) {
                String key = x + ">" + y;
                Integer n = subStats.get(key);
                subStats.put(key, n == null ? 1 : n + 1);
            }
        }
    }

    public static boolean isWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetter(s.charAt(i))) return false;
        }
        return true;
    }

    public static String applyCase(String typed, String word) {
        if (typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT))
                && !typed.equals(typed.toLowerCase(Locale.ROOT))) {
            return word.toUpperCase(Locale.ROOT);
        }
        if (!typed.isEmpty() && Character.isUpperCase(typed.charAt(0))) {
            return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
        }
        return word;
    }
}
