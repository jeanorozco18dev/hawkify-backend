package com.hawkify.notificacion;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.common.ApiException;
import com.hawkify.common.PaginaResponse;
import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository repository;

    @Transactional
    public void notificar(Usuario destinatario, TipoNotificacion tipo, String referenciaTipo,
                          UUID referenciaId, String mensaje) {
        Notificacion n = new Notificacion();
        n.setUsuario(destinatario);
        n.setTipo(tipo);
        n.setReferenciaTipo(referenciaTipo);
        n.setReferenciaId(referenciaId);
        n.setMensaje(mensaje);
        repository.save(n);
    }

    @Transactional
    public void notificarVarios(Collection<Usuario> destinatarios, TipoNotificacion tipo, String referenciaTipo,
                                UUID referenciaId, String mensaje) {
        destinatarios.forEach(u -> notificar(u, tipo, referenciaTipo, referenciaId, mensaje));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<NotificacionResponse> listar(UUID usuarioId, int pagina, int tamano) {
        return PaginaResponse.de(
                repository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId, PageRequest.of(pagina, tamano)),
                NotificacionResponse::de);
    }

    @Transactional(readOnly = true)
    public long noLeidas(UUID usuarioId) {
        return repository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    @Transactional
    public NotificacionResponse marcarLeida(UUID usuarioId, UUID id) {
        Notificacion n = repository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.noEncontrado("La notificación no existe"));
        n.setLeida(true);
        return NotificacionResponse.de(n);
    }

    @Transactional
    public int marcarTodas(UUID usuarioId) {
        return repository.marcarTodasLeidas(usuarioId);
    }

    public record NotificacionResponse(
            UUID id,
            TipoNotificacion tipo,
            String referenciaTipo,
            UUID referenciaId,
            String mensaje,
            boolean leida,
            OffsetDateTime creadoEn
    ) {
        static NotificacionResponse de(Notificacion n) {
            return new NotificacionResponse(n.getId(), n.getTipo(), n.getReferenciaTipo(), n.getReferenciaId(),
                    n.getMensaje(), n.isLeida(), n.getCreadoEn());
        }
    }
}
