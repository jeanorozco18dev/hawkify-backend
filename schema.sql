-- ============================================================================
-- Hawkify — Modelo de base de datos
-- Motor: PostgreSQL 14+
-- Ver MODELO_BASE_DATOS.md para el diagrama ER, el diccionario de datos
-- y la justificación de cada decisión de diseño.
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "btree_gist"; -- EXCLUDE con igualdad + rango de fechas

-- ============================================================================
-- 1. IDENTIDAD Y RBAC
-- ============================================================================

CREATE TABLE rol (
    id      smallint PRIMARY KEY,
    nombre  varchar(30) NOT NULL UNIQUE CHECK (nombre IN ('superadmin', 'administrador', 'usuario_final'))
);
INSERT INTO rol (id, nombre) VALUES (1, 'superadmin'), (2, 'administrador'), (3, 'usuario_final');

CREATE TABLE permiso (
    id          smallint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    codigo      varchar(60) NOT NULL UNIQUE,
    descripcion text NOT NULL
);

CREATE TABLE rol_permiso (
    rol_id     smallint NOT NULL REFERENCES rol(id),
    permiso_id smallint NOT NULL REFERENCES permiso(id),
    PRIMARY KEY (rol_id, permiso_id)
);

CREATE TABLE usuario (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rol_id              smallint NOT NULL REFERENCES rol(id),
    nombre_completo     varchar(150) NOT NULL,
    correo              varchar(150) NOT NULL UNIQUE,
    password_hash       varchar(255) NOT NULL,
    telefono            varchar(30),
    documento_identidad varchar(30),
    foto_perfil_url     text,
    correo_verificado   boolean NOT NULL DEFAULT false,
    estado              varchar(20) NOT NULL DEFAULT 'activo'
                         CHECK (estado IN ('activo', 'suspendido', 'desactivado')),
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_usuario_rol ON usuario(rol_id);

CREATE TABLE direccion (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    etiqueta       varchar(50),
    linea1         varchar(200) NOT NULL,
    linea2         varchar(200),
    ciudad         varchar(100) NOT NULL,
    departamento   varchar(100) NOT NULL,
    codigo_postal  varchar(20),
    predeterminada boolean NOT NULL DEFAULT false
);
CREATE INDEX idx_direccion_usuario ON direccion(usuario_id);

CREATE TABLE token_recuperacion (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash   varchar(255) NOT NULL,
    expira_en    timestamptz NOT NULL,
    usado        boolean NOT NULL DEFAULT false,
    creado_en    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_token_recuperacion_usuario ON token_recuperacion(usuario_id);

-- ============================================================================
-- 2. CATÁLOGO
-- ============================================================================

CREATE TABLE categoria (
    id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre   varchar(100) NOT NULL UNIQUE,
    descripcion text,
    activa   boolean NOT NULL DEFAULT true
);

CREATE TABLE marca (
    id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre varchar(100) NOT NULL UNIQUE
);

-- propietario_id: cualquier usuario registrado puede publicar sus herramientas (decisión §6.3).
-- administrador_revisor_id: Admin/Superadmin que aprueba/rechaza la publicación (filtro de calidad).
CREATE TABLE producto (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    propietario_id            uuid NOT NULL REFERENCES usuario(id),
    administrador_revisor_id  uuid REFERENCES usuario(id),
    categoria_id              uuid NOT NULL REFERENCES categoria(id),
    marca_id                  uuid REFERENCES marca(id),
    nombre                    varchar(150) NOT NULL,
    descripcion               text,
    condiciones_uso           text,
    politica_garantia         text,
    tarifa_dia                numeric(10,2) NOT NULL CHECK (tarifa_dia > 0),
    estado_publicacion        varchar(25) NOT NULL DEFAULT 'pendiente_aprobacion'
                               CHECK (estado_publicacion IN
                                 ('pendiente_aprobacion', 'aprobado', 'rechazado', 'pausado', 'retirado')),
    estado_fisico             varchar(20) NOT NULL DEFAULT 'nuevo'
                               CHECK (estado_fisico IN ('nuevo', 'usado', 'en_mantenimiento')),
    calificacion_promedio     numeric(3,2) NOT NULL DEFAULT 0,
    total_calificaciones      integer NOT NULL DEFAULT 0,
    creado_en                 timestamptz NOT NULL DEFAULT now(),
    actualizado_en            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_producto_propietario ON producto(propietario_id);
CREATE INDEX idx_producto_categoria_tarifa ON producto(categoria_id, tarifa_dia);
CREATE INDEX idx_producto_calificacion ON producto(calificacion_promedio DESC);
CREATE INDEX idx_producto_publicacion ON producto(estado_publicacion);

CREATE TABLE producto_imagen (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id   uuid NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    url           text NOT NULL,
    orden         smallint NOT NULL DEFAULT 0,
    es_principal  boolean NOT NULL DEFAULT false
);
CREATE INDEX idx_producto_imagen_producto ON producto_imagen(producto_id);

CREATE TABLE producto_especificacion (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id  uuid NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    clave        varchar(80) NOT NULL,
    valor        varchar(200) NOT NULL
);
CREATE INDEX idx_producto_especificacion_producto ON producto_especificacion(producto_id);

CREATE TABLE historial_tarifa (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id    uuid NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    tarifa_dia     numeric(10,2) NOT NULL,
    vigente_desde  timestamptz NOT NULL DEFAULT now(),
    vigente_hasta  timestamptz
);
CREATE INDEX idx_historial_tarifa_producto ON historial_tarifa(producto_id);

-- Cada fila = un ejemplar físico. La disponibilidad de un producto en un
-- rango de fechas es el conteo de unidades sin reserva solapada (§2.1).
CREATE TABLE unidad_producto (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id     uuid NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    codigo_interno  varchar(50) UNIQUE,
    estado          varchar(20) NOT NULL DEFAULT 'disponible'
                    CHECK (estado IN ('disponible', 'en_mantenimiento', 'retirada')),
    creado_en       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_unidad_producto_producto ON unidad_producto(producto_id);

-- ============================================================================
-- 3. TRANSACCIONAL: RESERVAS, PAGO SIMULADO, DEVOLUCIÓN
-- ============================================================================

CREATE TABLE reserva (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id            uuid NOT NULL REFERENCES usuario(id),
    unidad_producto_id    uuid NOT NULL REFERENCES unidad_producto(id),
    fecha_inicio          date NOT NULL,
    fecha_fin             date NOT NULL,
    dias                  integer GENERATED ALWAYS AS (fecha_fin - fecha_inicio + 1) STORED,
    tarifa_dia_snapshot   numeric(10,2) NOT NULL,
    total                 numeric(12,2) NOT NULL,
    estado                varchar(20) NOT NULL DEFAULT 'pendiente_pago'
                          CHECK (estado IN
                            ('pendiente_pago', 'confirmada', 'en_curso', 'finalizada', 'cancelada', 'con_incidencia')),
    motivo_cancelacion    text,
    creado_en             timestamptz NOT NULL DEFAULT now(),
    cancelado_en          timestamptz,
    CHECK (fecha_fin >= fecha_inicio),
    -- RNF-12: impide, a nivel de motor, dos reservas activas solapadas sobre
    -- la misma unidad física. 'cancelada' queda excluida de la restricción
    -- para poder liberar y re-reservar el mismo rango.
    EXCLUDE USING gist (
        unidad_producto_id WITH =,
        daterange(fecha_inicio, fecha_fin, '[]') WITH &&
    ) WHERE (estado <> 'cancelada')
);
CREATE INDEX idx_reserva_usuario ON reserva(usuario_id);
CREATE INDEX idx_reserva_unidad ON reserva(unidad_producto_id);
CREATE INDEX idx_reserva_fechas ON reserva USING gist (daterange(fecha_inicio, fecha_fin, '[]'));

CREATE TABLE pago_simulado (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id           uuid NOT NULL UNIQUE REFERENCES reserva(id) ON DELETE CASCADE,
    monto                numeric(12,2) NOT NULL,
    metodo_simulado      varchar(30) NOT NULL CHECK (metodo_simulado IN ('tarjeta_simulada', 'transferencia_simulada')),
    estado               varchar(20) NOT NULL DEFAULT 'pendiente'
                         CHECK (estado IN ('pendiente', 'aprobado', 'rechazado')),
    referencia_simulada  varchar(60),
    fecha_pago           timestamptz
);

CREATE TABLE devolucion (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id             uuid NOT NULL UNIQUE REFERENCES reserva(id),
    registrado_por         uuid NOT NULL REFERENCES usuario(id),
    fecha_real_devolucion  timestamptz NOT NULL DEFAULT now(),
    estado_equipo          varchar(20) NOT NULL CHECK (estado_equipo IN ('sin_novedad', 'con_daño', 'perdida')),
    observaciones          text
);

-- ============================================================================
-- 4. REPUTACIÓN: CALIFICACIONES Y RESEÑAS
-- ============================================================================

-- Una calificación por reserva finalizada (RF-12); reserva_id es UNIQUE.
CREATE TABLE calificacion (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id   uuid NOT NULL UNIQUE REFERENCES reserva(id),
    usuario_id   uuid NOT NULL REFERENCES usuario(id),
    producto_id  uuid NOT NULL REFERENCES producto(id),
    estrellas    smallint NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
    creado_en    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_calificacion_producto ON calificacion(producto_id);

CREATE TABLE resena (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    calificacion_id    uuid NOT NULL UNIQUE REFERENCES calificacion(id),
    texto              text NOT NULL,
    estado_moderacion  varchar(20) NOT NULL DEFAULT 'visible'
                       CHECK (estado_moderacion IN ('visible', 'oculta', 'eliminada')),
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    eliminado_en       timestamptz
);

CREATE TABLE moderacion_resena (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resena_id     uuid NOT NULL REFERENCES resena(id) ON DELETE CASCADE,
    moderador_id  uuid NOT NULL REFERENCES usuario(id),
    accion        varchar(20) NOT NULL CHECK (accion IN ('ocultar', 'eliminar', 'revertir')),
    motivo        text,
    fecha         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_moderacion_resena_resena ON moderacion_resena(resena_id);

-- ============================================================================
-- 5. INTERACCIÓN: WISHLIST Y NOTIFICACIONES
-- ============================================================================

CREATE TABLE lista_deseos_item (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    producto_id  uuid NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    agregado_en  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, producto_id)
);

CREATE TABLE notificacion (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    tipo             varchar(30) NOT NULL
                     CHECK (tipo IN ('disponibilidad', 'cambio_tarifa', 'reserva', 'moderacion', 'sistema')),
    referencia_tipo  varchar(30),
    referencia_id    uuid,
    mensaje          text NOT NULL,
    leida            boolean NOT NULL DEFAULT false,
    creado_en        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notificacion_usuario_no_leida ON notificacion(usuario_id) WHERE NOT leida;

-- ============================================================================
-- 6. GOBIERNO: AUDITORÍA Y PARÁMETROS GLOBALES
-- ============================================================================

CREATE TABLE log_auditoria (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id         uuid REFERENCES usuario(id),
    accion           varchar(60) NOT NULL,
    entidad          varchar(60) NOT NULL,
    entidad_id       uuid,
    valores_previos  jsonb,
    valores_nuevos   jsonb,
    fecha            timestamptz NOT NULL DEFAULT now(),
    ip               inet
);
CREATE INDEX idx_log_auditoria_entidad ON log_auditoria(entidad, entidad_id);
CREATE INDEX idx_log_auditoria_fecha ON log_auditoria(fecha DESC);

CREATE TABLE parametro_global (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clave            varchar(80) NOT NULL UNIQUE,
    valor            text NOT NULL,
    descripcion      text,
    actualizado_por  uuid REFERENCES usuario(id),
    actualizado_en   timestamptz NOT NULL DEFAULT now()
);

-- ============================================================================
-- 7. TRIGGERS
-- ============================================================================

-- RF-13: recalcula el promedio y el conteo de calificaciones de un producto
-- cada vez que se inserta, actualiza o borra una calificación.
CREATE OR REPLACE FUNCTION fn_actualizar_calificacion_producto()
RETURNS trigger AS $$
DECLARE
    v_producto_id uuid := COALESCE(NEW.producto_id, OLD.producto_id);
BEGIN
    UPDATE producto SET
        calificacion_promedio = COALESCE((
            SELECT ROUND(AVG(estrellas)::numeric, 2)
            FROM calificacion WHERE producto_id = v_producto_id
        ), 0),
        total_calificaciones = (
            SELECT COUNT(*) FROM calificacion WHERE producto_id = v_producto_id
        )
    WHERE id = v_producto_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_calificacion_actualiza_producto
AFTER INSERT OR UPDATE OR DELETE ON calificacion
FOR EACH ROW EXECUTE FUNCTION fn_actualizar_calificacion_producto();

-- RF-21: si cambia la tarifa de un producto, cierra el período de vigencia
-- anterior en historial_tarifa y abre uno nuevo (soporta detectar el cambio
-- para notificar a quienes lo tengan en su lista de deseos).
CREATE OR REPLACE FUNCTION fn_registrar_historial_tarifa()
RETURNS trigger AS $$
BEGIN
    IF NEW.tarifa_dia IS DISTINCT FROM OLD.tarifa_dia THEN
        UPDATE historial_tarifa
        SET vigente_hasta = now()
        WHERE producto_id = NEW.id AND vigente_hasta IS NULL;

        INSERT INTO historial_tarifa (producto_id, tarifa_dia, vigente_desde)
        VALUES (NEW.id, NEW.tarifa_dia, now());
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_producto_historial_tarifa
AFTER UPDATE OF tarifa_dia ON producto
FOR EACH ROW EXECUTE FUNCTION fn_registrar_historial_tarifa();

-- Fila inicial de historial_tarifa al crear el producto.
CREATE OR REPLACE FUNCTION fn_historial_tarifa_inicial()
RETURNS trigger AS $$
BEGIN
    INSERT INTO historial_tarifa (producto_id, tarifa_dia, vigente_desde)
    VALUES (NEW.id, NEW.tarifa_dia, now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_producto_historial_tarifa_inicial
AFTER INSERT ON producto
FOR EACH ROW EXECUTE FUNCTION fn_historial_tarifa_inicial();

-- ============================================================================
-- 8. PERMISOS A NIVEL DE MOTOR (RNF-07: auditoría inmutable)
-- ============================================================================
-- El rol de aplicación (ajustar el nombre al que use el backend) solo puede
-- insertar y leer el log de auditoría; nunca modificarlo ni borrarlo.
--
-- CREATE ROLE hawkify_app LOGIN PASSWORD '...';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO hawkify_app;
-- REVOKE UPDATE, DELETE ON log_auditoria FROM hawkify_app;
-- GRANT SELECT, INSERT ON log_auditoria TO hawkify_app;

COMMIT;
