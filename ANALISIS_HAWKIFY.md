# Análisis del Documento de Requerimientos — Hawkify

**Fuente:** `Hawkify_Documento_Requerimientos.pdf` (v1.0) — Jhon Andres Ponton Parra, ADSO23, SENA
**Propósito de este documento:** interpretar los requerimientos, extraer las reglas de negocio implícitas, detectar vacíos y contradicciones, e identificar las entidades candidatas como paso previo al **modelo de base de datos**.
**Fecha de análisis:** 2026-09-15

---

## 1. Resumen ejecutivo

Hawkify es un **marketplace curado de alquiler de herramientas por días**. La diferencia esencial frente a un e-commerce tradicional —y la que condiciona todo el modelo de datos— es que **la disponibilidad no es un booleano de stock, sino una función del calendario**: un producto puede estar "disponible" hoy y no estarlo del 3 al 9 de octubre.

Esto tiene tres consecuencias técnicas que atraviesan todo el sistema:

1. La tabla de reservas es el **corazón transaccional**, no un simple registro de pedido. Toda consulta de catálogo con filtro de fechas debe cruzarse contra ella.
2. Se necesita **prevención de solapamiento de rangos de fechas** a nivel de base de datos, no solo de aplicación (RNF-12 lo pide explícitamente).
3. El catálogo requiere **datos derivados precalculados** (calificación promedio, disponibilidad) para cumplir el objetivo de < 2 s con filtros (RNF-11).

El documento es sólido en alcance funcional (30 RF, 16 RNF, matriz de roles) pero deja **decisiones de modelado sin resolver** que hay que cerrar antes de dibujar el diagrama ER. Están listadas en la sección 6.

---

## 2. Actores del sistema

| Actor | Naturaleza | Capacidad distintiva |
|---|---|---|
| **Visitante** (no autenticado) | Actor implícito, no nombrado en la matriz | Ve el catálogo y se registra (RF-01). No reserva, no califica. |
| **Usuario Final** | Consumidor | Reserva, califica, reseña, wishlist. **No** administra catálogo. |
| **Administrador** | Operador de inventario | CRUD de catálogo y de usuarios finales, **limitado a su propio inventario**. Registra devoluciones. Modera reseñas de sus herramientas. |
| **Superadministrador** | Auditor / gobierno | Visibilidad y control global. Único con acceso a log de auditoría, reportes y parámetros globales. |
| **Propietario / Comercio aliado** | **Mencionado pero no modelado** | Secciones 1.1 y 1.2 hablan de ferreterías, distribuidores y *"usuarios particulares con herramientas subutilizadas"* que listan equipos. **Ningún RF les da esa capacidad.** Ver §6.3. |

**Observación clave sobre la jerarquía:** los tres roles no son estrictamente acumulativos. La matriz marca ✘ al Superadmin en *"Reservar herramientas"*, *"Calificar"*, *"Redactar reseñas"* y *"Gestionar wishlist"*. Es decir, **el Superadmin no puede actuar como consumidor**. El modelo de permisos debe reflejar esto: no es un escalafón de privilegios, son tres perfiles con capacidades disjuntas y solapadas.

---

## 3. Análisis por módulo

### 3.1 Registro y cuentas (RF-01 a RF-05)

- **RF-01** exige aceptación explícita de la Política de Tratamiento de Datos **antes** de completar el registro. Esto no es un checkbox decorativo: RNF-01 y la Ley 1581 de 2012 exigen poder **demostrar** qué versión del texto legal aceptó cada titular y cuándo. → Implica entidad de **documentos legales versionados** + **registro de aceptación**.
- **RF-02** recuperación de contraseña por correo verificado → tabla de **tokens de recuperación** con expiración, más un flag de correo verificado.
- **RF-03** menciona *dirección de entrega* y *foto de perfil*. La dirección de entrega puede ser una o varias (el documento no lo aclara).
- **RF-04** habla de *desactivar* y *suspender temporalmente* → dos estados distintos, no un booleano `activo`.
- **RF-05** dice que el Superadmin define *"el alcance de sus permisos"* de cada administrador. Esto **descarta un rol como simple ENUM**: exige RBAC con permisos asignables. Ver §6.2.

### 3.2 Catálogo (RF-06 a RF-11)

Atributos de herramienta que el documento nombra de forma dispersa:

