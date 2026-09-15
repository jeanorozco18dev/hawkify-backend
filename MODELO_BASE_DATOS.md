# Modelo de Base de Datos — Hawkify

**Motor:** PostgreSQL (ver `schema.sql` para el DDL ejecutable, incluye extensión `btree_gist`).
**Basado en:** `ANALISIS_HAWKIFY.md`, con las decisiones de la sección 6 ya incorporadas:

1. Disponibilidad por **decremento de unidades** (no producto = 1 unidad, no contador puro): `producto` tiene N `unidad_producto`, cada reserva ocupa una unidad concreta en un rango de fechas.
2. **Cualquier usuario registrado** puede publicar sus propias herramientas (`producto.propietario_id`); Admin/Superadmin **aprueban** la publicación (filtro de calidad), no la crean en exclusiva.
3. **Pago simulado**: sin pasarela real, con tabla `pago_simulado` que reproduce el flujo (pendiente → aprobado/rechazado).
4. **Tarifa congelada** en la reserva (`tarifa_dia_snapshot`) para no corromper histórico si el producto cambia de precio.

---

## 1. Diagrama entidad-relación

```mermaid
erDiagram
    ROL ||--o{ USUARIO : "tiene"
    ROL ||--o{ ROL_PERMISO : "otorga"
    PERMISO ||--o{ ROL_PERMISO : "concedido en"

    USUARIO ||--o{ DIRECCION : "registra"
    USUARIO ||--o{ TOKEN_RECUPERACION : "solicita"

    USUARIO ||--o{ PRODUCTO : "posee (propietario_id)"
    USUARIO ||--o{ PRODUCTO : "revisa (administrador_revisor_id)"
    CATEGORIA ||--o{ PRODUCTO : "clasifica"
    MARCA ||--o{ PRODUCTO : "identifica"
    PRODUCTO ||--o{ PRODUCTO_IMAGEN : "galería"
    PRODUCTO ||--o{ PRODUCTO_ESPECIFICACION : "detalla"
    PRODUCTO ||--o{ HISTORIAL_TARIFA : "registra cambios de"
    PRODUCTO ||--o{ UNIDAD_PRODUCTO : "compuesto por"

    USUARIO ||--o{ RESERVA : "reserva"
    UNIDAD_PRODUCTO ||--o{ RESERVA : "es reservada en"
    RESERVA ||--o| DEVOLUCION : "cierra con"
    RESERVA ||--o| PAGO_SIMULADO : "paga con"
    USUARIO ||--o{ DEVOLUCION : "registra (admin)"

    RESERVA ||--o| CALIFICACION : "habilita"
    USUARIO ||--o{ CALIFICACION : "califica"
    PRODUCTO ||--o{ CALIFICACION : "recibe"
    CALIFICACION ||--o| RESENA : "complementa"
    RESENA ||--o{ MODERACION_RESENA : "es moderada"
    USUARIO ||--o{ MODERACION_RESENA : "modera (admin)"

    USUARIO ||--o{ LISTA_DESEOS_ITEM : "guarda"
    PRODUCTO ||--o{ LISTA_DESEOS_ITEM : "es guardado en"
    USUARIO ||--o{ NOTIFICACION : "recibe"

    USUARIO ||--o{ LOG_AUDITORIA : "genera (actor)"
    USUARIO ||--o{ PARAMETRO_GLOBAL : "configura (superadmin)"

    USUARIO {
        uuid id PK
        uuid rol_id FK
        string nombre_completo
        string correo UK
        string password_hash
        string telefono
        string documento_identidad "opcional"
        string foto_perfil_url
        bool correo_verificado
        string estado "activo|suspendido|desactivado"
        timestamp creado_en
    }
    ROL {
        smallint id PK
        string nombre "superadmin|administrador|usuario_final"
    }
    PERMISO {
        smallint id PK
        string codigo UK
        string descripcion
    }
    ROL_PERMISO {
        smallint rol_id FK
        smallint permiso_id FK
    }
    DIRECCION {
        uuid id PK
        uuid usuario_id FK
        string etiqueta
        string linea1
        string ciudad
        string departamento
        bool predeterminada
    }
    TOKEN_RECUPERACION {
        uuid id PK
        uuid usuario_id FK
        string token_hash
        timestamp expira_en
        bool usado
    }
    CATEGORIA {
        uuid id PK
        string nombre UK
        bool activa
    }
    MARCA {
        uuid id PK
        string nombre UK
    }
    PRODUCTO {
        uuid id PK
        uuid propietario_id FK
        uuid administrador_revisor_id FK "nullable"
        uuid categoria_id FK
        uuid marca_id FK
        string nombre
        text descripcion
        text condiciones_uso
        text politica_garantia
        numeric tarifa_dia
        string estado_publicacion "pendiente_aprobacion|aprobado|rechazado|pausado|retirado"
        string estado_fisico "nuevo|usado|en_mantenimiento"
        numeric calificacion_promedio "desnormalizado"
        int total_calificaciones "desnormalizado"
        timestamp creado_en
    }
    PRODUCTO_IMAGEN {
        uuid id PK
        uuid producto_id FK
        string url
        int orden
        bool es_principal
    }
    PRODUCTO_ESPECIFICACION {
        uuid id PK
        uuid producto_id FK
        string clave
        string valor
    }
    HISTORIAL_TARIFA {
        uuid id PK
        uuid producto_id FK
        numeric tarifa_dia
        timestamp vigente_desde
        timestamp vigente_hasta "nullable"
    }
    UNIDAD_PRODUCTO {
        uuid id PK
        uuid producto_id FK
        string codigo_interno UK
        string estado "disponible|en_mantenimiento|retirada"
    }
    RESERVA {
        uuid id PK
        uuid usuario_id FK
        uuid unidad_producto_id FK
        date fecha_inicio
        date fecha_fin
        int dias "calculado"
        numeric tarifa_dia_snapshot
        numeric total
        string estado "pendiente_pago|confirmada|en_curso|finalizada|cancelada|con_incidencia"
        text motivo_cancelacion "nullable"
        timestamp creado_en
    }
    PAGO_SIMULADO {
        uuid id PK
        uuid reserva_id FK UK
        numeric monto
        string metodo_simulado "tarjeta_simulada|transferencia_simulada"
        string estado "pendiente|aprobado|rechazado"
        string referencia_simulada
        timestamp fecha_pago
    }
    DEVOLUCION {
        uuid id PK
        uuid reserva_id FK UK
        uuid registrado_por FK
        timestamp fecha_real_devolucion
        string estado_equipo "sin_novedad|con_daño|perdida"
        text observaciones
    }
    CALIFICACION {
        uuid id PK
        uuid reserva_id FK UK
        uuid usuario_id FK
        uuid producto_id FK
        smallint estrellas "1-5"
        timestamp creado_en
    }
    RESENA {
        uuid id PK
        uuid calificacion_id FK UK
        text texto
        string estado_moderacion "visible|oculta|eliminada"
        timestamp creado_en
        timestamp eliminado_en "nullable"
    }
    MODERACION_RESENA {
        uuid id PK
        uuid resena_id FK
        uuid moderador_id FK
        string accion "ocultar|eliminar|revertir"
        text motivo
        timestamp fecha
    }
    LISTA_DESEOS_ITEM {
        uuid id PK
        uuid usuario_id FK
        uuid producto_id FK
        timestamp agregado_en
    }
    NOTIFICACION {
        uuid id PK
        uuid usuario_id FK
        string tipo "disponibilidad|cambio_tarifa|reserva|moderacion|sistema"
        string referencia_tipo
        uuid referencia_id
        string mensaje
        bool leida
        timestamp creado_en
    }
    LOG_AUDITORIA {
        uuid id PK
        uuid actor_id FK
        string accion
        string entidad
        uuid entidad_id
        jsonb valores_previos
        jsonb valores_nuevos
        timestamp fecha
        inet ip
    }
    PARAMETRO_GLOBAL {
        uuid id PK
        string clave UK
        string valor
        text descripcion
        uuid actualizado_por FK
        timestamp actualizado_en
    }
```

