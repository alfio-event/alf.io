# Plan de Pruebas - alf.io

> **Versión:** 1.0  
> **Fecha de inicio:** 29 de mayo de 2026  
> **Fecha de fin:** 16 de julio de 2026  
> **Duración total:** 1.5 meses (7 semanas, ~48 días hábiles)  
> **Equipo QA:** 6 integrantes  
> **Metodología:** Ágil (Scrum) con sprints semanales  
> **Fundamentación:** ISTQB CTFL-AT Syllabus v1.0 (Enfoque de Equipo Completo, Pirámide de Pruebas, Cuadrantes Ágiles)

---

## 1. Alcance de las Pruebas y Enfoque Metodológico (ISTQB)

### 1.1. Visión General del Proyecto

`alf.io` es una plataforma de código abierto para la venta y gestión de entradas a eventos. Su arquitectura se compone de:

- **Backend:** Spring Boot 3.5.14 (Java 17+) con arquitectura en capas `Controller → Manager → Repository → PostgreSQL`.
- **Frontend Público:** Angular 17 (reserva de entradas, flujo de checkout, pasarelas de pago).
- **Frontend Admin:** Lit 3 + Shoelace 2 (configuración de eventos, pagos, campos adicionales).
- **Infraestructura:** PostgreSQL 10, Flyway para migraciones, Docker Compose para entornos locales.

### 1.2. Enfoque de Equipo Completo (_Whole-Team Approach_)

Siguiendo el principio ISTQB de involucrar a todos los miembros con el conocimiento necesario para asegurar la calidad (ISTQB CTFL-AT §1.2), la responsabilidad de las pruebas no recae únicamente en los testers, sino en todo el equipo. Cada integrante colabora directamente con el código del módulo asignado para acordar la estrategia de pruebas y los enfoques de automatización. Los desarrolladores y testers trabajan en conjunto durante las reuniones diarias y las sesiones de _pair testing_.

### 1.3. Retroalimentación Temprana y Frecuente (_Early and Frequent Feedback_)

En concordancia con ISTQB CTFL-AT §1.3, se ejecutan pruebas desde el inicio del mes y medio disponible. Cada sprint semanal produce artefactos de prueba ejecutables que proporcionan información continua sobre la calidad del producto. Esto permite descubrir, aislar y resolver defectos antes de que se propaguen a capas superiores.

### 1.4. Integración Continua (_Continuous Integration_)

El proyecto utiliza Gradle como sistema de construcción y dispone de archivos `build.gradle`, `docker-compose.yml` y `settings.gradle` que habilitan la integración continua (ISTQB CTFL-AT §1.4). Las pruebas unitarias y de integración se ejecutan automáticamente en cada push, y el análisis de cobertura de código se realiza con JaCoCo, reportando resultados a SonarCloud (`alfio-event_alf.io`).

### 1.5. Pirámide de Pruebas (_Test Pyramid_)

La distribución del esfuerzo se basa en el principio de pruebas tempranas del ISTQB (CTFL-AT §2.1):

| Nivel | % del esfuerzo | Enfoque |
|-------|:---:|---------|
| **Unitarias (Base)** | 45% | Lógica de negocio en `Manager`, `Model`, `Util`; componentes Angular y Lit |
| **Integración (Medio)** | 30% | Repositorios contra PostgreSQL (Testcontainers), controladores API (WireMock), extensión y jobs |
| **Sistema / E2E (Cima)** | 15% | Flujos completos de reserva y checkout con Selenium, pruebas de API con OpenAPI diff |
| **Exploratorias / Aceptación (Cima)** | 10% | Pruebas manuales en panel admin, flujos de regresión, pruebas exploratorias guiadas por _test charters_ |

### 1.6. Criterios de Entrada y Salida (_Definition of Done - DoD_)

**Criterios de entrada para cada sprint:**
- El código del módulo está compilando sin errores (`./gradlew build` exitoso o `pnpm build` exitoso).
- Las migraciones Flyway están aplicadas correctamente en el entorno de prueba.
- Los endpoints de API están documentados (OpenAPI/Swagger).

**Criterios de salida (DoD por nivel):**
- **Pruebas unitarias:** Cobertura >= 80% en líneas para módulos de negocio críticos (pagos, reservas). Sin fallos en `./gradlew test`.
- **Pruebas de integración:** Todos los repositorios y controladores testeados contra base de datos real (Testcontainers). Stubs de servicios externos verificados (WireMock).
- **Pruebas E2E:** Al menos 3 flujos de negocio completos automatizados con Selenium. Sin defectos de severidad "Crítica" o "Alta" abiertos.
- **Pruebas exploratorias:** Sesiones documentadas con _test charters_. Defectos encontrados registrados en el backlog con prioridad.
- **Pruebas de regresión:** Suite de regresión completa ejecutada sin fallos. Reporte de JaCoCo y SonarCloud sin nuevas incidencias bloqueantes.
- **Cierre del plan:** Documento de lecciones aprendidas. Todos los defectos mayores reportados y resueltos. Suite de pruebas almacenada en repositorio común bajo `src/test/`.