| Atributo | Origen |
|---|---|
| nombre, categoría, tarifa/día, imagen | RF-06, RF-09 |
| calificación promedio | RF-06, RF-13 |
| estado de disponibilidad | RF-06 |
| marca | RF-07 (filtro) |
| especificaciones técnicas | RF-08 |
| condiciones de uso | RF-08, RF-09 |
| política de garantía por daños | RF-08 |
| **galería** de imágenes | RF-08 → relación 1:N, no un solo campo |
| stock disponible | RF-09 |
| estado: nuevo / usado / en mantenimiento | RF-09 |

**Tensión importante:** RF-09 pide `stock disponible` (N unidades) mientras RF-22 dice que el calendario *"bloquea automáticamente fechas ya reservadas por otros usuarios"* (lógica de 1 unidad). Con stock = 3, una reserva **no** debe bloquear la fecha; debe decrementar la disponibilidad de ese día. Contradicción a resolver — es la decisión de modelado más importante del proyecto. Ver §6.1.

**"Herramientas bajo su gestión"** (RF-10, RF-11, RF-14, RF-17, RF-25) y la nota al pie de la matriz de roles establecen que **cada herramienta pertenece a un administrador**. → FK obligatoria `producto.administrador_id`, y toda consulta de admin debe filtrarse por ella.

### 3.3 Calificaciones (RF-12 a RF-14) y Reseñas (RF-15 a RF-18)

El documento las trata como **dos módulos separados**, pero comparten precondición: ambas requieren *"una reserva finalizada"* de ese equipo (RF-12 explícito, RF-15 "haya alquilado previamente").

- La precondición no es "el usuario alquiló el producto" sino **"existe una reserva finalizada concreta"** → la calificación debe colgar de la **reserva**, no solo del par usuario-producto. Esto además da la regla de unicidad natural: **una calificación por reserva finalizada**.
- **RF-13** pide promedio *"en tiempo real"*. Combinado con RNF-11 (< 2 s filtrando por calificación mínima), calcular `AVG()` en cada consulta de catálogo no escala. → **Columna desnormalizada** en producto (`calificacion_promedio`, `total_calificaciones`) actualizada por trigger o en la misma transacción.
- **RF-16** permite al usuario editar o eliminar su reseña *"en cualquier momento"*. Choca con RF-17/RF-18 (moderación y auditoría): si el usuario borra físicamente la reseña, el Superadmin no puede auditar ni revertir la moderación. → **Borrado lógico**. Además: si se borra la reseña, ¿se borra la calificación numérica asociada? El documento no lo dice. Recomendación: **no** — la calificación sobrevive, el texto se oculta.
- **RF-17 / RF-18** implican estados de moderación (`visible`, `oculta`, `eliminada`) **más el rastro de quién moderó y por qué**, ya que RF-18 permite *revertir* la decisión de un administrador.

### 3.4 Wishlist (RF-19 a RF-21)

Relación N:M clásica usuario↔producto, con fecha de agregado. Lo no trivial es **RF-21**: notificar cuando el producto pase a disponible **o cambie su tarifa**. Esto exige:

- Detectar el cambio → se necesita conocer el **valor anterior de la tarifa** (historial de precios o comparación en el UPDATE).
- Una entidad **notificación** con destinatario, tipo, estado leído/no leído.
- El documento **no define el canal** (¿correo? ¿in-app? ¿ambos?) ni la frecuencia. Vacío menor, pero afecta al modelo.

### 3.5 Reservas y alquiler (RF-22 a RF-27) — módulo crítico

Flujo derivado de los RF:

```
selección de rango (RF-22) → cálculo de costo (RF-23) → confirmación (RF-24)
   → [en curso] (RF-25) → devolución + estado del equipo (RF-26) → [finalizada]
   → habilita calificación (RF-12) y reseña (RF-15)
```

Estados de reserva explícitos e implícitos en el texto: `confirmada` (RF-24), `cancelada` (RF-24), `en curso`, `finalizada`, `con incidencia` (RF-25). Se sugiere añadir `pendiente` si se introduce pago.

- **RF-23**: costo = tarifa_diaria × n.º de días. La tarifa **debe congelarse en la reserva** (snapshot), porque RF-21 admite que la tarifa del producto cambie. Si se recalcula desde el producto, el histórico y los reportes de RF-29 quedan corruptos.
- **RF-26** (estado de devolución, daños y novedades) es una entidad propia, 1:1 con la reserva, no un campo suelto. Se relaciona con la *"política de garantía por daños"* de RF-08.
- **RNF-12** (condiciones de carrera) exige control a nivel de motor: restricción de exclusión sobre rangos de fechas (PostgreSQL `EXCLUDE ... WITH &&` + `btree_gist`) o transacción con bloqueo explícito de fila y verificación de solapamiento (MySQL). **No basta con validar en el backend.**

