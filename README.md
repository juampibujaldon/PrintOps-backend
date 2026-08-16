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
- **PostgreSQL 14+** — [Descargar](https://www.postgresql.org/download/)
- **Git**

---

## Configuración de base de datos

Creá la base de datos antes de levantar el servidor:

```sql
-- Conectate a psql y ejecutá:
CREATE DATABASE printops;
CREATE USER tu_usuario WITH PASSWORD 'tu_contraseña';
GRANT ALL PRIVILEGES ON DATABASE printops TO tu_usuario;
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

## Endpoints disponibles

### Autenticación — `/api/auth`

| Método | Ruta | Descripción | Requiere auth |
|---|---|---|:---:|
| `POST` | `/api/auth/register` | Registrar nuevo usuario | No |
| `POST` | `/api/auth/login` | Login → retorna access + refresh token | No |
| `POST` | `/api/auth/refresh` | Renovar access token con refresh token | No |
| `POST` | `/api/auth/forgot-password` | Recuperar contraseña | No |

### Impresoras — `/api/printers`

| Método | Ruta | Descripción | Requiere auth |
|---|---|---|:---:|
| `POST` | `/api/printers` | Registrar nueva impresora (`multipart/form-data`) | ✅ Bearer JWT |
| `GET` | `/api/printers` | Listar todas las impresoras | ✅ Bearer JWT |

### Archivos estáticos

| Ruta | Descripción |
|---|---|
| `GET /uploads/printers/{filename}` | Foto de una impresora (pública) |

---

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
