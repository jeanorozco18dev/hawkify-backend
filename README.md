# Hawkify — Backend

Plataforma de alquiler temporal de herramientas profesionales, maquinaria ligera y equipos de
construcción bajo un modelo de economía circular. Proyecto ADSO23 (SENA).

API REST en **Java 25 + Spring Boot 4 + PostgreSQL** (Supabase en producción). El frontend vive en
el repositorio `hakiwfy-frontend`.

## Documentación

- [`ANALISIS_HAWKIFY.md`](./ANALISIS_HAWKIFY.md) — análisis de requerimientos y decisiones de diseño.
- [`MODELO_BASE_DATOS.md`](./MODELO_BASE_DATOS.md) — diagrama ER y diccionario de datos.
- [`schema.sql`](./schema.sql) — DDL ejecutable (v2), con datos base: roles, permisos, categorías,
  marcas y parámetros globales.

## Puesta en marcha rápida (local con Docker)

```bash
docker compose up -d                 # PostgreSQL 15 con schema.sql ya cargado
cd hawkify-backend
./mvnw spring-boot:run               # http://localhost:8080
```

Al arrancar sobre una base vacía, el **DemoSeeder** carga cuentas, 11 herramientas, cinco meses de
historial de alquileres, reservas en curso, reseñas (una moderada), lista de deseos y
notificaciones. Contraseña de todas las cuentas: `Hawkify2026*`

| Correo | Rol |
|---|---|
| `superadmin@hawkify.co` | Superadministrador |
| `admin@hawkify.co` | Administrador con todos los permisos |
| `bodega@hawkify.co` | Administrador con alcance limitado (sin aprobar ni gestionar usuarios) |
| `laura.gomez@correo.co` | Usuaria final con reservas, reseñas y lista de deseos |
| `andres.rios@correo.co` | Usuario final que publica herramientas (una pendiente de aprobación) |

Desactívalo con `DEMO_SEED=false`. Solo corre si la tabla `producto` está vacía.

## Base de datos en Supabase

1. En Supabase → **SQL Editor**, pega y ejecuta `schema.sql` completo. Crea las 23 tablas, los
   triggers (promedio de calificaciones, historial de tarifa, log de auditoría inmutable) y habilita
   RLS en todas las tablas: así la API REST pública de Supabase (anon key) no puede leer nada. El
   backend entra como dueño de las tablas y no se ve afectado.
2. En **Project Settings → Database → Connection string**, usa el **Session pooler** (puerto 5432).
   El Transaction pooler (6543) no soporta sentencias preparadas.
3. Arranca con:

```bash
export DB_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require"
export DB_USERNAME="postgres.<ref-del-proyecto>"
export DB_PASSWORD="<password-de-la-base>"
export JWT_SECRET="$(openssl rand -base64 48)"
export SUPERADMIN_CORREO="tu-correo@dominio.co"     # crea el superadmin inicial si no existe
export SUPERADMIN_PASSWORD="<clave-segura>"
export DEMO_SEED=false                                # en producción, sin datos de demo
export CORS_ORIGINS="https://tu-frontend.com"
export FRONTEND_URL="https://tu-frontend.com"
./mvnw spring-boot:run
```

### Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hawkify` | Conexión JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Credenciales |
| `DB_POOL_SIZE` | `5` | Conexiones del pool (Supabase gratis limita) |
| `JWT_SECRET` | secreto de desarrollo | Firma de tokens; **cámbialo en producción** |
| `JWT_EXPIRATION_MINUTES` | `480` | Vigencia de la sesión |
| `CORS_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` | Orígenes del frontend |
| `FRONTEND_URL` | `http://localhost:5500` | Base del enlace de recuperación de contraseña |
| `EXPONER_ENLACE_RECUPERACION` | `false` | `true` devuelve el enlace en la respuesta (demos sin correo) |
| `UPLOADS_DIR` | `uploads` | Carpeta de fotos subidas (servidas en `/uploads/**`) |
| `SUPERADMIN_CORREO` / `SUPERADMIN_PASSWORD` | vacío | Bootstrap del primer superadmin |
| `DEMO_SEED` | `true` | Datos de demostración si el catálogo está vacío |
| `SERVER_PORT` | `8080` | Puerto HTTP |

## Arquitectura

Paquetes por módulo de negocio, cada uno con entidades, repositorios, servicio y controlador:

| Paquete | Responsabilidad | RF |
|---|---|---|
| `auth` | Registro con aceptación de política, login JWT, recuperación de contraseña | 01, 02 |
| `perfil` | Datos, contraseña, direcciones, exportar datos y supresión (anonimización) | 03, Habeas Data |
| `catalogo` | Búsqueda con filtros, ficha, disponibilidad por día, publicación y aprobación | 06–11, 30 |
| `reserva` | Cotización, reserva con bloqueo, pago simulado, cancelación, devoluciones | 22–27 |
| `resena` | Calificación por reserva finalizada, reseñas, moderación y reversión | 12–18 |
| `wishlist` | Lista de deseos (los avisos los emite `catalogo`) | 19–21 |
| `notificacion` | Bandeja in-app | 21 |
| `admin` | Alcance por permisos, usuarios, tablero, reportes | 04, 05, 29 |
| `auditoria` | Log append-only (trigger en BD bloquea UPDATE/DELETE) | 28 |
| `parametro` | Políticas y textos legales configurables | 30 |