### 3.6 Administración y auditoría (RF-28 a RF-30)

- **RF-28 + RNF-07**: log de auditoría **append-only**, inmutable desde la aplicación. Campos mínimos: autor, acción, entidad afectada, id de la entidad, valores previos/nuevos, fecha-hora, IP. La inmutabilidad se garantiza con permisos de BD (el usuario de la app solo tiene INSERT y SELECT sobre esa tabla), no con código.
- **RF-29**: reportes de herramientas más alquiladas, ingresos estimados por periodo y tasa de cancelación. Todos derivables de reservas + snapshot de tarifa, **siempre que no se borren físicamente las reservas canceladas**.
- **RF-30**: categorías configurables por el Superadmin → **categoría es una tabla**, nunca un ENUM. Igual para políticas de cancelación y textos legales.

---

## 4. Requerimientos no funcionales con impacto directo en la base de datos

| RNF | Impacto en el modelo |
|---|---|
| RNF-01 | Tabla de aceptación de políticas con versión del documento, fecha e IP. |
| RNF-02 | Endpoints/consultas de acceso, rectificación y supresión sobre datos del titular. |
| RNF-03 | Campo `password_hash` dimensionado para bcrypt/Argon2 (≥ 255 chars). Nunca columna `password`. |
| RNF-05 | Estructura RBAC: roles, permisos y asignaciones. |
| RNF-06 | **Choca con RF-27 y RF-29**: la supresión de datos personales no puede borrar el histórico transaccional. → Anonimización del usuario conservando la reserva. Ver §6.4. |
| RNF-07 | Tabla de auditoría con permisos restringidos a nivel de motor. |
| RNF-11 | Índices sobre categoría, marca, tarifa, calificación promedio y sobre el rango de fechas de reserva. |
| RNF-12 | Restricción de exclusión / bloqueo transaccional sobre solapamiento de fechas. |
| RNF-14 | Integridad transaccional (ACID) → descarta modelar reservas en un almacén sin transacciones. |
| RNF-15 | Separación en capas → acceso a datos aislado (repositorios / ORM), sin SQL disperso en la vista. |

---

## 5. Entidades candidatas (insumo para el modelo ER)

> Esta lista es el borrador inicial. El modelo final, ya con las decisiones de la sección 6 incorporadas (unidades físicas, propietario = cualquier usuario, pago simulado), está en **`MODELO_BASE_DATOS.md`** junto con el diagrama ER y el diccionario de datos completo, y el DDL ejecutable en **`schema.sql`**.
>
> **Descope posterior:** por decisión del autor, el proyecto no aborda el cumplimiento formal de Ley 1581/Habeas Data (§4.1 del PDF). Las entidades `documento_legal`, `aceptacion_legal` y `solicitud_habeas_data` mencionadas más abajo **no están en el modelo final** — el foco se dejó en que el sistema funcione, no en la trazabilidad legal.

Agrupadas por dominio. Los nombres son preliminares.

**Identidad y acceso**
- `usuario` — datos comunes, `password_hash`, estado (`activo` / `suspendido` / `desactivado`), correo verificado.
- `rol` — superadmin, administrador, usuario_final.
- `permiso` y `rol_permiso` — necesarios por RF-05 y RNF-05.
- `usuario_rol` (o FK directa si se decide rol único).
- `direccion` — dirección de entrega (RF-03).
- `token_recuperacion` — RF-02.

**Legal / Habeas Data**
- `documento_legal` — tipo (política de datos, T&C), versión, contenido, vigencia. (RF-30)
- `aceptacion_legal` — usuario, documento_legal, fecha, IP. (RF-01, RNF-01)
- `solicitud_habeas_data` — tipo (acceso/rectificación/supresión), estado. (RNF-02) *opcional pero recomendable*

**Catálogo**
- `categoria` — configurable por Superadmin (RF-30).
- `marca` — filtro de RF-07.
- `producto` (herramienta) — con `administrador_id`, tarifa diaria, estado, stock, `calificacion_promedio`, `total_calificaciones`.
- `producto_imagen` — galería (RF-08).
- `producto_especificacion` — especificaciones técnicas clave-valor (RF-08), flexible sin alterar el esquema.
- `unidad_producto` — **solo si se adopta el modelo por unidades físicas** (ver §6.1).
- `historial_tarifa` — soporte de RF-21. *opcional*