---

## 2. Tipos de Pruebas y Cuadrantes Ágiles

Siguiendo el modelo de Cuadrantes de Pruebas Ágiles del ISTQB (CTFL-AT §2.2), se clasifican y asignan los tipos de prueba:

### 2.1. Cuadrante Q1 - Tecnología / Soporte al Desarrollador

> Pruebas automatizadas orientadas a verificar la calidad interna del código. Se ejecutan en integración continua.

| Tipo de Prueba | Módulo en alf.io | Herramienta | Responsable |
|---|---|---|---|
| **Unitarias Backend** | `alfio.manager` (EventManager, TicketReservationManager, CheckInManager, PaymentManager) | JUnit 5 + Mockito | Alvaro Quispe |
| **Unitarias Backend** | `alfio.model` (Event, Ticket, PromoCodeDiscount, PriceContainer) | JUnit 5 | Alvaro Quispe |
| **Unitarias Backend** | `alfio.manager.payment` (StripeManager, SaferpayManager, MollieManager, PayPalManager) | JUnit 5 + Mockito | Christian Mestaz |
| **Unitarias Backend** | `alfio.config` (Authentication, Security), `alfio.extension` (ExtensionService) | JUnit 5 + Mockito | Luis Sequerios |
| **Unitarias Backend** | `alfio.repository` (consultas SQL anotadas con npjt-extra) | JUnit 5 + Testcontainers | Rodrigo Fernandez |
| **Unitarias Frontend** | Servicios Angular (`EventService`, `ReservationService`, `UserService`, `TicketService`) | Karma + Jasmine | Mariel Jara |
| **Unitarias Frontend** | Componentes Lit Admin (`additional-item-edit`, `additional-field-edit`, `configuration`) | Vitest | Mathias Barrios |
| **Cobertura de Código** | Proyecto completo (backend + frontend) | JaCoCo + SonarCloud | Todo el equipo |

### 2.2. Cuadrante Q2 - Negocio / Confirmación de Comportamiento

> Pruebas funcionales automatizadas que validan el cumplimiento de los criterios de aceptación de las historias de usuario.

| Tipo de Prueba | Módulo en alf.io | Herramienta | Responsable |
|---|---|---|---|
| **Integración de API v2** | `alfio.controller.api.v2.user` (EventApiController, ReservationApiController) | JUnit 5 + Spring MockMvc + Testcontainers | Rodrigo Fernandez |
| **Integración de API Admin** | `alfio.controller.api.admin` (CheckInApiController, ConfigurationApiController) | JUnit 5 + Spring MockMvc + Testcontainers | Rodrigo Fernandez |
| **Integración de Pasarelas de Pago** | `alfio.controller.payment.api` (stripe, mollie, saferpay, paypal webhooks) | JUnit 5 + WireMock + Testcontainers | Christian Mestaz |
| **Flujo de Checkout Frontend** | `frontend/projects/public/src/app/reservation/` (booking → overview → payment → success) | Protractor / Cypress | Mariel Jara |
| **Formularios y Validaciones** | `frontend/projects/public/src/app/` (ticket-form, invoice-form, additional-service) | Karma + Jasmine | Mariel Jara |
| **Gestión de Eventos Admin** | `frontend/admin/src/event/` (additional-items, custom-payment-selector) | Vitest + Playwright | Mathias Barrios |
| **Pruebas de Contrato API** | Comparación de especificaciones OpenAPI entre versiones | OpenAPI Diff | Rodrigo Fernandez |

### 2.3. Cuadrante Q3 - Negocio / Crítica al Producto

> Pruebas manuales y exploratorias que evalúan la experiencia real del usuario final, detectando problemas no previstos en los casos de prueba automatizados.

| Tipo de Prueba | Alcance | Técnica | Responsable |
|---|---|---|---|
| **Exploratorias - Reserva de Entradas** | Flujo completo de compra: selección de evento → cantidad de tickets → checkout → pago → confirmación | Sesiones con _test charter_ de 60 min | Mariel Jara |
| **Exploratorias - Panel Admin** | Configuración de eventos, precios, descuentos, promociones, campos adicionales | Sesiones con _test charter_ de 45 min | Mathias Barrios |
| **Exploratorias - Pagos y Reembolsos** | Flujos con tarjetas de prueba, cancelaciones, pagos offline, reembolsos parciales | Sesiones con _test charter_ de 60 min | Christian Mestaz |
| **Exploratorias - Seguridad y Permisos** | Escalación de privilegios, acceso a rutas no autorizadas, manipulación de tokens | Sesiones con _test charter_ de 45 min | Luis Sequerios |
| **Exploratorias - Gestión de Check-in** | Escaneo de tickets, lista de asistentes, estadísticas de asistencia | Sesiones con _test charter_ de 45 min | Alvaro Quispe |
| **UAT (User Acceptance Testing)** | Validación final con criterios de negocio definidos por el Product Owner | Sesión formal de revisión con stakeholders | Todo el equipo |

