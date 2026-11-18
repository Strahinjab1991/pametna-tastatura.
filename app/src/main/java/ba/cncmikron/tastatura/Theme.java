package ba.cncmikron.tastatura;

/** Boje tastature. Sve teme su ovdje; redoslijed = redoslijed u podesavanjima. */
public final class Theme {
    public final String name;
    public final int bg;          // pozadina tastature i trake prijedloga
    public final int key;         // obicni taster (slova, razmak)
    public final int keySpecial;  // Shift, Delete, ?123
    public final int keyPressed;  // pritisnut taster
    public final int accent;      // Enter, Caps Lock, izabrana kvacica
    public final int accentPressed;
    public final int text;        // slova na tasterima
    public final int textDim;     // razmak, mala slova za kvacice
    public final int popup;       // oblacic za kvacice i uvecano slovo
    public final int border;      // tanka ivica tastera (0 = bez ivice)
    public final int suggestion;  // prijedlog koji ce se primijeniti

    private Theme(String name, int bg, int key, int keySpecial, int keyPressed, int accent, int accentPressed,
                  int text, int textDim, int popup, int border, int suggestion) {
        this.name = name;
        this.bg = bg;
        this.key = key;
        this.keySpecial = keySpecial;
        this.keyPressed = keyPressed;
        this.accent = accent;
        this.accentPressed = accentPressed;
        this.text = text;
        this.textDim = textDim;
        this.popup = popup;
        this.border = border;
        this.suggestion = suggestion;
    }

    public static final Theme[] ALL = {
            new Theme("Tamna", 0xFF1C1D20, 0xFF4A4E56, 0xFF33363C, 0xFF6B7180, 0xFF2F6FD6, 0xFF5C8EE0,
                    0xFFF2F2F2, 0xFF9AA0AA, 0xFF2A2D33, 0, 0xFF8FB4FF),
            new Theme("Svijetla", 0xFFE4E6EA, 0xFFFFFFFF, 0xFFC9CDD4, 0xFFB4BAC4, 0xFF2F6FD6, 0xFF5C8EE0,
                    0xFF1E2126, 0xFF6B717C, 0xFFFFFFFF, 0xFFCDD1D8, 0xFF1F5FC8),
            new Theme("Crna (AMOLED)", 0xFF000000, 0xFF141414, 0xFF0A0A0A, 0xFF333333, 0xFF2F6FD6, 0xFF5C8EE0,
                    0xFFFFFFFF, 0xFF8A8A8A, 0xFF1E1E1E, 0xFF2A2A2A, 0xFF8FB4FF),
            new Theme("Plava noć", 0xFF0F1B2D, 0xFF213A5C, 0xFF172A44, 0xFF34588A, 0xFF3FA4FF, 0xFF7CC2FF,
                    0xFFEAF3FF, 0xFF8FA7C4, 0xFF1A2E4A, 0, 0xFF7CC2FF),
            new Theme("Zelena", 0xFF14201A, 0xFF2B4236, 0xFF1F3128, 0xFF44664F, 0xFF2EAD5B, 0xFF5ACB82,
                    0xFFEAF5EE, 0xFF93AD9D, 0xFF1D2D24, 0, 0xFF7ADB9C),
            new Theme("Ljubičasta", 0xFF1D1726, 0xFF3A2F4D, 0xFF2A2238, 0xFF5A4A78, 0xFF8E5BE8, 0xFFAD86F0,
                    0xFFF3EEFB, 0xFFA59CB5, 0xFF2A2238, 0, 0xFFC5A8FF),
            new Theme("Narandžasta", 0xFF2A1F17, 0xFF4A3628, 0xFF36281D, 0xFF6E513B, 0xFFF07A1A, 0xFFF59A4E,
                    0xFFFFF3E8, 0xFFB8A08C, 0xFF36281D, 0, 0xFFFFB36B),
            new Theme("Bež (svijetla)", 0xFFEDE6DA, 0xFFFBF8F2, 0xFFD9CFBE, 0xFFC7BBA6, 0xFFB5651D, 0xFFCC8547,
                    0xFF3B2F22, 0xFF85765F, 0xFFFBF8F2, 0xFFD3C8B5, 0xFF9C5214),
    };

    public static Theme get(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    /** Oblik tastera. */
    public static final int SHAPE_ROUND = 0, SHAPE_SQUARE = 1, SHAPE_PILL = 2, SHAPE_FLAT = 3;
    public static final String[] SHAPES = {"Zaobljeni", "Uglasti", "Okrugli", "Bez okvira"};

    /** Razmak izmedju tastera, u dp. */
    public static final float[] GAPS = {1.5f, 3f, 5f};
    public static final String[] GAP_NAMES = {"Mali", "Srednji", "Veliki"};
}
