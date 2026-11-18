package ba.cncmikron.tastatura;

import android.content.Context;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import ba.cncmikron.tastatura.engine.Corrector;
import ba.cncmikron.tastatura.engine.Dictionary;

/** Jedan zajednicki rjecnik za tastaturu i podesavanja; ucitava se jednom, u pozadini. */
public final class Engine {
    private static Corrector corrector;
    private static boolean loading;

    private Engine() {}

    /** Vraca mehanizam ako je rjecnik ucitan, inace null (i pokrece ucitavanje). */
    public static synchronized Corrector get(final Context ctx) {
        if (corrector != null) return corrector;
        if (!loading) {
            loading = true;
            final Context app = ctx.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Dictionary d = new Dictionary();
                    try {
                        d.load(new InputStreamReader(app.getAssets().open("words.txt"), StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        // bez rjecnika tastatura i dalje kuca, samo ne ispravlja
                    }
                    Corrector c = new Corrector(d);
                    new UserStore(app).applyTo(c);
                    synchronized (Engine.class) {
                        corrector = c;
                        loading = false;
                    }
                }
            }, "rjecnik").start();
        }
        return null;
    }

    /** Nakon brisanja naucenog: ucitaj sve ispocetka. */
    public static synchronized void reset() {
        corrector = null;
    }
}
