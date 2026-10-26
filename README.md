# PrintOps — Backend

API REST del sistema de gestión de impresoras 3D **PrintOps**, construida con **Java 21 + Spring Boot 4 + PostgreSQL**.

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Seguridad | Spring Security + JWT (JJWT 0.12.6) |
| Persistencia | Spring Data JPA + Hibernate |
| Base de datos | PostgreSQL 14+ |
| Build | Gradle (Kotlin DSL) |

---

## Prerrequisitos

- **Java 21** — [Descargar](https://adoptium.net/)
- **PostgreSQL 15.19-1-+** — [Descargar](https://www.postgresql.org/download/)
- **Git**

---

## Configuración de base de datos

Creá la base de datos antes de levantar el servidor:

```sql
-- Conectate a psql y ejecutá:
CREATE DATABASE printops;
CREATE USER printopsadmin WITH PASSWORD 'printops1234';
GRANT ALL PRIVILEGES ON DATABASE printops TO printopsadmin;
```

> **Nota:** El esquema de tablas se genera automáticamente al iniciar la app (`ddl-auto: update`).

---

## Configuración local

El archivo de configuración principal es `src/main/resources/application.yaml`.

Ajustá los siguientes valores según tu entorno local antes de ejecutar:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/printops
    username: TU_USUARIO_POSTGRES
    password: TU_PASSWORD_POSTGRES

jwt:
  secret: "cHJpbnRvcHMtc2VjcmV0LWtleS0yMDI0LW11c3QtYmUtYXQtbGVhc3QtMzItY2hhcnM="
  expiration: 900000            # 15 minutos (ms)
  refresh-expiration: 604800000 # 7 días (ms)

server:
  port: 8080
```

> ⚠️ En producción nunca commitees credenciales reales. Usá variables de entorno o un gestor de secretos.

---

## Cómo levantar el proyecto

### 1. Clonar el repositorio

```bash
git clone https://github.com/juampibujaldon/PrintOps-backend.git
cd PrintOps-backend
```

### 2. Configurar la base de datos

Editá `src/main/resources/application.yaml` con tus credenciales de PostgreSQL (ver sección anterior).

### 3. Ejecutar con Gradle

```bash
# macOS / Linux
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

El servidor queda disponible en: **`http://localhost:8080`**

### 4. Build de producción (opcional)

```bash
./gradlew build
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

---

## API

La documentación completa (todos los endpoints, con sus DTOs) la genera springdoc:

- `GET /v3/api-docs` — OpenAPI 3 en JSON (el front genera sus tipos con `npm run gen:api`).
- `GET /swagger-ui.html` — explorador interactivo.

Se desactiva con `OPENAPI_ENABLED=false`.

Grupos principales: `/api/auth`, `/api/printers` (incluye `/telemetry` e `/{id}/integration`),
`/api/orders`, `/api/parts`, `/api/quotes` (incluye `/{id}/share`), `/api/jobs`, `/api/spools`,
`/api/suppliers`, `/api/purchase-orders`, `/api/metrics` (dashboard, `/predictions`,
`/profitability`), `/api/activity`, `/api/exports/{orders|quotes|parts|movements|jobs}.csv`,
`/api/public/quotes/{token}` (link para el cliente) y `/api/agent/telemetry` (agente local).

Los listados de órdenes, presupuestos y notificaciones aceptan `page`/`size` y responden
`{content, page, size, totalElements, totalPages, last}`.

## Base de datos y migraciones

El esquema lo manejan las migraciones de Flyway en `src/main/resources/db/migration`
(`ddl-auto=validate`: la app no arranca si las entidades no coinciden con las tablas).
Una base creada antes de Flyway se marca como V1 automáticamente (`baseline-on-migrate`)
y solo recibe las migraciones nuevas. Para cambiar el esquema: agregar un `Vn__descripcion.sql`.

## Variables de entorno (producción)

| Variable | Uso |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Perfil de producción (el Dockerfile ya lo define) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres en formato JDBC (`jdbc:postgresql://host:puerto/base`) |
| `JWT_SECRET` | `openssl rand -base64 32` |
| `REDIS_URL` | Cache del dashboard y contadores de rate limit (opcional: sin Redis funciona igual) |
| `CORS_ALLOWED_ORIGINS` | Orígenes del front (con el proxy de nginx del front no hace falta) |
| `FRONTEND_URL` | URL pública del front, para los links de presupuestos compartidos |
| `APP_BASE_URL` | URL pública del backend |
| `STORAGE_TYPE` | `s3` (recomendado) o `local` |
| `S3_BUCKET`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION` | Bucket privado (Cloudflare R2: endpoint `https://<account>.r2.cloudflarestorage.com`, región `auto`) |
| `STORAGE_LOCAL_DIR` | Con `local`: carpeta montada en un Volume (sin volumen, las fotos se pierden en cada deploy) |
| `GOOGLE_CLIENT_IDS` | Client IDs aceptados para "Continuar con Google" |
| `RESEND_API_KEY`, `RESEND_FROM` | Emails (verificación, invitaciones, órdenes de compra) |
| `AUTH_COOKIE_SECURE` | `true` en producción (cookie del refresh token solo por HTTPS) |

Health check: `GET /actuator/health/readiness` (Railway lo usa en cada deploy, ver `railway.json`).

## Agente local de impresoras

En `agent/` hay un programa Node que corre en la red del taller, lee OctoPrint, Moonraker
(Klipper) y Bambu Lab y envía la telemetría a `/api/agent/telemetry`. Ver `agent/README.md`.

## Tests

```bash
./gradlew test                     # usa Testcontainers (necesita Docker)
# Sin Docker, contra una base local descartable (el nombre debe terminar en _test):
createdb printops_it_test
TEST_JDBC_URL=jdbc:postgresql://localhost:5432/printops_it_test TEST_DB_PASSWORD=... ./gradlew test
```

Cada corrida limpia la base y aplica las migraciones de Flyway, así que los tests también
validan los scripts.

## Estructura del proyecto

```
src/
└── main/
    ├── java/com/printops/demo/
    │   ├── config/          # SecurityConfig, JwtAuthFilter, WebMvcConfig
    │   ├── controller/      # AuthController, PrinterController
    │   ├── dto/             # LoginRequest, RegisterRequest, AuthResponse, ...
    │   ├── entity/          # Printer, User, Role, PrinterStatus (enum)
    │   ├── exception/       # GlobalExceptionHandler
    │   ├── repository/      # PrinterRepository, UserRepository, ...
    │   └── service/         # PrinterService, AuthService, JwtService
    └── resources/
        └── application.yaml
```

---

## Seguridad

- Autenticación **stateless** con JWT (access token: 15 min | refresh token: 7 días).
- Rutas públicas: `/api/auth/**` y `/uploads/**`.
- Todas las demás rutas requieren el header `Authorization: Bearer <token>`.
- Contraseñas hasheadas con **BCrypt**.

---

## Notas de desarrollo

- Las fotos se guardan en `uploads/printers/` (relativo al directorio de ejecución). La carpeta se crea automáticamente al iniciar.
- El `qrCodeData` de cada impresora se genera con UUID via `@PrePersist` — no hace falta enviarlo desde el cliente.
- El schema se sincroniza en cada arranque (`ddl-auto: update`). En producción se recomienda migrar a **Flyway**.





hola mundo