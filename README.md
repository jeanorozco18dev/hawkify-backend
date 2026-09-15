# Hawkify — Backend

Plataforma de alquiler temporal de herramientas profesionales, maquinaria ligera y equipos de
construcción bajo un modelo de economía circular. Proyecto ADSO23 (SENA).

Este repo contiene el **backend** (Java 21 + Spring Boot 4 + PostgreSQL). El frontend vive en otro
repositorio, gestionado por el compañero de equipo encargado de esa parte.

## Documentación del proyecto

- [`ANALISIS_HAWKIFY.md`](./ANALISIS_HAWKIFY.md) — análisis del documento de requerimientos: actores, módulos funcionales, decisiones de diseño y por qué se tomaron.
- [`MODELO_BASE_DATOS.md`](./MODELO_BASE_DATOS.md) — diagrama entidad-relación, diccionario de datos y justificación del esquema.
- [`schema.sql`](./schema.sql) — DDL ejecutable de la base de datos (PostgreSQL).
- [`hawkify-backend/postman/`](./hawkify-backend/postman/) — colección de Postman para probar los endpoints ya implementados.

## Requisitos previos

- **Java 21** (JDK)
- **PostgreSQL 14+** corriendo en local (o accesible por red)
- No hace falta instalar Maven: el proyecto trae `mvnw` (Maven Wrapper)

En macOS, si no tienes Java ni PostgreSQL:

```bash
brew install openjdk@21
brew install --cask postgres-unofficial   # o Postgres.app: https://postgresapp.com
```

## 1. Crear la base de datos

Con PostgreSQL corriendo y `psql` en el PATH:

```bash
psql -h localhost -U <tu_usuario_de_postgres> -d postgres -c "CREATE DATABASE hawkify;"
psql -h localhost -U <tu_usuario_de_postgres> -d hawkify -f schema.sql
```

`schema.sql` crea las 23 tablas, los triggers (recalculo de calificación promedio, historial de
tarifa) y siembra los 3 roles base (`superadmin`, `administrador`, `usuario_final`). Es
autocontenido: no requiere pasos adicionales.

## 2. Configurar la conexión

El backend lee la conexión desde `hawkify-backend/src/main/resources/application.properties`, con
valores por defecto que funcionan en la máquina donde se creó el proyecto. **No edites ese
archivo** para probar en tu máquina — sobreescribe con variables de entorno antes de arrancar:

```bash
export DB_USERNAME=<tu_usuario_de_postgres>
export DB_PASSWORD=<tu_password>   # vacio si tu Postgres usa autenticacion trust
```

Variables disponibles: `DB_NAME` (default `hawkify`), `DB_USERNAME` (default `jean`), `DB_PASSWORD`
(default vacío). El host/puerto están fijos en `localhost:5432`; si tu Postgres corre en otro lado,
avisa y lo parametrizamos también.

## 3. Levantar el backend

```bash
cd hawkify-backend
./mvnw spring-boot:run
```

Debería arrancar en **`http://localhost:8080`**. En consola confirmas que conectó bien si ves algo
como:

```
Database JDBC URL [jdbc:postgresql://localhost:5432/hawkify]
...
Started HawkifyBackendApplication in X seconds
```

Si Hibernate se queja de que una tabla/columna no existe (`ddl-auto=validate`), es que `schema.sql`
no se corrió o corrió contra otra base — revisa el paso 1.

## Endpoints disponibles (para el frontend)

Todos bajo el prefijo `/api`. Respuestas y bodies en JSON.

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/api/auth/registro` | No | Registra un usuario final nuevo (`nombreCompleto`, `correo`, `password`) |
| POST | `/api/auth/login` | No | Autentica y devuelve un JWT (`correo`, `password`) |
| GET | `/api/auth/me` | **Sí** | Devuelve el perfil del usuario autenticado |

Cualquier otra ruta exige autenticación. Para llamar un endpoint protegido, el frontend debe
mandar el token obtenido en `/login`:

```
Authorization: Bearer <token>
```

La colección de Postman en `hawkify-backend/postman/Hawkify-Auth.postman_collection.json` tiene
ejemplos exactos de request/response de cada endpoint (útil como referencia de contrato mientras
se integra el frontend). Se puede importar directo en Postman, o correr por consola con:

```bash
npx newman run hawkify-backend/postman/Hawkify-Auth.postman_collection.json
```

## Estado del proyecto

Implementado: registro de usuarios, login con JWT, endpoint de perfil autenticado.
Pendiente: CRUD de catálogo de herramientas, reservas, calificaciones/reseñas, wishlist — ver
`ANALISIS_HAWKIFY.md` para el detalle completo de requerimientos funcionales (RF-01 a RF-30).