---

## 2. Decisiones de diseño clave (resumen ejecutivo)

### 2.1 Disponibilidad: `unidad_producto` + restricción de exclusión

`producto` es la ficha de catálogo (nombre, tarifa, categoría...). `unidad_producto` representa cada ejemplar físico. La reserva se hace sobre una **unidad concreta**, nunca sobre el producto directamente.

Disponibilidad de un producto en un rango `[f1, f2]` = número de `unidad_producto` de ese producto que **no** tienen ninguna reserva activa solapada con `[f1, f2]`. Cuando ese número llega a 0, el rango se bloquea en el selector de fechas (RF-22).

La concurrencia (RNF-12) se resuelve **a nivel de motor**, no de aplicación, con:

```sql
EXCLUDE USING gist (
    unidad_producto_id WITH =,
    daterange(fecha_inicio, fecha_fin, '[]') WITH &&
) WHERE (estado NOT IN ('cancelada'))
```

Esto hace que **PostgreSQL rechace** cualquier INSERT que intente reservar la misma unidad en fechas solapadas, incluso bajo transacciones concurrentes — no hay ventana de carrera posible, a diferencia de un `SELECT` de disponibilidad seguido de un `INSERT` separado.

### 2.2 Propietario vs. administrador revisor

`producto.propietario_id` → cualquier `usuario` (quien publica y cobra). `producto.administrador_revisor_id` → el Administrador o Superadmin que aprobó/rechazó la ficha (nullable mientras está `pendiente_aprobacion`). Ambas son FKs a la misma tabla `usuario`; no se duplica la entidad.

