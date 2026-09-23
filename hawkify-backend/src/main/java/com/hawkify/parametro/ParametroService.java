package com.hawkify.parametro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.common.ApiException;
import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/** RF-30: parametros globales configurables por el superadmin. */
@Service
@RequiredArgsConstructor
public class ParametroService {

    public static final String CANCELACION_HORAS = "cancelacion.horas_minimas";
    public static final String CANCELACION_POLITICA = "cancelacion.politica";
    public static final String ENVIO_TARIFA = "envio.tarifa_domicilio";
    public static final String RESERVA_DIAS_MAX = "reserva.dias_maximos";
    public static final String RESERVA_MINUTOS_PAGO = "reserva.minutos_pago";
    public static final String GARANTIA_BASE = "garantia.texto_base";
    public static final String LEGAL_DATOS = "legal.politica_datos";
    public static final String LEGAL_TERMINOS = "legal.terminos";

    private static final Set<String> NUMERICOS =
            Set.of(CANCELACION_HORAS, ENVIO_TARIFA, RESERVA_DIAS_MAX, RESERVA_MINUTOS_PAGO);

    private static final Map<String, String> POR_DEFECTO = Map.of(
            CANCELACION_HORAS, "24",
            ENVIO_TARIFA, "15000",
            RESERVA_DIAS_MAX, "30",
            RESERVA_MINUTOS_PAGO, "15");

    private final ParametroGlobalRepository repository;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public String texto(String clave) {
        return repository.findByClave(clave)
                .map(ParametroGlobal::getValor)
                .orElse(POR_DEFECTO.getOrDefault(clave, ""));
    }

    @Transactional(readOnly = true)
    public int entero(String clave) {
        try {
            return Integer.parseInt(texto(clave).trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(POR_DEFECTO.get(clave));
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal decimal(String clave) {
        try {
            return new BigDecimal(texto(clave).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(POR_DEFECTO.get(clave));
        }
    }

    /** Todos los parametros son de lectura publica: son politicas que el usuario debe conocer. */
    @Transactional(readOnly = true)
    public Map<String, String> publicos() {
        Map<String, String> mapa = new LinkedHashMap<>(POR_DEFECTO);
        repository.findAllByOrderByClaveAsc().forEach(p -> mapa.put(p.getClave(), p.getValor()));
        return mapa;
    }

    @Transactional(readOnly = true)
    public List<ParametroResponse> listar() {
        return repository.findAllByOrderByClaveAsc().stream().map(ParametroResponse::de).toList();
    }

    @Transactional
    public ParametroResponse actualizar(Usuario superadmin, String clave, String valor) {
        ParametroGlobal p = repository.findByClave(clave)
                .orElseThrow(() -> ApiException.noEncontrado("No existe el parámetro '" + clave + "'"));

        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw ApiException.invalido("El valor no puede quedar vacío");
        }
        if (NUMERICOS.contains(clave)) {
            try {
                if (new BigDecimal(limpio).signum() < 0) {
                    throw ApiException.invalido("El valor debe ser un número positivo");
                }
            } catch (NumberFormatException e) {
                throw ApiException.invalido("El parámetro '" + clave + "' debe ser numérico");
            }
        }

        String anterior = p.getValor();
        p.setValor(limpio);
        p.setActualizadoPor(superadmin);
        p.setActualizadoEn(OffsetDateTime.now());

        auditoria.registrar(superadmin, "parametro_actualizado", "parametro_global", p.getId(),
                Map.of("clave", clave, "valor", anterior), Map.of("clave", clave, "valor", limpio));
        return ParametroResponse.de(p);
    }

    public record ParametroResponse(UUID id, String clave, String valor, String descripcion,
                                    boolean numerico, OffsetDateTime actualizadoEn) {
        static ParametroResponse de(ParametroGlobal p) {
            return new ParametroResponse(p.getId(), p.getClave(), p.getValor(), p.getDescripcion(),
                    NUMERICOS.contains(p.getClave()), p.getActualizadoEn());
        }
    }
}