### 2.4. Cuadrante Q4 - Tecnología / Crítica al Producto

> Pruebas no funcionales automatizadas que verifican atributos de calidad como rendimiento, seguridad y estabilidad.

| Tipo de Prueba | Alcance | Herramienta | Responsable |
|---|---|---|---|
| **Rendimiento - API** | Endpoints críticos de reserva y consulta de eventos bajo carga concurrente | JMeter / k6 | Alvaro Quispe |
| **Rendimiento - Base de Datos** | Consultas SQL de repositorios con volúmenes altos de tickets y reservas | pgbench + Testcontainers | Rodrigo Fernandez |
| **Seguridad - Dependencias** | Análisis de vulnerabilidades en dependencias Java (Gradle) y Node.js (pnpm) | OWASP Dependency-Check / npm audit | Luis Sequerios |
| **Seguridad - Autenticación** | Pruebas de inyección, XSS, CSRF, manipulación de JWT, OpenID Connect | OWASP ZAP + pruebas manuales | Luis Sequerios |
| **Estabilidad y Recuperación** | Comportamiento ante caídas de PostgreSQL, timeouts de pasarelas de pago, fallos en jobs de background | Docker Compose con inyección de fallos | Luis Sequerios |
| **Compatibilidad** | Navegadores (Chrome, Firefox, Edge), dispositivos móviles en flujo de compra | BrowserStack / Selenium Grid | Mathias Barrios |

---

## 3. Entorno de Pruebas y Herramientas

### 3.1. Entorno Técnico

| Componente | Tecnología | Versión |
|---|---|---|
| **Lenguaje Backend** | Java | 17+ |
| **Framework Backend** | Spring Boot | 3.5.14 |
| **Lenguaje Frontend** | TypeScript | 5.x |
| **Framework Frontend Público** | Angular | 17.x |
| **Framework Frontend Admin** | Lit + Shoelace | Lit 3 / Shoelace 2.x |
| **Base de Datos** | PostgreSQL | 10 (producción) / 16 (test containers) |
| **Migraciones** | Flyway | integrado en Spring Boot |
| **Construcción** | Gradle (backend) + pnpm (frontend) | Gradle 8.x / pnpm 11.x |
| **Contenedores** | Docker + Docker Compose | `docker-compose.yml` |
| **CI/CD** | GitHub Actions | configurado en `.github/` |

### 3.2. Herramientas de Prueba

| Herramienta | Propósito | Nivel |
|---|---|---|
| **JUnit 5 (Jupiter)** | Framework de pruebas unitarias y de integración para Java | Backend |
| **Mockito** | Mocking de dependencias en pruebas unitarias | Backend |
| **Testcontainers** | Contenedores Docker efímeros para PostgreSQL en pruebas de integración | Backend |
| **WireMock** | Simulación de APIs externas (Stripe, PayPal, Mollie, Saferpay) | Backend |
| **Spring MockMvc** | Pruebas de controladores REST sin levantar servidor HTTP | Backend |
| **JaCoCo** | Cobertura de código Java | Backend |
| **OpenAPI Diff** | Pruebas de contrato y retrocompatibilidad de API REST | Backend |
| **Karma + Jasmine** | Pruebas unitarias de componentes y servicios Angular | Frontend |
| **Vitest** | Pruebas unitarias de componentes Lit | Frontend |
| **Selenium WebDriver** | Automatización de navegador para pruebas E2E | E2E |
| **OWASP Dependency-Check** | Análisis de vulnerabilidades en dependencias | Seguridad |
| **OWASP ZAP** | Análisis dinámico de seguridad en aplicaciones web | Seguridad |
| **SonarCloud** | Análisis estático de código y calidad (`alfio-event_alf.io`) | Calidad |

### 3.3. Datos de Prueba

- **Fixtures de base de datos:** Scripts SQL en `src/test/resources/alfio/db/PGSQL/`.
- **JSON de transacciones:** Fixtures en `src/test/resources/transaction-json/` y `wallet-json/`.
- **APIs externas simuladas:** Stubs de WireMock para Stripe, PayPal, Mollie, Saferpay con respuestas predefinidas.
- **Tarjetas de prueba:** Tarjetas sandbox proporcionadas por cada pasarela (Stripe test cards, PayPal sandbox).
- **Variables de entorno:** Archivo `.env` con configuración para entorno local de pruebas.