Flujo de estados de `estado_publicacion`: `pendiente_aprobacion → aprobado | rechazado`, y desde `aprobado` → `pausado` (el propio propietario o un admin) → `retirado` (baja definitiva, admin/superadmin, RF-10).

### 2.3 Tarifa congelada

`reserva.tarifa_dia_snapshot` copia `producto.tarifa_dia` en el momento de la reserva. `reserva.total = tarifa_dia_snapshot × dias`. Los reportes de RF-29 e ingresos siempre usan el snapshot, nunca el precio actual del producto. `historial_tarifa` guarda el rastro completo para poder disparar la notificación de RF-21 cuando el precio cambia.

### 2.4 Pago simulado

`pago_simulado` es 1:1 con `reserva`. No hay integración real; el "estado" se resuelve dentro de la misma aplicación (por ejemplo, siempre `aprobado` tras un formulario ficticio, o con una probabilidad simulada de rechazo si se quiere enriquecer la demo). `reserva.estado` pasa de `pendiente_pago` a `confirmada` solo cuando `pago_simulado.estado = 'aprobado'`.

### 2.5 Calificación vs. reseña

Una `calificacion` por `reserva` (UNIQUE en `reserva_id`), obligatoria solo tras reserva `finalizada`. `resena` es 1:0..1 sobre `calificacion`, con borrado **lógico** (`estado_moderacion = 'eliminada'`, `eliminado_en`) para que la auditoría de RF-18 pueda revertir una moderación incluso después de que el usuario "elimine" su reseña.

### 2.6 Desnormalización controlada

`producto.calificacion_promedio` y `producto.total_calificaciones` se recalculan con un **trigger** en cada INSERT/UPDATE/DELETE de `calificacion` (ver `schema.sql`), no con `AVG()` en cada consulta de catálogo — necesario para cumplir RNF-11 (< 2 s con filtros).

### 2.7 Auditoría inmutable

`log_auditoria` es append-only a nivel de permisos de PostgreSQL: el rol de aplicación tiene `GRANT INSERT, SELECT` pero **no** `UPDATE`/`DELETE` sobre esa tabla (RNF-07). Ver sección de roles en `schema.sql`.

---

## 3. Diccionario de datos

### `usuario`
| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| id | uuid | PK | |
| rol_id | smallint | FK → rol, NOT NULL | superadmin / administrador / usuario_final |
| nombre_completo | varchar(150) | NOT NULL | |
| correo | varchar(150) | UNIQUE, NOT NULL | |
| password_hash | varchar(255) | NOT NULL | bcrypt/Argon2 (RNF-03) |
| telefono | varchar(30) | | |
| documento_identidad | varchar(30) | NULL | opcional al registro, requerido antes de la 1ª reserva (§6.7 del análisis) |
| foto_perfil_url | text | NULL | |
| correo_verificado | boolean | DEFAULT false | |
| estado | varchar(20) | CHECK IN ('activo','suspendido','desactivado') | RF-04 |
| creado_en / actualizado_en | timestamptz | | |

