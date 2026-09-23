package com.hawkify.auditoria;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.hawkify.common.PaginaResponse;
import com.hawkify.usuario.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-28 / RNF-07: log append-only. Se escribe con SQL nativo porque las
 * columnas jsonb e inet no tienen un mapeo JPA limpio, y la tabla nunca se
 * actualiza (el trigger trg_log_auditoria_inmutable lo impide a nivel de motor).
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public void registrar(Usuario actor, String accion, String entidad, UUID entidadId,
                          Map<String, ?> previos, Map<String, ?> nuevos) {
        jdbc.update("""
                insert into log_auditoria (actor_id, accion, entidad, entidad_id, valores_previos, valores_nuevos, ip)
                values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as inet))
                """,
                actor == null ? null : actor.getId(),
                accion,
                entidad,
                entidadId,
                aJson(previos),
                aJson(nuevos),
                ipActual());
    }

    public PaginaResponse<RegistroAuditoria> buscar(Usuario solicitante, String entidad, String accion,
                                                   String q, LocalDate desde, LocalDate hasta,
                                                   int pagina, int tamano) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();

        if (!solicitante.esSuperadmin()) {
            // Matriz de roles: el administrador solo audita su propio inventario.
            where.append("""
                     and (l.actor_id = ?
                          or (l.entidad = 'producto' and l.entidad_id in (
                                select p.id from producto p
                                where p.administrador_revisor_id = ? or p.propietario_id = ?)))
                    """);
            args.add(solicitante.getId());
            args.add(solicitante.getId());
            args.add(solicitante.getId());
        }
        if (entidad != null && !entidad.isBlank()) {
            where.append(" and l.entidad = ?");
            args.add(entidad);
        }
        if (accion != null && !accion.isBlank()) {
            where.append(" and l.accion = ?");
            args.add(accion);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(u.nombre_completo) like ? or lower(u.correo) like ? or l.entidad_id::text like ?)");
            String like = "%" + q.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (desde != null) {
            where.append(" and l.fecha >= ?");
            args.add(Timestamp.from(desde.atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (hasta != null) {
            where.append(" and l.fecha < ?");
            args.add(Timestamp.from(hasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }

        String from = " from log_auditoria l left join usuario u on u.id = l.actor_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());

        List<Object> argsPagina = new ArrayList<>(args);
        argsPagina.add(tamano);
        argsPagina.add((long) pagina * tamano);

        List<RegistroAuditoria> filas = jdbc.query("""
                select l.id, l.actor_id, u.nombre_completo, u.correo, l.accion, l.entidad, l.entidad_id,
                       l.valores_previos::text as previos, l.valores_nuevos::text as nuevos,
                       l.fecha, host(l.ip) as ip
                """ + from + where + " order by l.fecha desc limit ? offset ?",
                (rs, i) -> new RegistroAuditoria(
                        rs.getObject("id", UUID.class),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("nombre_completo"),
                        rs.getString("correo"),
                        rs.getString("accion"),
                        rs.getString("entidad"),
                        rs.getObject("entidad_id", UUID.class),
                        leerJson(rs.getString("previos")),
                        leerJson(rs.getString("nuevos")),
                        rs.getObject("fecha", OffsetDateTime.class),
                        rs.getString("ip")),
                argsPagina.toArray());

        return PaginaResponse.de(filas, pagina, tamano, total == null ? 0 : total);
    }

    public List<String> entidadesRegistradas() {
        return jdbc.queryForList("select distinct entidad from log_auditoria order by entidad", String.class);
    }

    private String aJson(Map<String, ?> valores) {
        return valores == null || valores.isEmpty() ? null : jsonMapper.writeValueAsString(valores);
    }

    private JsonNode leerJson(String json) {
        return json == null ? null : jsonMapper.readTree(json);
    }

    private String ipActual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String reenviada = request.getHeader("X-Forwarded-For");
        String ip = reenviada != null && !reenviada.isBlank()
                ? reenviada.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip != null && ip.matches("[0-9a-fA-F:.]+") ? ip : null;
    }

    public record RegistroAuditoria(
            UUID id,
            UUID actorId,
            String actorNombre,
            String actorCorreo,
            String accion,
            String entidad,
            UUID entidadId,
            JsonNode valoresPrevios,
            JsonNode valoresNuevos,
            OffsetDateTime fecha,
            String ip
    ) {
    }
}