---

## 4. Distribución de Módulos por Integrante y Cronograma

### 4.1. Asignación de Módulos

| Integrante | Módulo Principal | Paquetes en alf.io | Enfoque de Pruebas |
|---|---|---|---|
| **Christian Mestaz** | Integración de Pagos | `alfio.manager.payment.*`, `alfio.controller.payment.*`, `alfio.model.transaction.*` | Pruebas unitarias de PaymentManagers (Stripe, PayPal, Mollie, Saferpay, Revolut, Custom/Offline), pruebas de integración con WireMock de webhooks de pago, pruebas exploratorias de flujos de pago y reembolso, validación de tokens y capacidades de pago |
| **Alvaro Quispe** | Lógica de Negocio Core (Eventos y Reservas) | `alfio.manager` (EventManager, TicketReservationManager, CheckInManager, WaitingQueueManager), `alfio.model` (Event, Ticket, TicketReservation, PromoCodeDiscount), `alfio.manager.support.reservation` | Pruebas unitarias de Managers de negocio, pruebas de cálculo de precios y descuentos, pruebas de estados de reserva (PENDING, COMPLETE, CANCELLED), pruebas de check-in, pruebas de rendimiento en API de reserva |
| **Mariel Jara** | Frontend Público Angular (Flujo de Compra) | `frontend/projects/public/src/app/` (reservation, payment, booking, ticket-form, overview, success) | Pruebas unitarias de servicios Angular (EventService, ReservationService, TicketService), pruebas de componentes de formularios de compra, pruebas de pasarelas de pago en frontend (proxies), pruebas de guards y interceptors, pruebas exploratorias de UX de compra |
| **Rodrigo Fernandez** | Acceso a Datos y API REST | `alfio.repository.*`, `alfio.controller.api.v2.*`, `alfio.controller.api.admin.*`, `alfio.model.api.v2.*`, `alfio.db.*` | Pruebas de integración de repositorios con Testcontainers (PostgreSQL real), pruebas de controladores API v2 y Admin con MockMvc, pruebas de contrato OpenAPI, validación de migraciones Flyway, pruebas de rendimiento de consultas SQL |
| **Luis Sequerios** | Seguridad, Infraestructura y Extensiones | `alfio.config.*`, `alfio.config.authentication.*`, `alfio.extension.*`, `alfio.job.*`, `alfio.util.oauth2` | Pruebas de autenticación/autorización (Spring Security, JWT, OpenID), pruebas de extensiones y scripting (Rhino JS), pruebas de jobs en background (AssignTicketToSubscriber, RetryFailedExtension), pruebas de seguridad (OWASP ZAP, Dependency-Check), pruebas de estabilidad y recuperación ante fallos |
| **Mathias Barrios** | Frontend Admin (Lit) y Automatización E2E | `frontend/admin/src/` (Lit web components), `src/test/java/alfio/e2e/`, `frontend/projects/public/e2e/` | Pruebas unitarias de Web Components Lit (Vitest), pruebas de componentes admin (additional-items, additional-fields, payment config), pruebas E2E con Selenium (flujos completos de compra), automatización de suite de regresión, pruebas de compatibilidad multi-navegador |

### 4.2. Cronograma de Sprints (29 de mayo - 16 de julio)

#### Sprint 0: Planificación y Diseño de Pruebas (29 mayo - 4 junio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Analizar managers de pago existentes (`StripeManager`, `SaferpayManager`, `MollieManager`, `PayPalManager`). Identificar casos de prueba de caja negra (partición de equivalencia en montos, valores límite en cantidades). Diseñar stubs de WireMock para cada pasarela. | 20h |
| Alvaro Quispe | Revisar `EventManager` y `TicketReservationManager`. Diseñar matriz de estados de reserva. Identificar reglas de negocio críticas (límites de tickets, vencimiento de reservas). Diseñar casos de prueba de tabla de decisión para descuentos (PromoCodeDiscount). | 20h |
| Mariel Jara | Revisar estructura de componentes Angular en `reservation/`. Identificar flujos de usuario en el checkout. Diseñar casos de prueba para validaciones de formularios. Configurar Karma + Jasmine para el proyecto Angular 17. | 20h |
| Rodrigo Fernandez | Revisar repositorios y controladores API v2. Configurar Testcontainers con PostgreSQL. Diseñar fixtures de base de datos para pruebas de integración. Revisar migraciones Flyway existentes (`alfio/db/PGSQL/`). | 20h |
| Luis Sequerios | Revisar `alfio.config.authentication` y `alfio.extension`. Identificar vectores de ataque comunes (OWASP Top 10). Configurar OWASP Dependency-Check en Gradle. Diseñar _test charters_ de seguridad. | 20h |
| Mathias Barrios | Revisar Web Components Lit en `frontend/admin/`. Configurar Vitest para Lit. Diseñar casos de prueba E2E principales (happy path de compra, cancelación, check-in). Configurar Selenium WebDriver con `NormalFlowE2ETest` como base. | 20h |
| **Total Sprint 0** | | **120h** |

