# C4 (niveles 1 a 3) - Alf.io

Este documento recolecta informacion suficiente para construir diagramas C4 hasta nivel 3 (Contexto, Contenedores y Componentes). No incluye nivel 4.

Fuentes del repositorio (evidencia directa):
- docker-compose.yml (contenedor DB y puertos)
- build.gradle (empaquetado y assets de frontends)
- src/main/java/alfio/** (controllers, managers, repositories, jobs)
- src/main/resources/application.properties (configuracion base)
- frontend/README.md (public SPA Angular)
- frontend/admin/README.md (admin SPA Lit + Vite)
- website/README.md (sitio Hugo)

## Nivel 1 - Contexto (System Context)

### Elementos (atributos obligatorios)
| Nombre | Tipo | Descripcion | Externo/Interno |
|---|---|---|---|
| Organizadores de eventos | Persona | Crean eventos, configuran tickets, controlan ventas y check-in | Externo |
| Asistentes (clientes) | Persona | Buscan eventos, reservan entradas, pagan y reciben tickets | Externo |
| Alf.io | Software System | Sistema de gestion de asistencia y venta de entradas | Interno |
| Pasarelas de pago | Software System | Proveen autorizacion y captura de pagos (Stripe/PayPal/Mollie/Saferpay) | Externo |
| Proveedores de email | Software System | Entregan correos transaccionales (SMTP/Mailgun/Mailjet/SendGrid) | Externo |
| Proveedor de identidad (OpenID/OIDC) | Software System | Autenticacion SSO para administracion | Externo |
| Google reCAPTCHA | Software System | Validacion anti-bot para formularios | Externo |
| Google Wallet | Software System | Generacion/gestion de pases para Google Wallet | Externo |
| Apple Wallet (PassKit) | Software System | Clientes de Apple Wallet consumen pases PKPass | Externo |
| Sitio web/Docs | Software System | Sitio informativo y documentacion (Hugo) | Externo |

### Relaciones (etiqueta obligatoria, sin tecnologia)
| Origen -> Destino | Etiqueta |
|---|---|
| Organizadores de eventos -> Alf.io | Administra eventos, ventas y check-in |
| Asistentes (clientes) -> Alf.io | Consulta eventos, reserva y paga entradas |
| Alf.io -> Pasarelas de pago | Autoriza y captura pagos |
| Alf.io -> Proveedores de email | Envia tickets, recibos y notificaciones |
| Alf.io -> Proveedor de identidad (OpenID/OIDC) | Autentica administradores |
| Alf.io -> Google reCAPTCHA | Verifica formularios |
| Alf.io -> Google Wallet | Genera y actualiza pases |
| Apple Wallet (PassKit) -> Alf.io | Descarga pases PKPass |
| Alf.io -> Sitio web/Docs | Publica informacion y documentacion (fuera de runtime principal) |

---

## Nivel 2 - Contenedores (Containers)

### Contenedores internos (dentro del boundary de Alf.io)
| Nombre | Tipo | Descripcion | Tecnologia | Puerto/Protocolo | Externo/Interno |
|---|---|---|---|---|---|
| Backend Alf.io | Container | API, logica de negocio, render de HTML y servicios internos | Java 17, Spring Boot, Gradle | HTTP/HTTPS :8080 | Interno |
| Public SPA (clientes) | Container | UI publica para explorar eventos y comprar tickets | Angular 15, TypeScript (SPA en navegador) | HTTPS | Interno |
| Admin SPA (organizadores) | Container | UI de administracion (panel, check-in, reportes) | Lit + Shoelace + Vite (SPA en navegador) | HTTPS | Interno |
| Base de datos principal | Container | Persistencia de eventos, tickets, reservas, pagos, usuarios | PostgreSQL 10+ | TCP :5432 | Interno |

### Contenedores externos (fuera del boundary)
| Nombre | Tipo | Descripcion | Tecnologia | Puerto/Protocolo | Externo/Interno |
|---|---|---|---|---|---|
| Pasarelas de pago | Container | Procesamiento de pagos (Stripe/PayPal/Mollie/Saferpay) | APIs HTTPS/SDKs | HTTPS | Externo |
| Proveedores de email | Container | Envio de emails transaccionales | SMTP / HTTPS APIs | SMTP/HTTPS | Externo |
| Proveedor OpenID/OIDC | Container | Login SSO para admin | OIDC/OAuth2 | HTTPS | Externo |
| Google reCAPTCHA | Container | Verificacion anti-bot | HTTPS API | HTTPS | Externo |
| Google Wallet API | Container | Emision/actualizacion de pases | HTTPS API + OAuth SA | HTTPS | Externo |
| Apple Wallet (clientes) | Container | Descarga/actualizacion de pases PKPass | HTTPS (PassKit Web Service) | HTTPS | Externo |
| Sitio web/Docs | Container | Sitio informativo y docs | Hugo (static) | HTTPS | Externo |

### Relaciones (etiqueta + tecnologia)
| Origen -> Destino | Etiqueta | Tecnologia |
|---|---|---|
| Public SPA -> Backend Alf.io | Consume API publica y assets | HTTPS/REST, HTML/CSS/JS |
| Admin SPA -> Backend Alf.io | Consume API admin y assets | HTTPS/REST, HTML/CSS/JS |
| Backend Alf.io -> Base de datos principal | Lee/escribe datos del dominio | JDBC/SQL (PostgreSQL) |
| Backend Alf.io -> Pasarelas de pago | Inicializa pagos, captura, refunds | HTTPS/SDKs |
| Pasarelas de pago -> Backend Alf.io | Webhooks de confirmacion de pago | HTTPS (webhooks) |
| Backend Alf.io -> Proveedores de email | Envia correos y adjuntos | SMTP o HTTPS API |
| Backend Alf.io -> Proveedor OpenID/OIDC | Autentica administradores | OAuth2/OIDC |
| Backend Alf.io -> Google reCAPTCHA | Verifica tokens anti-bot | HTTPS |
| Backend Alf.io -> Google Wallet API | Crea/actualiza pases | HTTPS + OAuth SA |
| Apple Wallet (clientes) -> Backend Alf.io | Descarga pases PKPass | HTTPS |

---

## Nivel 3 - Componentes (Container: Backend Alf.io)

### Contenedor en foco
**Backend Alf.io** (Java 17 + Spring Boot, HTTP :8080). Contiene controladores web/API, logica de negocio (managers), integraciones externas y repositorios.

### Contenedores externos relacionados con este container
- Base de datos PostgreSQL
- Pasarelas de pago (Stripe, PayPal, Mollie, Saferpay)
- Proveedores de email (SMTP/Mailgun/Mailjet/SendGrid)
- Google reCAPTCHA
- Google Wallet API
- Apple Wallet (clientes)
- Proveedor OpenID/OIDC

### Componentes internos (atributos obligatorios + recomendados)
| Nombre | Tipo | Descripcion | Tecnologia | Responsabilidad | Interfaz expuesta |
|---|---|---|---|---|---|
| Public Web UI Controller | Component | Sirve el index del frontend publico y rutas SPA | Spring MVC Controller | Entregar HTML/JS inicial y pre-cargar datos | GET /, /event/{...}, /subscription/{...}, /my-orders |
| Admin Web UI + Auth Controller | Component | Sirve el admin UI y login | Spring MVC Controller + Templates | Render admin index, login, CSP, estado de autenticacion | GET /admin, GET /authentication, POST /authenticate, GET /authentication/status |
| Public API v2 Controllers | Component | API publica para eventos, reservas y tickets | Spring REST Controller | Listar eventos, crear reservas, gestionar tickets | /api/v2/public/** |
| Admin API Controllers | Component | API admin para eventos, pagos, check-in, configuracion | Spring REST Controller | CRUD de eventos, estadisticas, export, check-in | /admin/api/** |
| Payment API + Webhooks | Component | Endpoints de pagos y callbacks | Spring REST Controller | Inicializa transacciones y procesa webhooks | /api/reservation/*/payment/*, /api/payment/webhook/* |
| Wallet/Pass API | Component | API para pases (Apple/Google) | Spring REST Controller | Entrega PKPass y links de Google Wallet | /api/pass/event/{eventName}/v1/**, /api/wallet/event/{eventName}/v1/** |
| Reservation Domain Manager | Component | Orquesta ciclo de vida de reservas y tickets | Spring @Component | Reservas, validaciones, expiraciones, webhooks | Metodos invocados por controllers y jobs |
| Payment Orchestrator | Component | Selecciona proveedor y gestiona estado | Spring @Component | Tokens, estados, refunds, metodos soportados | Metodos internos (PaymentManager) |
| Payment Providers | Component | Implementaciones por proveedor | Spring @Component | Stripe/PayPal/Mollie/Saferpay via SDK/HTTP | Interfaces PaymentProvider, RefundRequest, PaymentInfo |
| Check-In Manager | Component | Gestion de check-in y validacion de tickets | Spring @Component | Check-in online/offline, auditoria | Metodos internos (CheckInManager) |
| Notification/Email Manager | Component | Genera y envia emails con adjuntos | Spring @Component | Tickets PDF, ICS, invoices, passbook | Metodos internos (NotificationManager) |
| Mailer Implementations | Component | Envio por SMTP o APIs externas | Java HttpClient / SMTP | SendGrid, Mailjet, Mailgun, SMTP | Mailer.send(...) |
| File/Blob Manager | Component | Almacen y entrega de archivos | Spring @Component | Uploads, thumbnails, cache, serving | POST /admin/api/file/upload, GET /file/{digest} |
| Wallet Managers | Component | Generacion y actualizacion de pases | Spring @Component | Apple PassKit, Google Wallet | Metodos internos (PassKitManager, GoogleWalletManager) |
| Anti-bot / Security | Component | Verifica reCAPTCHA | Spring @Component | Verificacion anti-bot en login/reservas | RecaptchaService.checkRecaptcha(...) |
| Scheduled Jobs | Component | Tareas periodicas del sistema | Spring @Scheduled | Limpieza, recordatorios, expiraciones | Jobs.* |
| Repository Layer | Component | Acceso a datos del dominio | Spring Repositories/JdbcTemplate | Persistencia de eventos, tickets, reservas, pagos | EventRepository, TicketRepository, TransactionRepository, etc. |

### Relaciones internas (direccionales + tecnologia)
| Origen -> Destino | Etiqueta | Tecnologia |
|---|---|---|
| Public Web UI Controller -> Repository Layer | Carga datos base para render | JDBC/SQL |
| Admin Web UI + Auth Controller -> Configuration Manager | Carga config de login y CSP | Metodo interno |
| Public API v2 Controllers -> Reservation Domain Manager | Crea/actualiza reservas y tickets | Llamada a metodo |
| Public API v2 Controllers -> Payment Orchestrator | Consulta metodos/estado de pago | Llamada a metodo |
| Admin API Controllers -> Event/Reservation Managers | Administra eventos y reservas | Llamada a metodo |
| Admin API Controllers -> Check-In Manager | Ejecuta check-in | Llamada a metodo |
| Payment API + Webhooks -> Reservation Domain Manager | Inicia transaccion y procesa webhooks | Llamada a metodo |
| Reservation Domain Manager -> Payment Orchestrator | Selecciona proveedor y procesa pagos | Llamada a metodo |
| Payment Orchestrator -> Payment Providers | Ejecuta pago/refund/info | SDK/HTTP |
| Reservation Domain Manager -> Notification/Email Manager | Envia tickets y recibos | Llamada a metodo |
| Notification/Email Manager -> Mailer Implementations | Envia email | SMTP / HTTPS |
| Wallet/Pass API -> Wallet Managers | Genera pase y valida ticket | Llamada a metodo |
| Wallet Managers -> Google Wallet API | Crea/actualiza pases | HTTPS + OAuth SA |
| Wallet/Pass API -> Apple Wallet (clientes) | Entrega PKPass | HTTPS |
| File/Blob Manager -> Repository Layer | Guarda/lee blobs | JDBC/SQL + cache FS |
| Scheduled Jobs -> Reservation Domain Manager | Limpieza y expiraciones | Llamada a metodo |
| Scheduled Jobs -> Notification/Email Manager | Envio de cola de emails | Llamada a metodo |
| Check-In Manager -> Repository Layer | Lee/actualiza estado de tickets | JDBC/SQL |
| Anti-bot / Security -> Google reCAPTCHA | Valida token | HTTPS |

### Endpoints clave (para anotar interfaces de componentes)
- Public API: `/api/v2/public/events`, `/api/v2/public/event/{eventName}`, `/api/v2/public/reservation/{reservationId}`, `/api/v2/public/reservation/{reservationId}/transaction/force-check`
- Admin API: `/admin/api/events`, `/admin/api/check-in/**`, `/admin/api/file/upload`
- Payment API: `/api/reservation/{reservationId}/payment/{method}/init`, `/api/reservation/{reservationId}/payment/{method}/status`
- Payment Webhooks: `/api/payment/webhook/stripe/payment`, `/api/payment/webhook/mollie/payment`, `/api/payment/webhook/saferpay/payment`
- Pass/Wallet: `/api/pass/event/{eventName}/v1/version/passes/{...}`, `/api/wallet/event/{eventName}/v1/version/passes/{uuid}`
- Check-in online publico: `/event/{shortName}/ticket/{publicUUID}/check-in/{ticketCodeHash}`

### Notas de implementacion relevantes para el diagrama
- El backend empaqueta y sirve los assets del public SPA y del admin SPA (ver build.gradle y AdminIndexController/IndexController).
- Los jobs se ejecutan en el mismo proceso del backend (no hay contenedor aparte).
- La base de datos Postgres es el unico contenedor de infraestructura declarado en docker-compose.yml.
- Integraciones externas confirmadas en codigo: Stripe, PayPal, Mollie, Saferpay, SendGrid, Mailjet, Mailgun/SMTP, Google reCAPTCHA, Google Wallet, Apple PassKit, OpenID/OIDC.