**Transaccional**
- `reserva` — usuario, producto (o unidad), fecha_inicio, fecha_fin, tarifa_snapshot, días, total, estado.
- `estado_reserva` — catálogo de estados si se quiere configurable; si no, ENUM/CHECK.
- `devolucion` — 1:1 con reserva: fecha real, estado del equipo, daños, observaciones, admin que registra (RF-26).

**Reputación**
- `calificacion` — reserva, estrellas 1-5, fecha. Único por reserva (RF-12).
- `resena` — reserva/calificación, texto, estado de moderación, borrado lógico (RF-15, RF-16).
- `moderacion_resena` — moderador, acción, motivo, fecha; permite la reversión de RF-18.

**Interacción**
- `lista_deseos` / `lista_deseos_item` — RF-19, RF-20.
- `notificacion` — destinatario, tipo, referencia, leída, fecha (RF-21).

**Gobierno**
- `log_auditoria` — append-only (RF-28, RNF-07).
- `parametro_global` — política de cancelación y configuración del sistema (RF-30).

### Trazabilidad RF → entidades (resumen)

| Módulo | RF | Entidades principales |
|---|---|---|
| Cuentas | RF-01…05 | usuario, rol, permiso, direccion, aceptacion_legal, token_recuperacion |
| Catálogo | RF-06…11 | producto, categoria, marca, producto_imagen, producto_especificacion |
| Calificaciones | RF-12…14 | calificacion, producto (agregados) |
| Reseñas | RF-15…18 | resena, moderacion_resena |
| Wishlist | RF-19…21 | lista_deseos_item, notificacion |
| Reservas | RF-22…27 | reserva, devolucion |
| Auditoría | RF-28…30 | log_auditoria, parametro_global, documento_legal, categoria |

---

## 6. Vacíos, contradicciones y decisiones abiertas

Ordenadas por impacto sobre el modelo de datos.

### 6.1 ✅ RESUELTO — Stock múltiple vs. bloqueo de fechas
**Decisión del autor:** se maneja por decremento de disponibilidad; cuando llega a 0 para un rango de fechas dado, ahí sí se bloquea ese rango en el calendario.

**Implementación elegida:** `producto` = ficha de catálogo con N unidades físicas (`unidad_producto`). Cada reserva se asocia a **una unidad concreta**, no al producto directamente. La "disponibilidad" que ve RF-22 en el calendario es el conteo de unidades libres en ese rango de fechas (`stock_total − unidades_reservadas_en_ese_rango`); se bloquea el rango solo cuando ese conteo llega a 0.

Modelar a nivel de unidad (y no como un simple contador en `producto`) es lo que permite usar una restricción de integridad nativa de PostgreSQL (`EXCLUDE ... WITH &&` sobre `unidad_producto_id` + rango de fechas) para resolver RNF-12 sin lógica de aplicación propensa a condiciones de carrera — ver `schema.sql`.

### 6.2 🟠 Alcance de permisos del administrador
RF-05 dice que el Superadmin define *"el alcance de sus permisos sobre el catálogo y los usuarios finales"*, pero el documento nunca enumera qué permisos son configurables. Sin esa lista, un RBAC granular es especulativo.
**Recomendación:** modelar `rol` + `permiso` + `rol_permiso` con un conjunto acotado de permisos derivados de la matriz de la sección 5 del PDF. Es defendible ante el instructor y cumple RNF-05 sin sobredimensionar.

### 6.3 ✅ RESUELTO — El propietario del equipo es cualquier usuario registrado
**Decisión del autor:** cualquier usuario final registrado puede poner en alquiler sus propias herramientas (no exclusivo de Administrador/Superadmin). Esto materializa la visión de negocio de las secciones 1.1/1.2 ("usuarios particulares con herramientas subutilizadas") que los RF-09 a RF-11, leídos de forma literal, no habilitaban.

**Consecuencia sobre el modelo de roles:** `usuario_final` deja de ser un rol puramente consumidor y pasa a tener **dos facetas simultáneas** sobre el mismo registro: arrendador de sus propias herramientas y arrendatario de las de terceros. No se crea un rol nuevo — se añade `producto.propietario_id → usuario.id`.