#### Sprint 1: Pruebas Unitarias - Backend (5 junio - 11 junio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Implementar pruebas unitarias de `StripeManager`, `SaferpayManager`, `MollieManager`, `PayPalManager` con Mockito. Mock de clientes HTTP externos. | 20h |
| Alvaro Quispe | Implementar pruebas unitarias de `EventManager`, `TicketReservationManager`. Pruebas de `CostCalculator`, categorías de tickets, `PromoCodeDiscount`. | 20h |
| Mariel Jara | Implementar pruebas unitarias de servicios Angular: `ReservationService`, `TicketService`, `EventService`, `UserService`. Pruebas de guards e interceptors. | 15h |
| Rodrigo Fernandez | Implementar pruebas unitarias de repositorios con Testcontainers (`EventRepository`, `TicketRepository`). Pruebas de consultas SQL anotadas. | 20h |
| Luis Sequerios | Implementar pruebas unitarias de `ExtensionService`, `ScriptingExecutionService`. Pruebas de autenticación y configuración de Spring Security. | 20h |
| Mathias Barrios | Implementar pruebas unitarias de Web Components Lit (additional-item-edit, additional-field-edit, configuration-dialogs). | 15h |
| **Total Sprint 1** | | **110h** |

#### Sprint 2: Pruebas de Integración - Backend (12 junio - 18 junio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Implementar pruebas de integración de webhooks de pago con WireMock. Simular respuestas de Stripe, PayPal, Mollie, Saferpay. Pruebas de flujo de pago completo (inicio → confirmación → webhook). | 20h |
| Alvaro Quispe | Implementar pruebas de integración de `TicketReservationManager` con base de datos real (Testcontainers). Pruebas de concurrencia en reservas. Pruebas de vencimiento de reservas. | 20h |
| Mariel Jara | Implementar pruebas de integración de componentes Angular con servicios reales. Pruebas de comunicación con API v2 del backend. Validación de formularios de checkout. | 15h |
| Rodrigo Fernandez | Implementar pruebas de integración de controladores API v2 (`EventApiControllerIntegrationTest`, `ReservationFlow*`). Pruebas de contrato OpenAPI. Validación de DTOs y respuestas JSON. | 20h |
| Luis Sequerios | Implementar pruebas de integración de jobs en background (`AssignTicketToSubscriberJobExecutorIntegrationTest`). Pruebas de extensiones con scripts Rhino reales. | 20h |
| Mathias Barrios | Implementar pruebas de integración de componentes Admin Lit con API del backend. Pruebas de flujo de configuración de pagos offline, campos adicionales, items adicionales. | 15h |
| **Total Sprint 2** | | **110h** |

#### Sprint 3: Pruebas de Componente y UI - Frontend (19 junio - 25 junio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Pruebas de integración de frontend de pagos: validar proxies de Stripe, PayPal, Mollie, Offline en Angular. Pruebas de flujo completo de pago desde la UI hasta el webhook de confirmación. | 15h |
| Alvaro Quispe | Implementar pruebas de API de check-in. Pruebas de flujo de escaneo de ticket con QR (ZXing). Pruebas de listas de asistentes. | 15h |
| Mariel Jara | Pruebas de componentes de reserva: booking, overview, ticket-form, success, processing-payment. Pruebas de estados de UI (loading, error, success). Pruebas de responsive design en flujo de compra. | 20h |
| Rodrigo Fernandez | Pruebas de API Admin (CheckInApiController, ConfigurationApiController). Pruebas de endpoints v1 heredados para retrocompatibilidad. Documentación de diferencias de API detectadas. | 15h |
| Luis Sequerios | Pruebas de seguridad en endpoints API. Pruebas de CSRF, XSS, inyección SQL en parámetros de búsqueda. Pruebas de manipulación de JWT y tokens de sesión. | 20h |
| Mathias Barrios | Automatización de pruebas E2E con Selenium: flujo feliz de compra (`NormalFlowE2ETest`). Validación de pantallas de éxito, descarga de tickets PDF, envío de emails. | 20h |
| **Total Sprint 3** | | **105h** |