Decisiones relevantes:

- **Concurrencia (RNF-12):** la reserva toma un bloqueo de fila sobre el producto y la restricción
  `EXCLUDE USING gist` de la tabla `reserva` impide solapamientos aunque dos transacciones lleguen
  a la vez. Probado con reservas simultáneas por la última unidad: una entra, la otra recibe 409.
- **Alcance del administrador (RF-05):** cada admin tiene permisos individuales
  (`usuario_permiso`) y solo opera sobre las herramientas que publicó o aprobó.
- **Tarifa congelada:** la reserva guarda `tarifa_dia_snapshot`; los reportes nunca usan el precio actual.
- **Pago simulado:** tarjeta, PSE o Nequi. Tarjetas terminadas en `0002` se rechazan y en `0069`
  simulan tarjeta bloqueada. Una reserva sin pagar se libera a los 15 minutos (configurable).
- **Errores:** todas las respuestas de error tienen la forma
  `{ status, error, message, fieldErrors }`, con `fieldErrors` por campo en validaciones.

## Endpoints

Todas las rutas bajo `/api`. Autenticación con `Authorization: Bearer <token>`.

### Públicos
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/registro` | `nombreCompleto`, `correo`, `password`, `aceptaPolitica` |
| POST | `/auth/login` | Devuelve `token` y `usuario` |
| POST | `/auth/recuperar` · `/auth/restablecer` | Recuperación de contraseña |
| GET | `/productos` | Filtros: `q`, `categoriaId`, `marcaId`, `precioMin`, `precioMax`, `calificacionMin`, `desde`, `hasta`, `soloDisponibles`, `orden`, `pagina`, `tamano` |
| GET | `/productos/{id}` | Ficha técnica |
| GET | `/productos/{id}/disponibilidad` | Unidades libres por día (`desde`, `hasta`) |
| GET | `/productos/{id}/resenas` | Promedio, distribución y reseñas visibles |
| POST | `/reservas/cotizar` | Desglose y disponibilidad real de un rango |
| GET | `/categorias` · `/marcas` · `/parametros/publicos` | Catálogos y políticas |

### Usuario autenticado
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/auth/me` | Perfil con rol y permisos |
| GET/PUT | `/perfil` · PUT `/perfil/password` | Datos y contraseña |
| CRUD | `/perfil/direcciones` | Direcciones de entrega |
| GET | `/perfil/mis-datos` · POST `/perfil/eliminar-cuenta` | Habeas Data |
| POST | `/reservas` · `/reservas/{id}/pago` · `/reservas/{id}/cancelar` | Flujo de reserva |
| GET | `/reservas` · `/reservas/{id}` | Mis reservas |
| POST | `/reservas/{id}/calificacion` · PUT `/calificaciones/{id}` · DELETE `/resenas/{id}` | Calificar y reseñar |
| GET | `/mis-calificaciones` | Mis reseñas |
| GET/PUT/DELETE | `/lista-deseos[/{productoId}]` | Lista de deseos |
| GET | `/notificaciones` · `/notificaciones/no-leidas` · PATCH `/{id}/leida` · POST `/leer-todas` | Notificaciones |
| GET/POST/PUT | `/mis-productos[/{id}]` · POST `/{id}/pausar` · `/{id}/reanudar` | Publicar herramientas |
| GET | `/mis-productos/reservas` | Reservas sobre mis herramientas |
| POST | `/archivos` | Subir foto (multipart `archivo`, JPG/PNG/WEBP, 5 MB) |

### Staff (`/api/admin/**`, administrador y superadmin)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/admin/resumen` | Tablero (alcance según rol) |
| GET/POST/PUT | `/admin/productos[/{id}]` | Inventario (`alcance=todos\|gestionados\|pendientes`) |
| POST | `/admin/productos/{id}/aprobar\|rechazar\|pausar\|reactivar\|retirar` | Ciclo de vida de la ficha |
| GET | `/admin/productos/{id}/unidades` · PATCH `/admin/unidades/{id}` | Unidades físicas |
| GET | `/admin/reservas` · `/admin/reservas/{id}` | Calendario y listado (`estado`, `q`, `desde`, `hasta`) |
| PATCH | `/admin/reservas/{id}/estado` · POST `/admin/reservas/{id}/devolucion` | Operación de reservas |
| GET/POST/PUT/PATCH | `/admin/usuarios[/{id}[/estado]]` · GET `/admin/permisos` | Usuarios y alcance |
| GET/POST | `/admin/resenas` · `/admin/resenas/{id}/moderar` · `/historial` | Moderación |
| GET | `/admin/calificaciones` | Historial de calificaciones (`maxEstrellas`) |
| GET | `/admin/auditoria` · `/admin/auditoria/entidades` | Log de auditoría |

### Solo superadmin
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/admin/reportes` | KPIs, ingresos por mes, top herramientas, por categoría y estado |
| GET/PUT | `/admin/parametros[/{clave}]` | Parámetros globales y textos legales |
| POST/PUT | `/admin/categorias[/{id}]` | Categorías |