**Consecuencia sobre el flujo de catálogo:** el modelo "marketplace curado" de la sección 1.3 se conserva como **filtro de calidad posterior a la publicación**, no como filtro de creación: cualquier usuario crea la ficha, pero queda en estado `pendiente_aprobacion` hasta que un Administrador o Superadministrador la revisa y aprueba (o rechaza), igual que hoy modera reseñas (RF-17/RF-18). Esto reinterpreta RF-09/RF-10 ("el administrador debe poder crear/editar/retirar fichas") como **capacidad de aprobar, editar y retirar cualquier ficha del sistema**, no como exclusividad de creación — es una desviación deliberada de la lectura literal del PDF, justificada por la visión de negocio del propio documento, y debe declararse así en la sustentación.

### 6.4 🟠 Supresión de datos (RNF-06) vs. histórico de reservas (RF-27, RF-29)
Un usuario puede exigir la supresión de sus datos, pero el Superadmin debe conservar visibilidad de todas las reservas históricas y generar reportes de ingresos.
**Recomendación:** borrado lógico + **anonimización**: se limpian nombre, correo, teléfono, dirección y foto; la reserva conserva su FK, montos y fechas. Documentarlo en la política de tratamiento.

### 6.5 ✅ RESUELTO — Pagos: sin pasarela real, con simulación
**Decisión del autor:** al ser un proyecto académico, no hay conexión a una pasarela de pago real, pero el software debe **simular** el proceso de pago (flujo completo: método, monto, estado, confirmación), sin transaccionar dinero real.

**Implementación elegida:** tabla `pago_simulado` (1:1 con `reserva`) con método simulado, monto (= `reserva.total`), estado (`pendiente` / `aprobado` / `rechazado`) y una referencia simulada generada por el propio sistema. La confirmación de la reserva (RF-24) queda condicionada a que el pago simulado pase a `aprobado`.

### 6.6 🟡 Relación calificación ↔ reseña sin definir
¿Son una sola entidad o dos? El PDF las separa en módulos distintos, pero ambas dependen de la misma reserva finalizada.
**Recomendación:** dos tablas con `calificacion` obligatoria y `resena` opcional (1:0..1). Permite cumplir RF-16 (borrar la reseña) sin destruir el promedio de RF-13.

### 6.7 🟡 Inconsistencia en los datos personales recolectados
RNF-01 afirma que el sistema procesa *"nombre, documento de identidad, dirección, información de contacto e historial de alquiler"*, pero RF-01 solo pide nombre, correo y contraseña. El **documento de identidad nunca se solicita** en ningún RF.
**Recomendación:** incluirlo en `usuario` como opcional, requerido solo al confirmar la primera reserva (es razonable para un alquiler y alinea el RF con el RNF).

### 6.8 🟡 Cancelación sin reglas concretas
RF-24 remite a *"las condiciones y plazos establecidos por la política de cancelación"*, definida como parámetro global en RF-30, pero no se especifica ninguna regla (¿hasta cuántas horas antes? ¿penalización?).
**Recomendación:** modelar la política como parámetros configurables y no cablear ninguna regla en el esquema.

### 6.9 🟢 Otros puntos menores
- El **visitante no autenticado** no aparece en la matriz de roles: no queda claro si puede ver el catálogo sin registrarse. Se asume que sí (RF-01 habla de "visitante").
- **RF-21** no define el canal de notificación.
- No se especifica **moneda ni impuestos** (se asume COP, sin IVA discriminado).
- No hay requerimiento de **mensajería** entre usuario y administrador, habitual en un marketplace.

---

## 7. Conclusión y siguiente paso

El documento define un alcance **coherente y suficiente** para modelar la base de datos. La arquitectura de datos se organiza en seis dominios: **identidad/RBAC, legal, catálogo, transaccional (reservas), reputación y gobierno/auditoría**.

Las dos características que distinguen este modelo de un e-commerce genérico y que deben resolverse con cuidado son:

1. **Disponibilidad temporal** en lugar de stock plano — requiere consulta de solapamiento de rangos y una restricción de integridad que impida la doble reserva (RNF-12).
2. **Trazabilidad legal y de auditoría** — aceptación versionada de políticas, log inmutable y anonimización en lugar de borrado físico.

Las decisiones bloqueantes (6.1, 6.3, 6.5) fueron cerradas con el autor del proyecto y quedan documentadas arriba. El modelo entidad-relación, el diccionario de datos y el script DDL que las materializan están en **`MODELO_BASE_DATOS.md`** y **`schema.sql`**, en la raíz de este mismo repositorio.