#### Sprint 4: Pruebas de Sistema y E2E (26 junio - 2 julio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Pruebas exploratorias de pasarelas de pago. Validación manual de flujos de pago reales en entornos sandbox. Pruebas de reembolsos y cancelaciones de pago. | 20h |
| Alvaro Quispe | Pruebas de rendimiento con JMeter/k6 sobre endpoints de reserva. Simulación de 100+ usuarios concurrentes comprando entradas. Análisis de tiempos de respuesta y cuellos de botella. | 20h |
| Mariel Jara | Pruebas exploratorias de UX de compra. Sesiones de _test charter_ en flujo de checkout. Pruebas en dispositivos móviles (responsive). Pruebas de internacionalización (i18n) en diferentes idiomas. | 20h |
| Rodrigo Fernandez | Pruebas de rendimiento de base de datos: consultas SQL complejas con grandes volúmenes de datos. Pruebas de migraciones Flyway en escenarios de upgrade. | 15h |
| Luis Sequerios | Pruebas de estabilidad: inyección de fallos en PostgreSQL, timeouts de pasarelas. Pruebas de recuperación de jobs fallidos. Pruebas de OWASP ZAP sobre toda la superficie de ataque. | 20h |
| Mathias Barrios | Automatización de 3 flujos E2E completos: compra con Stripe, compra con PayPal, compra con pago offline. Pruebas de regresión visual con capturas de pantalla automatizadas. | 20h |
| **Total Sprint 4** | | **115h** |

#### Sprint 5: Pruebas No Funcionales, Exploratorias y Seguridad (3 julio - 9 julio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Pruebas exploratorias adicionales de pago. Validación de escenarios edge: pagos duplicados, timeouts, fondos insuficientes, monedas múltiples. Reporte de defectos. | 15h |
| Alvaro Quispe | Pruebas de aceptación de usuario (UAT) en flujos de reserva. Validación de reglas de negocio con stakeholders. Documentación de resultados de rendimiento. | 15h |
| Mariel Jara | Pruebas exploratorias de todo el flujo de compra. Sesiones de _pair testing_ con Christian Mestaz para integración frontend-pasarela. Reporte de defectos de UX. | 20h |
| Rodrigo Fernandez | Pruebas de regresión de API v1 vs v2. Validación de retrocompatibilidad. Pruebas de límites de paginación y filtros en endpoints de búsqueda. | 15h |
| Luis Sequerios | Pruebas de penetración manuales adicionales. Verificación de OWASP Top 10 (2021). Análisis de vulnerabilidades de dependencias con `npm audit` y OWASP Dependency-Check. | 20h |
| Mathias Barrios | Pruebas de compatibilidad multi-navegador. Ejecución de suite E2E en Chrome, Firefox, Edge. Pruebas en diferentes resoluciones y dispositivos móviles. | 15h |
| **Total Sprint 5** | | **100h** |

#### Sprint 6: Regresión Final, Reporte y Cierre (10 julio - 16 julio) — 1 semana

| Integrante | Actividades | Horas |
|---|---|---|
| Christian Mestaz | Ejecutar suite completa de pruebas de pago. Análisis de cobertura con JaCoCo en módulo de pagos (objetivo >= 80%). Documentar lecciones aprendidas del módulo de pagos. | 20h |
| Alvaro Quispe | Ejecutar suite completa de pruebas de negocio. Reporte de cobertura final (JaCoCo). Documentar matriz de trazabilidad de requisitos vs pruebas. Cerrar defectos pendientes. | 20h |
| Mariel Jara | Ejecutar suite completa de pruebas frontend Angular. Reporte de cobertura de código frontend. Documentar hallazgos exploratorios. Elaborar informe de UX. | 20h |
| Rodrigo Fernandez | Ejecutar suite completa de pruebas de API y repositorios. Generar reporte OpenAPI Diff final. Validar integridad de migraciones Flyway en último entorno. | 20h |
| Luis Sequerios | Ejecutar suite completa de pruebas de seguridad. Generar reporte de vulnerabilidades (OWASP ZAP + Dependency-Check). Documentar riesgos residuales de seguridad. | 20h |
| Mathias Barrios | Ejecutar suite completa E2E. Generar reporte de regresión visual. Consolidar informe final de calidad. Coordinar retrospectiva del equipo. | 20h |
| **Total Sprint 6** | | **120h** |

### 4.3. Resumen de Horas por Integrante

