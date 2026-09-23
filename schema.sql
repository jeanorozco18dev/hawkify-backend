-- ============================================================================
-- Hawkify — Modelo de base de datos (v2)
-- Motor: PostgreSQL 14+ (probado sobre PostgreSQL 15; compatible con Supabase)
-- Ver MODELO_BASE_DATOS.md para el diagrama ER y la justificación del diseño.
--
-- Cambios v2 respecto a v1:
--   * usuario_permiso reemplaza rol_permiso: el superadmin define el alcance
--     de CADA administrador (RF-05), no del rol completo.
--   * usuario: suspendido_hasta, motivo_estado, eliminado_en (RF-04, supresión).
--   * producto: motivo_rechazo (feedback al propietario al rechazar).
--   * reserva: modalidad_entrega, direccion_entrega, subtotal, costo_envio,
--     actualizado_en.
--   * pago_simulado: métodos PSE/Nequi simulados, estado 'reembolsado', detalle.
--   * devolucion: 'con_dano' (sin ñ en valores de dominio).
--   * notificacion: tipo 'publicacion'.
--   * Semillas: permisos, categorías, marcas y parámetros globales.
--   * RLS habilitado en todas las tablas (Supabase: bloquea la API REST pública;
--     el backend entra como dueño de las tablas y no se ve afectado).
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "btree_gist"; -- EXCLUDE con igualdad + rango de fechas

-- ============================================================================
-- 1. IDENTIDAD Y PERMISOS
-- ============================================================================

CREATE TABLE rol (
    id      smallint PRIMARY KEY,
    nombre  varchar(30) NOT NULL UNIQUE CHECK (nombre IN ('superadmin', 'administrador', 'usuario_final'))
);
INSERT INTO rol (id, nombre) VALUES (1, 'superadmin'), (2, 'administrador'), (3, 'usuario_final');

CREATE TABLE permiso (
    id          smallint PRIMARY KEY,
    codigo      varchar(60) NOT NULL UNIQUE,
    descripcion text NOT NULL
);
INSERT INTO permiso (id, codigo, descripcion) VALUES
    (1, 'catalogo_gestionar', 'Crear, editar, pausar y retirar herramientas bajo su gestión'),
    (2, 'catalogo_aprobar',   'Aprobar o rechazar publicaciones pendientes'),
    (3, 'usuarios_gestionar', 'Crear, editar, suspender y desactivar usuarios finales'),
    (4, 'reservas_gestionar', 'Actualizar estados de reserva y registrar devoluciones'),
    (5, 'resenas_moderar',    'Ocultar o eliminar reseñas de sus herramientas');

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
    suspendido_hasta    timestamptz,
    motivo_estado       text,
    acepto_politica_en  timestamptz,
    eliminado_en        timestamptz,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_usuario_rol ON usuario(rol_id);

-- Alcance de cada administrador (RF-05). El superadmin tiene todos implícitamente.
CREATE TABLE usuario_permiso (
    usuario_id  uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    permiso_id  smallint NOT NULL REFERENCES permiso(id),
    PRIMARY KEY (usuario_id, permiso_id)
);

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
CREATE INDEX idx_token_recuperacion_hash ON token_recuperacion(token_hash);

-- ============================================================================
-- 2. CATÁLOGO
-- ============================================================================

CREATE TABLE categoria (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre      varchar(100) NOT NULL UNIQUE,
    descripcion text,
    activa      boolean NOT NULL DEFAULT true
);

CREATE TABLE marca (
    id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre varchar(100) NOT NULL UNIQUE
);

-- propietario_id: cualquier usuario registrado puede publicar sus herramientas.
-- administrador_revisor_id: Admin/Superadmin que aprueba y gestiona la ficha.
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
    motivo_rechazo            text,
    calificacion_promedio     numeric(3,2) NOT NULL DEFAULT 0,
    total_calificaciones      integer NOT NULL DEFAULT 0,
    creado_en                 timestamptz NOT NULL DEFAULT now(),
    actualizado_en            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_producto_propietario ON producto(propietario_id);
