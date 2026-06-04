# Ejecucion en modo developer (alf.io)

Este documento describe pasos concretos para correr alf.io en modo developer y contrasta la ejecucion con `model_c4.md`.

## 1) Prerequisitos
- **Java 17 (JDK)** para backend.
- **PostgreSQL 10+** (ideal con Docker/Podman).
- **Node.js + pnpm** para frontends.
- **Docker** opcional pero recomendado para la BD.

## 2) Base de datos (elige UNA opcion)

### Opcion A: Docker Compose (BD solamente)
`docker-compose.yml` trae solo el servicio `db` por defecto.
```bash
docker-compose up
```
Usa credenciales desde `.env`:
- DB: `alfio`
- User: `alfio`
- Password: `alfio`
- Host: `localhost:5432`

### Opcion B: Docker run (segun README)
```bash
docker run -d --name alfio-db -p 5432:5432 \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=alfio \
  --restart unless-stopped postgres
```
Con esto, el usuario esperado es `postgres` con password `password`.

> Nota: esta opcion **no coincide** con `.env`. Usa una u otra, pero no mezcles credenciales.

## 3) Backend (Spring Boot)
Desde la raiz del repo:
```bash
./gradlew -Pprofile=dev :bootRun
```
Cuando arranca:
- **Admin UI:** `http://localhost:8080/admin`
- El usuario **admin** y la **password** se imprimen en consola al primer inicio.

## 4) Frontend publico (Angular)
El frontend publico puede correr en su **propio proceso**:
```bash
cd frontend
pnpm install
pnpm run start
```
Por defecto sirve en `http://localhost:4200`.

El proxy ya esta configurado en `frontend/projects/public/proxy.config.json` para apuntar a `http://localhost:8080`:
- `/api`, `/file`, `/openid`, `/logout`, `/resources`

## 5) Frontend admin (Lit + Vite)
```bash
cd frontend/admin
pnpm install
pnpm run dev
```
Mantener el backend corriendo y abrir:
```
http://localhost:8080/admin/
```
El servidor Vite por defecto usa `http://localhost:5173` (ver salida de consola para el puerto real).

## 6) Sitio web/Docs (opcional)
Si quieres levantar el sitio de docs:
```bash
cd website
hugo server
```

---

## Contraste con `model_c4.md`

### Consistencias confirmadas
- **Backend Alf.io** corre como un proceso JVM en `:8080`.
- **Base de datos PostgreSQL** corre como proceso independiente en `:5432`.
- **Public SPA** (Angular) corre como proceso separado (`ng serve`) en `:4200` con proxy a backend.
- **Admin SPA** (Lit + Vite) corre como proceso separado (`vite`) y se integra con el backend en `/admin`.

### Diferencias relevantes a considerar
- **Credenciales de BD**:
  - `docker-compose` usa **alfio/alfio** (ver `.env`).
  - README usa **postgres/password**.
  - Debes elegir **una configuracion** y mantener consistencia en backend/DB.

### Externos (no obligatorios en dev)
Integraciones externas del diagrama (Stripe/PayPal/SendGrid/Mailjet/recaptcha/Wallet) requieren configuracion extra y no son necesarias para iniciar el sistema en modo developer.