| Integrante | S0 | S1 | S2 | S3 | S4 | S5 | S6 | **Total** |
|---|---|---|---|---|---|---|---|---|
| Christian Mestaz | 20h | 20h | 20h | 15h | 20h | 15h | 20h | **130h** |
| Alvaro Quispe | 20h | 20h | 20h | 15h | 20h | 15h | 20h | **130h** |
| Mariel Jara | 20h | 15h | 15h | 20h | 20h | 20h | 20h | **130h** |
| Rodrigo Fernandez | 20h | 20h | 20h | 15h | 15h | 15h | 20h | **125h** |
| Luis Sequerios | 20h | 20h | 20h | 20h | 20h | 20h | 20h | **140h** |
| Mathias Barrios | 20h | 15h | 15h | 20h | 20h | 15h | 20h | **125h** |
| **Total semanal** | **120h** | **110h** | **110h** | **105h** | **115h** | **100h** | **120h** | **780h** |

> **Nota:** La carga horaria está calculada sobre una base de ~20h semanales por integrante (media jornada), consistente con un proyecto académico de 1.5 meses. El total de 780 horas representa el esfuerzo colectivo del equipo.

---

## 5. Gestión de Riesgos de Calidad

### 5.1. Identificación de Riesgos de Producto (_Product Risks_)

En concordancia con ISTQB CTFL-AT §3.1, se identifican los siguientes riesgos de calidad que podrían afectar al producto, clasificados por nivel de impacto y probabilidad.

| ID | Riesgo | Categoría | Impacto | Probabilidad | Nivel | Estrategia de Mitigación |
|---|---|---|---|---|---|---|
| **R01** | Errores en el cálculo de precios, impuestos o descuentos en la reserva de tickets | Reglas de Negocio | Crítico | Alto | **Crítico** | Pruebas unitarias exhaustivas de `CostCalculator`, `FeeCalculator` y `PromoCodeDiscount`. Técnicas de partición de equivalencia en rangos de precios y tablas de decisión para combinaciones de descuentos. |
| **R02** | Fallos en la integración con pasarelas de pago (Stripe, PayPal, Mollie, Saferpay) que impidan completar transacciones | Integración | Crítico | Alto | **Crítico** | WireMock para simular todas las pasarelas. Pruebas de webhook con firmas y respuestas de cada proveedor. Validación de idempotencia en notificaciones duplicadas. Monitoreo de cambios en APIs externas. |
| **R03** | Pérdida o inconsistencia de datos de reservas en la base de datos | Datos | Crítico | Medio | **Alto** | Pruebas de integración con Testcontainers y PostgreSQL real. Validación de transaccionalidad con `@Transactional`. Pruebas de migraciones Flyway forward y rollback. |
| **R04** | Vulnerabilidades de seguridad que expongan datos de usuarios o permitan acceso no autorizado al panel admin | Seguridad | Crítico | Medio | **Alto** | OWASP ZAP + Dependency-Check. Pruebas de autenticación JWT/OpenID. Pruebas de CSRF, XSS, SQL Injection. Validación de permisos por rol (Admin, Owner, User). |
| **R05** | Experiencia de usuario deficiente en el flujo de checkout que cause abandono de compra | Usabilidad | Alto | Medio | **Alto** | Pruebas exploratorias en dispositivos móviles y desktop. Pruebas de validación de formularios con mensajes de error claros. Pruebas de rendimiento de carga de página. |
| **R06** | Degradación de rendimiento en eventos con alta demanda (múltiples usuarios comprando simultáneamente) | Rendimiento | Alto | Medio | **Alto** | Pruebas de carga con JMeter/k6 simulando +100 usuarios concurrentes. Pruebas de concurrencia en `TicketReservationManager`. Monitoreo de tiempos de respuesta de endpoints críticos. |
| **R07** | Fallos en la generación de tickets PDF, códigos QR o pases de wallet (Apple/Google) | Funcional | Medio | Medio | **Medio** | Pruebas unitarias de generación de PDF (openhtmltopdf) y QR (ZXing). Pruebas de integración de Wallet (PassKit). Validación de formatos de salida. |
| **R08** | Incompatibilidad con navegadores o dispositivos que usen los compradores | Compatibilidad | Medio | Bajo | **Medio** | Pruebas multi-navegador con Selenium (Chrome, Firefox, Edge). Pruebas responsive en viewports de móvil y tablet. |
| **R09** | Fallos en el sistema de extensiones/plugins que afecten funcionalidades personalizadas | Infraestructura | Medio | Bajo | **Medio** | Pruebas de `ExtensionService` con scripts Rhino JS reales. Pruebas de aislamiento de extensiones fallidas. Pruebas de jobs `RetryFailedExtension`. |
| **R10** | Regresiones introducidas por cambios en la API que rompan la compatibilidad con el frontend | Integración | Alto | Medio | **Alto** | Pruebas de contrato OpenAPI con OpenAPI Diff. Suite de regresión automatizada ejecutada en cada sprint. Comunicación constante entre backend (Rodrigo Fernandez) y frontend (Mariel Jara, Mathias Barrios). |

### 5.2. Estrategia de Mitigación por Nivel de Riesgo