CREATE INDEX idx_producto_revisor ON producto(administrador_revisor_id);
CREATE INDEX idx_producto_categoria_tarifa ON producto(categoria_id, tarifa_dia);
CREATE INDEX idx_producto_marca ON producto(marca_id);
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
-- rango de fechas es el conteo de unidades sin reserva solapada.
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
    subtotal              numeric(12,2) NOT NULL,
    costo_envio           numeric(12,2) NOT NULL DEFAULT 0,
    total                 numeric(12,2) NOT NULL,
    modalidad_entrega     varchar(20) NOT NULL DEFAULT 'recogida'
                          CHECK (modalidad_entrega IN ('recogida', 'domicilio')),
    direccion_entrega     text,
    estado                varchar(20) NOT NULL DEFAULT 'pendiente_pago'
                          CHECK (estado IN
                            ('pendiente_pago', 'confirmada', 'en_curso', 'finalizada', 'cancelada', 'con_incidencia')),
    motivo_cancelacion    text,
    creado_en             timestamptz NOT NULL DEFAULT now(),
    actualizado_en        timestamptz NOT NULL DEFAULT now(),
    cancelado_en          timestamptz,
    CHECK (fecha_fin >= fecha_inicio),
    -- RNF-12: impide, a nivel de motor, dos reservas activas solapadas sobre
    -- la misma unidad física, incluso con transacciones concurrentes.
    EXCLUDE USING gist (
        unidad_producto_id WITH =,
        daterange(fecha_inicio, fecha_fin, '[]') WITH &&
    ) WHERE (estado <> 'cancelada')
);
CREATE INDEX idx_reserva_usuario ON reserva(usuario_id);
CREATE INDEX idx_reserva_unidad ON reserva(unidad_producto_id);
CREATE INDEX idx_reserva_estado ON reserva(estado);
CREATE INDEX idx_reserva_fechas ON reserva USING gist (daterange(fecha_inicio, fecha_fin, '[]'));

CREATE TABLE pago_simulado (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id           uuid NOT NULL UNIQUE REFERENCES reserva(id) ON DELETE CASCADE,
    monto                numeric(12,2) NOT NULL,
    metodo_simulado      varchar(30) NOT NULL
                         CHECK (metodo_simulado IN ('tarjeta_simulada', 'pse_simulado', 'nequi_simulado')),
    estado               varchar(20) NOT NULL DEFAULT 'pendiente'
                         CHECK (estado IN ('pendiente', 'aprobado', 'rechazado', 'reembolsado')),
    referencia_simulada  varchar(60),
    detalle              varchar(120),
    fecha_pago           timestamptz
);

