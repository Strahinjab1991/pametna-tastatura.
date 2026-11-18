package ba.cncmikron.tastatura.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rjecnik: rijec -> ucestalost, i indeks po obliku bez kvacica (mozemo -> [možemo]). */
public class Dictionary {
    private final Map<String, Integer> freq = new HashMap<>(80000);
    private final Map<String, List<String>> byStripped = new HashMap<>(80000);

    public void load(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String w = line.substring(0, tab);
            int f;
            try {
                f = Integer.parseInt(line.substring(tab + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            add(w, f);
        }
    }

    public synchronized void add(String word, int f) {
        String w = word.toLowerCase();
        Integer old = freq.get(w);
        if (old == null) {
            freq.put(w, f);
            String s = strip(w);
            List<String> list = byStripped.get(s);
            if (list == null) {
                list = new ArrayList<>(1);
                byStripped.put(s, list);
            }
            list.add(w);
        } else if (f > old) {
            freq.put(w, f);
        }
    }

    public synchronized void remove(String word) {
        String w = word.toLowerCase();
        if (freq.remove(w) != null) {
            List<String> list = byStripped.get(strip(w));
            if (list != null) list.remove(w);
        }
    }

    public synchronized boolean contains(String word) {
        return freq.containsKey(word.toLowerCase());
    }

    public synchronized int freq(String word) {
        Integer f = freq.get(word);
        return f == null ? 0 : f;
    }

    /** Sve rijeci ciji je oblik bez kvacica jednak s (s mora biti vec bez kvacica). */
    public synchronized List<String> byStripped(String s) {
        List<String> l = byStripped.get(s);
        return l == null ? java.util.Collections.<String>emptyList() : new ArrayList<>(l);
    }

    public synchronized int size() {
        return freq.size();
    }

    public static char stripChar(char c) {
        switch (c) {
            case 'č': case 'ć': return 'c';
            case 'ž': return 'z';
            case 'š': return 's';
            case 'đ': return 'd';
            default: return c;
        }
    }

    public static String strip(String w) {
        StringBuilder sb = new StringBuilder(w.length());
        for (int i = 0; i < w.length(); i++) sb.append(stripChar(w.charAt(i)));
        return sb.toString();
    }
}