| Nivel de Riesgo | Estrategia |
|---|---|
| **Crítico** (R01, R02) | Pruebas automatizadas en CI/CD bloqueantes. Sin pruebas verdes, no hay despliegue. Tests diseñados con técnicas de caja negra (partición de equivalencia, valores límite). Suite de regresión específica que se ejecuta en cada commit. |
| **Alto** (R03, R04, R05, R06, R10) | Pruebas de integración y sistema automatizadas. Pruebas manuales exploratorias en cada sprint. Reporte de defectos prioritario. Validación cruzada entre integrantes (ej. Rodrigo y Mariel verifican integración API-frontend). |
| **Medio** (R07, R08, R09) | Pruebas automatizadas básicas + sesiones exploratorias programadas. Monitoreo post-despliegue. |

### 5.3. Estrategia de Regresión

- **Suite de regresión automatizada:** Compuesta por todas las pruebas unitarias (JUnit + Karma + Vitest) y de integración (Testcontainers + WireMock). Ejecutada en CI en cada push y de forma completa al final de cada sprint.
- **Suite de regresión E2E:** Compuesta por al menos 3 flujos de negocio completos automatizados con Selenium. Ejecutada al final de cada sprint a partir del Sprint 4.
- **Regresión manual:** Sesiones exploratorias guiadas por _test charters_ enfocadas en áreas modificadas en cada sprint.
- **Monitoreo de cobertura:** JaCoCo con umbral mínimo de 80% en líneas para módulos críticos (pagos, reservas). SonarCloud para detección de nuevas incidencias.

### 5.4. Matriz de Trazabilidad (Requisitos vs Pruebas)

| Requisito Funcional | Pruebas Unitarias | Pruebas de Integración | Pruebas E2E | Pruebas Exploratorias |
|---|---|---|---|---|
| Crear/editar/eliminar eventos | `EventManagerTest` | `EventApiControllerIntegrationTest` | `NormalFlowE2ETest` | Sesión charter Admin eventos |
| Configurar categorías de tickets | `TicketCategory` tests | `ConfigurationApiControllerIntegrationTest` | — | Sesión charter Config Admin |
| Flujo de reserva de tickets | `TicketReservationManagerTest` | `ReservationFlowIntegrationTest` | `NormalFlowE2ETest` | Sesión charter checkout |
| Pasarelas de pago (Stripe, PayPal, Mollie, Offline) | `*ManagerTest` (Stripe, PayPal, etc.) | WireMock webhook tests | `StripeFlowE2ETest` | Sesión charter pagos |
| Aplicación de descuentos (PromoCode) | `PromoCodeDiscountTest` | `ReservationFlowIntegrationTest` | `NormalFlowE2ETest` | Sesión charter pricing |
| Check-in y escaneo de entradas | `CheckInManagerTest` | `CheckInApiControllerIntegrationTest` | — | Sesión charter check-in |
| Panel de administración (configuración) | Vitest tests (Lit components) | Admin API integration tests | — | Sesión charter admin |
| Extensiones y plugins | `ExtensionServiceTest` | `AssignTicketToSubscriberJobExecutorIntegrationTest` | — | — |
| Gestión de usuarios y organizaciones | `UserManagerTest` | `OrganizationApiControllerIntegrationTest` | — | Sesión charter permisos |
| Generación de PDFs, entradas, QR | `TicketTest`, `EventUtilTest` | `DownloadTicketComponent` tests | `NormalFlowE2ETest` | — |

### 5.5. Definición de Terminado (_Definition of Done_) del Plan de Pruebas

El plan de pruebas se considera completado cuando:

1. Se han ejecutado todas las pruebas planificadas en los 7 sprints (780 horas totales).
2. La suite de pruebas unitarias alcanza >= 80% de cobertura en módulos críticos (pagos, reservas, seguridad).
3. No existen defectos de severidad "Crítica" o "Alta" en estado abierto.
4. Las pruebas de regresión E2E automatizadas (3 flujos de negocio) se ejecutan sin fallos.
5. Los reportes de OWASP ZAP y Dependency-Check no muestran vulnerabilidades de severidad "Crítica" sin mitigación.
6. El reporte final de calidad ha sido revisado y aprobado por el equipo completo.
7. Se ha realizado la retrospectiva final y se han documentado las lecciones aprendidas.

---

## Referencias

- ISTQB Certified Tester Foundation Level - Agile Tester (CTFL-AT) Syllabus v1.0
- Código fuente de alf.io: `src/main/java/alfio/` (backend Spring Boot) y `frontend/` (Angular + Lit)
- Build: `build.gradle`, `settings.gradle`, `docker-compose.yml`
- Documentación interna: `docs/index.md`, `some_conceptos.md`
