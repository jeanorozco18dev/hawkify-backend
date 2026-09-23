package com.hawkify.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Tablero del staff y reportes consolidados (RF-29). SQL de agregacion directo:
 * es lectura pura y PostgreSQL resuelve estas sumas mucho mejor que JPA.
 * Los ingresos siempre usan reserva.total (tarifa congelada), nunca el precio actual.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    private static final String FACTURABLES = "('confirmada','en_curso','finalizada','con_incidencia')";

    private final JdbcTemplate jdbc;

    public record ResumenStaff(
            long publicacionesPendientes,
            long herramientasPublicadas,
            long reservasActivas,
            long entregasHoy,
            long devolucionesPendientes,
            long incidencias,
            long resenasUltimos7Dias,
            long usuariosActivos,
            BigDecimal ingresosMes) {
    }

    public ResumenStaff resumen(Usuario staff) {
        String alcance = staff.esSuperadmin() ? "true"
                : "(p.administrador_revisor_id = ? or p.propietario_id = ?)";
        List<Object> args = staff.esSuperadmin() ? List.of() : List.of(staff.getId(), staff.getId());
        String baseReserva = " from reserva r join unidad_producto u on u.id = r.unidad_producto_id "
                + "join producto p on p.id = u.producto_id where " + alcance;

        LocalDate hoy = LocalDate.now();
        long pendientes = contar("select count(*) from producto where estado_publicacion = 'pendiente_aprobacion'");
        long publicadas = contar("select count(*) from producto p where p.estado_publicacion = 'aprobado' and "
                + alcance, args.toArray());
        long activas = contar("select count(*)" + baseReserva + " and r.estado in ('confirmada','en_curso')",
                args.toArray());
        long entregasHoy = contar("select count(*)" + baseReserva + " and r.estado = 'confirmada' and r.fecha_inicio <= ?",
                concat(args, Date.valueOf(hoy)));
        long devoluciones = contar("select count(*)" + baseReserva + " and r.estado = 'en_curso' and r.fecha_fin <= ?",
                concat(args, Date.valueOf(hoy)));
        long incidencias = contar("select count(*)" + baseReserva + " and r.estado = 'con_incidencia'", args.toArray());
        long resenas = contar("select count(*) from resena rs join calificacion c on c.id = rs.calificacion_id "
                + "join producto p on p.id = c.producto_id where " + alcance
                + " and rs.creado_en >= now() - interval '7 days'", args.toArray());
        long usuarios = contar(staff.esSuperadmin()
                ? "select count(*) from usuario where estado = 'activo' and eliminado_en is null"
                : "select count(*) from usuario u join rol ro on ro.id = u.rol_id "
                        + "where ro.nombre = 'usuario_final' and u.estado = 'activo' and u.eliminado_en is null");
        BigDecimal ingresos = jdbc.queryForObject("select coalesce(sum(r.total), 0)" + baseReserva
                        + " and r.estado in " + FACTURABLES + " and r.creado_en >= date_trunc('month', now())",
                BigDecimal.class, args.toArray());

        return new ResumenStaff(pendientes, publicadas, activas, entregasHoy, devoluciones, incidencias, resenas,
                usuarios, ingresos);
    }

    public Map<String, Object> reporte(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta == null ? LocalDate.now() : hasta;
        LocalDate inicio = desde == null ? fin.minusMonths(5).withDayOfMonth(1) : desde;
        Object[] rango = {Date.valueOf(inicio), Date.valueOf(fin.plusDays(1))};
        String enRango = " r.creado_en >= ? and r.creado_en < ? ";

        Map<String, Object> kpis = jdbc.queryForMap("""
                select
                  count(*) as reservas,
                  count(*) filter (where r.estado = 'cancelada') as canceladas,
                  coalesce(sum(r.total) filter (where r.estado in %s), 0) as ingresos,
                  coalesce(avg(r.total) filter (where r.estado in %s), 0) as ticket_promedio,
                  coalesce(avg(r.dias) filter (where r.estado in %s), 0) as dias_promedio
                from reserva r where %s
                """.formatted(FACTURABLES, FACTURABLES, FACTURABLES, enRango), rango);

        long reservas = ((Number) kpis.get("reservas")).longValue();
        long canceladas = ((Number) kpis.get("canceladas")).longValue();
        BigDecimal tasa = reservas == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(canceladas * 100.0 / reservas).setScale(1, RoundingMode.HALF_UP);
        long usuariosNuevos = contar("select count(*) from usuario where creado_en >= ? and creado_en < ?", rango);

        List<Map<String, Object>> porMes = jdbc.queryForList("""
                select to_char(date_trunc('month', r.creado_en), 'YYYY-MM') as mes,
                       count(*) as reservas,
                       count(*) filter (where r.estado = 'cancelada') as canceladas,
                       coalesce(sum(r.total) filter (where r.estado in %s), 0) as ingresos
                from reserva r where %s
                group by 1 order by 1
                """.formatted(FACTURABLES, enRango), rango);

        List<Map<String, Object>> top = jdbc.queryForList("""
                select p.id, p.nombre, c.nombre as categoria,
                       count(*) as reservas,
                       coalesce(sum(r.dias), 0) as dias,
                       coalesce(sum(r.total), 0) as ingresos,
                       p.calificacion_promedio as calificacion
                from reserva r
                join unidad_producto u on u.id = r.unidad_producto_id
                join producto p on p.id = u.producto_id
                join categoria c on c.id = p.categoria_id
                where r.estado in %s and %s
                group by p.id, p.nombre, c.nombre, p.calificacion_promedio
                order by reservas desc, ingresos desc
                limit 10
                """.formatted(FACTURABLES, enRango), rango);

        List<Map<String, Object>> porCategoria = jdbc.queryForList("""
                select c.nombre as categoria, count(*) as reservas, coalesce(sum(r.total), 0) as ingresos
                from reserva r
                join unidad_producto u on u.id = r.unidad_producto_id
                join producto p on p.id = u.producto_id
                join categoria c on c.id = p.categoria_id
                where r.estado in %s and %s
                group by c.nombre order by ingresos desc
                """.formatted(FACTURABLES, enRango), rango);

        List<Map<String, Object>> porEstado = jdbc.queryForList(
                "select r.estado, count(*) as total from reserva r where" + enRango + "group by r.estado order by 2 desc",
                rango);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("desde", inicio);
        resultado.put("hasta", fin);
        resultado.put("reservas", reservas);
        resultado.put("canceladas", canceladas);
        resultado.put("tasaCancelacion", tasa);
        resultado.put("ingresos", kpis.get("ingresos"));
        resultado.put("ticketPromedio", ((BigDecimal) kpis.get("ticket_promedio")).setScale(0, RoundingMode.HALF_UP));
        resultado.put("diasPromedio", ((BigDecimal) kpis.get("dias_promedio")).setScale(1, RoundingMode.HALF_UP));
        resultado.put("usuariosNuevos", usuariosNuevos);
        resultado.put("porMes", porMes);
        resultado.put("topProductos", top);
        resultado.put("porCategoria", porCategoria);
        resultado.put("porEstado", porEstado);
        return resultado;
    }

    private long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Object[] concat(List<Object> base, Object extra) {
        List<Object> l = new ArrayList<>(base);
        l.add(extra);
        return l.toArray();
    }
}