CREATE TABLE devolucion (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id             uuid NOT NULL UNIQUE REFERENCES reserva(id),
    registrado_por         uuid NOT NULL REFERENCES usuario(id),
    fecha_real_devolucion  timestamptz NOT NULL DEFAULT now(),
    estado_equipo          varchar(20) NOT NULL CHECK (estado_equipo IN ('sin_novedad', 'con_dano', 'perdida')),
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
CREATE INDEX idx_calificacion_usuario ON calificacion(usuario_id);

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
                     CHECK (tipo IN ('disponibilidad', 'cambio_tarifa', 'reserva', 'moderacion', 'publicacion', 'sistema')),
    referencia_tipo  varchar(30),
    referencia_id    uuid,
    mensaje          text NOT NULL,
    leida            boolean NOT NULL DEFAULT false,
    creado_en        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notificacion_usuario ON notificacion(usuario_id, creado_en DESC);
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
CREATE INDEX idx_log_auditoria_actor ON log_auditoria(actor_id);
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

-- RF-13: recalcula el promedio y el conteo de calificaciones de un producto.
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

-- RF-21: historial de tarifa (el aviso a la wishlist lo emite el backend).
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

-- RNF-07: el log de auditoría es append-only. Se bloquea a nivel de motor,
-- sin depender de permisos del rol de conexión (que en Supabase es dueño).
CREATE OR REPLACE FUNCTION fn_log_auditoria_inmutable()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'log_auditoria es de solo inserción (RNF-07)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_log_auditoria_inmutable
BEFORE UPDATE OR DELETE ON log_auditoria
FOR EACH ROW EXECUTE FUNCTION fn_log_auditoria_inmutable();

-- ============================================================================
-- 8. DATOS BASE (configuración, no datos de demo)
-- ============================================================================

INSERT INTO categoria (nombre, descripcion) VALUES
    ('Perforación',        'Taladros, rotomartillos y martillos demoledores'),
    ('Corte',              'Sierras circulares, caladoras, ingletadoras y pulidoras'),
    ('Carpintería',        'Lijadoras, cepillos y router para madera'),
    ('Aire y neumática',   'Compresores y herramienta neumática'),
    ('Kits y combos',      'Conjuntos de herramientas a batería listos para obra'),
    ('Jardín y exteriores','Guadañas, hidrolavadoras y sopladoras'),
    ('Medición',           'Niveles láser, medidores y detectores');

INSERT INTO marca (nombre) VALUES
    ('Bosch'), ('DeWalt'), ('Makita'), ('Hilti'), ('Stanley'), ('Black+Decker'), ('Milwaukee'), ('Karcher');

INSERT INTO parametro_global (clave, valor, descripcion) VALUES
    ('cancelacion.horas_minimas', '24',
     'Horas mínimas antes del inicio del alquiler para cancelar sin intervención de soporte'),
    ('cancelacion.politica', 'Puedes cancelar sin costo hasta 24 horas antes de la fecha de inicio. Si ya pagaste, el reembolso se refleja en el mismo medio de pago. Pasado ese plazo, la cancelación debe gestionarse con soporte.',
     'Texto de la política de cancelación mostrado al usuario'),
    ('envio.tarifa_domicilio', '15000',
     'Costo fijo en COP de entrega y recogida a domicilio'),
    ('reserva.dias_maximos', '30',
     'Número máximo de días por reserva'),
    ('reserva.minutos_pago', '15',
     'Minutos que se retiene una reserva pendiente de pago antes de liberarse'),
    ('garantia.texto_base', 'Cada herramienta se entrega probada. Si presenta una falla de fábrica durante el alquiler, la reemplazamos sin costo. Los daños por mal uso o pérdida se cobran según la valoración técnica registrada en la devolución.',
     'Política de garantía por daños que se aplica si la ficha no define una propia'),
    ('legal.politica_datos', 'Hawkify trata tus datos personales (nombre, correo, teléfono, documento, dirección e historial de alquileres) con la única finalidad de gestionar tu cuenta, tus reservas y la comunicación sobre ellas, conforme a la Ley 1581 de 2012. Puedes conocer, actualizar, rectificar y solicitar la supresión de tus datos en cualquier momento desde tu cuenta, en la sección Privacidad. No vendemos ni cedemos tus datos a terceros.',
     'Política de Tratamiento de Datos Personales'),
    ('legal.terminos', 'Al reservar aceptas devolver la herramienta en la fecha pactada y en el estado en que la recibiste. La tarifa se cobra por día calendario, incluyendo el día de entrega y el de devolución. El propietario y Hawkify pueden registrar novedades en la devolución; los daños por mal uso se cobran aparte. Hawkify puede suspender cuentas que incumplan estas condiciones.',
     'Términos y Condiciones de uso');

-- ============================================================================
-- 9. ROW LEVEL SECURITY (Supabase)
-- ============================================================================
-- Toda lectura/escritura pasa por el backend (Spring Boot), que se conecta como
-- dueño de las tablas y por lo tanto no está sujeto a RLS. Habilitar RLS sin
-- políticas cierra la API REST autogenerada de Supabase (anon/authenticated),
-- evitando que alguien con la anon key lea, por ejemplo, password_hash.
DO $$
DECLARE t record;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    END LOOP;
END $$;

COMMIT;
