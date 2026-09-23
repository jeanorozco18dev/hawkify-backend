package com.hawkify.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Formatos {

    private static final Locale ES_CO = Locale.of("es", "CO");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM yyyy", ES_CO);

    private Formatos() {
    }

    /** $25.000 */
    public static String cop(BigDecimal valor) {
        NumberFormat nf = NumberFormat.getIntegerInstance(ES_CO);
        return "$" + nf.format(valor);
    }

    /** 12 oct 2026 */
    public static String fecha(LocalDate fecha) {
        return FECHA_CORTA.format(fecha).replace(".", "");
    }

    /** Mapa ordenado que admite valores null (Map.of no los admite), para auditoria. */
    public static Map<String, Object> mapa(Object... claveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) {
            m.put(String.valueOf(claveValor[i]), claveValor[i + 1]);
        }
        return m;
    }

    public static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }
}
