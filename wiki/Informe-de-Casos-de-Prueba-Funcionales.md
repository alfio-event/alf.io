
# Informe de Pruebas del Sistema alf.io

## Índice

1. Introducción
2. Propósito
3. Alcance
4. Referencias
5. Entorno de pruebas
   5.1. Configuración del entorno
6. Limitaciones
7. Estrategia y métodos de prueba aplicados
   7.1. Técnicas de diseño de pruebas
8. Integración continua
9. Conclusión

---

## 1. Introducción

El presente informe documenta la estrategia, ejecución y resultados de las pruebas funcionales y no funcionales realizadas sobre alf.io, un sistema de gestión y venta de entradas para eventos de código abierto. Su propósito es evaluar la calidad del software, identificar defectos potenciales y verificar el cumplimiento de los requisitos del sistema, desde unidades de código hasta flujos completos de usuario.

## 2. Propósito

Este documento sirve como referencia para:

- Describir el enfoque de pruebas adoptado y los niveles de cobertura alcanzados.
- Detallar la configuración del entorno de pruebas.
- Proporcionar evidencia sobre el comportamiento del sistema en escenarios controlados.
- Facilitar la reproducibilidad de las pruebas por parte del equipo de desarrollo y QA.

## 3. Alcance

Las pruebas abarcan la totalidad del sistema alf.io, incluyendo:

- Backend (Java 17, Spring Boot 3.5, Jetty): lógica de negocio, repositorios de datos, flujos de reserva, pagos, generación de entradas, gestión de eventos y suscripciones, procesamiento de extensiones JavaScript (Rhino), y migraciones de base de datos.
- Frontend público (Angular 17): interfaz de compra de entradas, visualización de eventos, formularios de registro.
- Frontend de administración (Lit + Shoelace + Vite): panel de administración para organizadores de eventos.
- API REST: endpoints públicos y privados.
- Integraciones externas: pasarelas de pago (Stripe, PayPal), plataformas de correo (SendGrid, Mailjet), y servicios de mapas.

No se incluyen en el alcance las pruebas de infraestructura subyacente ni las pruebas de rendimiento en producción.

## 4. Referencias

- ISO/IEC/IEEE 29119: estándar internacional para pruebas de software.
- Documentación oficial: [https://alf.io](https://alf.io)
- Repositorio oficial: [https://github.com/alfio-event/alf.io](https://github.com/alfio-event/alf.io)
- SonarCloud: [https://sonarcloud.io/project/overview?id=alfio-event_alf.io](https://sonarcloud.io/project/overview?id=alfio-event_alf.io)
- Codecov: [https://codecov.io/gh/alfio-event/alf.io](https://codecov.io/gh/alfio-event/alf.io)

## 5. Entorno de pruebas

### 5.1. Configuración del entorno

Las pruebas se ejecutan en dos modalidades:

- **Local (desarrollo):**
    - Clonación del repositorio y ejecución mediante Gradle Wrapper (`./gradlew`).
    - Base de datos PostgreSQL 10+ en contenedor Docker (`docker run -d --name alfio-db -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_DB=alfio postgres`).
    - Entorno de desarrollo Spring Boot con perfil `dev`, servidor embebido Jetty en `localhost:8080`.
    - Sistema operativo: cualquier distribución compatible con JDK 17 y Docker.

- **CI/CD (GitHub Actions - ubuntu-latest):**
    - La suite completa de pruebas se ejecuta en cada Pull Request a través del workflow `test.yml`.
    - Matriz de tres versiones de PostgreSQL (10, 15, 16) para verificar compatibilidad.
    - Se generan informes de cobertura con Jacoco y se suben a Codecov tras validación.


## 6. Limitaciones

- El acceso a servicios externos (pasarelas de pago, servicios de correo) se realiza mediante simulación sin conexión real a producción.
- No se realizan pruebas de carga ni de rendimiento bajo estrés.
- Las pruebas no cubren la validación de la infraestructura en la nube ni del entorno de producción.
- Las extensiones JavaScript se prueban con Rhino directamente, pero no se cubren todos los casos de uso reales.

## 7. Estrategia y métodos de prueba aplicados

### 7.1. Técnicas de diseño de pruebas

- **Partición por equivalencia:** Los datos de entrada se agrupan en clases válidas e inválidas. Por ejemplo, los roles de usuario (administrador, organizador, operador de check-in) se prueban para verificar que cada uno tenga acceso exclusivo a las operaciones permitidas.

- **Análisis de valores límite:** Se prueban valores en los extremos de los rangos permitidos, como límites de caracteres en campos de texto, fechas próximas al evento, cupos mínimos y máximos de entradas, y montos de dinero en los límites de precisión de BigDecimal.

- **Validación de entradas:** Se inyectan datos malformados, incompletos o inválidos (cadenas vacías, correos sin formato, códigos promocionales expirados, pagos rechazados) para verificar que el sistema rechace correctamente las transacciones con mensajes de error claros y sin efectos secundarios no deseados.

- **Pruebas de casos de uso:** Se recorren paso a paso los flujos principales del sistema: creación de un evento, configuración de categorías de entradas, proceso de compra, generación de entradas (incluyendo Apple Wallet), check-in y reporting.

- **Pruebas de concurrencia:** `TicketReservationManagerConcurrentTest` y `WaitingQueueProcessorMultiThreadedIntegrationTest` validan que el sistema maneje correctamente reservas simultáneas sin condiciones de carrera, usando bloqueos optimistas y control de versiones en la base de datos.

- **Tablas de decisión:** Se aplican para validar reglas complejas de negocio, como el cálculo de precios con descuentos, impuestos (IVA), tarifas de servicio y promociones combinadas, donde múltiples condiciones booleanas determinan el resultado final.

## 8. Integración continua

Cada Pull Request activa automáticamente el flujo de pruebas en GitHub Actions:

```yaml
# Extraído de .github/workflows/test.yml
jobs:
  test:
    strategy:
      matrix:
        postgresql: ["10", "15", "16"]
    steps:
      - run: ./gradlew test jacocoTestReport -Dpgsql.version=${{ matrix.postgresql }}
```

Este proceso garantiza que:
- El sistema es compatible con las tres versiones principales de PostgreSQL.
- La cobertura de código no se degrada (verificable mediante Codecov).
- Los informes de cobertura se generan y publican automáticamente.

## 9. Conclusión

El enfoque de pruebas de alf.io combina múltiples niveles con un fuerte énfasis en la automatización para garantizar entornos de prueba reproducibles y fiables. La cobertura es extensa en la capa de negocio y los flujos críticos de pago y reserva, con 141 archivos de prueba que abarcan desde validaciones unitarias hasta escenarios concurrentes complejos. El pipeline de CI verifica cada cambio contra tres versiones de PostgreSQL, asegurando compatibilidad y calidad continua.