### `producto`
| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| id | uuid | PK | |
| propietario_id | uuid | FK → usuario, NOT NULL | cualquier usuario (decisión §6.3) |
| administrador_revisor_id | uuid | FK → usuario, NULL | quien aprobó/rechazó |
| categoria_id | uuid | FK → categoria, NOT NULL | |
| marca_id | uuid | FK → marca, NULL | |
| tarifa_dia | numeric(10,2) | NOT NULL, CHECK > 0 | |
| estado_publicacion | varchar(25) | CHECK IN (...) | ver §2.2 |
| estado_fisico | varchar(20) | CHECK IN ('nuevo','usado','en_mantenimiento') | RF-09 |
| calificacion_promedio | numeric(3,2) | DEFAULT 0 | desnormalizado, trigger |
| total_calificaciones | integer | DEFAULT 0 | desnormalizado, trigger |

### `unidad_producto`
| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| id | uuid | PK | |
| producto_id | uuid | FK → producto, NOT NULL | |
| codigo_interno | varchar(50) | UNIQUE | trazabilidad física por unidad |
| estado | varchar(20) | CHECK IN ('disponible','en_mantenimiento','retirada') | independiente del estado de sus reservas |

### `reserva`
| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| id | uuid | PK | |
| usuario_id | uuid | FK → usuario, NOT NULL | quien reserva (arrendatario) |
| unidad_producto_id | uuid | FK → unidad_producto, NOT NULL | |
| fecha_inicio / fecha_fin | date | NOT NULL, CHECK fecha_fin >= fecha_inicio | |
| dias | integer | GENERATED (fecha_fin - fecha_inicio + 1) | |
| tarifa_dia_snapshot | numeric(10,2) | NOT NULL | congelada, §2.3 |
| total | numeric(12,2) | NOT NULL | |
| estado | varchar(20) | CHECK IN ('pendiente_pago','confirmada','en_curso','finalizada','cancelada','con_incidencia') | |
| — | — | EXCLUDE USING gist (...) | ver §2.1, resuelve RNF-12 |

### `calificacion`
| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| reserva_id | uuid | FK → reserva, **UNIQUE**, NOT NULL | una calificación por reserva finalizada |
| estrellas | smallint | CHECK BETWEEN 1 AND 5 | RF-12 |

### `pago_simulado`
| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| reserva_id | uuid | FK → reserva, **UNIQUE**, NOT NULL | 1:1 |
| estado | varchar(20) | CHECK IN ('pendiente','aprobado','rechazado') | simulado, sin pasarela real |

*(El resto de tablas — `rol`, `permiso`, `direccion`, `token_recuperacion`, `categoria`, `marca`, `producto_imagen`, `producto_especificacion`, `historial_tarifa`, `devolucion`, `resena`, `moderacion_resena`, `lista_deseos_item`, `notificacion`, `log_auditoria`, `parametro_global` — están completamente definidas con tipos, PKs, FKs y CHECKs en `schema.sql`, que es la fuente de verdad ejecutable.)*

---

## 4. Trazabilidad de requerimientos críticos

| Requerimiento | Mecanismo en el modelo |
|---|---|
| RF-13 (promedio en tiempo real) | Trigger sobre `calificacion` → actualiza `producto.calificacion_promedio` |
| RF-16 (editar/eliminar reseña propia) | Borrado lógico en `resena.estado_moderacion` |
| RF-18 (revertir moderación) | `moderacion_resena` conserva historial completo de acciones |
| RF-21 (notificar cambio de tarifa) | `historial_tarifa` + trigger que compara con el valor anterior |
| RF-22 (bloqueo de fechas) | Consulta de `unidad_producto` libres vía `EXCLUDE` |
| RF-23 (cálculo automático) | `reserva.dias` generado, `total` calculado en aplicación desde el snapshot |
| RF-28 (log inmutable) | Permisos de PostgreSQL: rol de app sin UPDATE/DELETE en `log_auditoria` |
| RNF-03 (hash de contraseña) | `password_hash varchar(255)`, nunca texto plano |
| RNF-06 (supresión con histórico intacto) | Anonimización de `usuario`, FKs de `reserva` se mantienen (ON DELETE RESTRICT, nunca CASCADE hacia reservas) |
| RNF-07 (auditoría inmutable) | Ver RF-28 |
| RNF-11 (< 2s con filtros) | Índices en `producto(categoria_id, tarifa_dia, calificacion_promedio)` + columnas desnormalizadas |
| RNF-12 (condiciones de carrera) | `EXCLUDE USING gist` sobre `unidad_producto_id` + rango de fechas |
