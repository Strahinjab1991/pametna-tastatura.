package ba.cncmikron.tastatura.engine;

import java.util.HashMap;
import java.util.Map;

/** Susjedni tasteri na QWERTZ rasporedu telefona (racuna se iz polozaja tastera). */
public final class Neighbors {
    // Isti raspored kao na tastaturi: treci red pocinje iza Shift tastera (pomak 1.5).
    private static final String[] ROWS = {"qwertzuiop", "asdfghjkl", "yxcvbnm"};
    private static final float[] OFFSETS = {0f, 0.5f, 1.5f};

    private static final Map<Character, String> NEAR = new HashMap<>();

    static {
        Map<Character, float[]> pos = new HashMap<>();
        for (int r = 0; r < ROWS.length; r++) {
            for (int i = 0; i < ROWS[r].length(); i++) {
                pos.put(ROWS[r].charAt(i), new float[]{OFFSETS[r] + i + 0.5f, r});
            }
        }
        for (Map.Entry<Character, float[]> a : pos.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Character, float[]> b : pos.entrySet()) {
                if (a.getKey().equals(b.getKey())) continue;
                float dx = a.getValue()[0] - b.getValue()[0];
                float dy = a.getValue()[1] - b.getValue()[1];
                // isti red: samo lijevo/desno; susjedni red: do 1 taster dijagonalno
                if (dy == 0 && Math.abs(dx) <= 1.01f) sb.append(b.getKey());
                else if (Math.abs(dy) == 1 && Math.abs(dx) <= 1.01f) sb.append(b.getKey());
            }
            NEAR.put(a.getKey(), sb.toString());
        }
    }

    private Neighbors() {}

    /** Tasteri oko slova c (bez kvacica). */
    public static String of(char c) {
        String s = NEAR.get(c);
        return s == null ? "" : s;
    }

    public static boolean areNear(char a, char b) {
        return of(a).indexOf(b) >= 0;
    }
}
