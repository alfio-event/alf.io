# Plan de Pruebas Unitarias

## m1
- [[1.1 Alcance]]
- [[1.2 Referencias]]
- [[1.3 Glosario]]
- [[2.1 Proyecto o Subprocesos de Prueba]]

## m2

### 2.2 Elementos de Prueba

Se realizarán pruebas unitarias a los siguientes elementos:

* **Backend (Java/Spring Boot):**
  * Controladores REST API (endpoints v1 y v2)
  * Lógica de negocio (managers de reservación, pagos, eventos, notificaciones)
  * Servicios de datos (repositorios JPA)
  * Entidades de dominio y objetos de transferencia de datos (DTOs)
  * Utilidades y helpers (calculadora de precios, validadores, formateadores)
  * Trabajos programados (jobs de limpieza, envío de emails)

* **Frontend (Angular):**
  * Componentes de interfaz de usuario (reservación, pago, administración)
  * Servicios de comunicación con API
  * Validaciones del lado cliente
  * Guardianes de rutas (guards)

### 2.3 Alcance de la Prueba

Incluido en el alcance:
* Pruebas unitarias de lógica de negocio en managers y servicios
* Pruebas de entidades de dominio y DTOs
* Pruebas de controladores REST con mocks de dependencias
* Pruebas de utilitarios, helpers y calculadoras
* Pruebas de componentes Angular con mocks de servicios
* Pruebas de servicios compartidos del frontend
* Pruebas de guardianes de rutas (guards)

Excluido del alcance:
* Pruebas de integración con base de datos
* Pruebas end-to-end
* Pruebas de rendimiento
* Pruebas de seguridad avanzadas (penetration testing)
* Pruebas de servicios externos reales (Stripe, PayPal)
* Pruebas de aceptación del usuario (UAT)

### 2.4 Suposiciones y Restricciones

Suposiciones:
* El entorno de desarrollo local tiene Java 17, Node.js y PostgreSQL configurado correctamente
* Se dispone de acceso al repositorio de GitHub del proyecto alf.io
* El equipo tiene conocimientos básicos de JUnit 5, Mockito y Jasmine/Karma para Angular
* Las dependencias del proyecto están documentadas en build.gradle y package.json

Restricciones:
* Las pruebas unitarias deben ejecutarse en menos de 15 minutos por ciclo
* Los mocks de servicios externos se utilizarán para evitar llamadas reales a APIs de pago
* La cobertura mínima objetivo es del 85% para código nuevo

### 2.5 Partes Interesadas

| Rol | Responsabilidades |
| :--- | :--- |
| Docente a cargo | Aprobación de criterios de aceptación académicos, validación y aprobación del plan de pruebas, definición de escenarios de uso real, supervisión general del proyecto. |
| Test Lead | Liderazgo y coordinación del equipo de pruebas, planificación de pruebas unitarias en sprints, supervisión de actividades de testing, comunicación con stakeholders, gestión de riesgos y escalaciones, aprobación de entregables de prueba, revisión de código. |
| Desarrolladores | Implementación de funcionalidades necesarias, planificación eimplementación de casos de prueba, documentación del desarrollo de sus tareas, solicitud de revisiones al equipo de revisión. |

### 3. Comunicación de las Pruebas

En esta sección se explican las pautas para comunicar de manera efectiva durante el proceso de pruebas unitarias. Se define cómo debe ser la comunicación dentro del equipo y con las personas o grupos externos que estén involucrados. Además, se especifica quién es responsable de comunicar qué, por qué medios se debe hacer, con qué frecuencia y qué hacer cuando surjan conflictos o desacuerdos.

**Objetivo**
Asegurar que todos los miembros del equipo estén informados sobre el avance de las pruebas, defectos encontrados, prioridades de resolución, y mejoras del proceso.

**Protocolo de Comunicación**
* Comunicación Interna: Se usará WhatsApp para conversaciones rápidas, reuniones sincrónicas por Google Meet, y GitHub Projects para registro de tareas y seguimiento.
* Comunicación Externa: Se mantendrá en constante actualización la plataforma de GitHub para las revisiones periódicas por parte del docente.
* Resolución de Conflictos: Se gestionará en primera instancia por el Tech Lead. Si no se resuelve, se eleva al docente.
* Metodología: Se utiliza Scrum como marco de trabajo, con sprints de duración definida por el equipo.

| Punto de Comunicación | Propósito | Frecuencia | Medios | Responsable | Audiencia |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Sprint Planning | Planificar pruebas del sprint, definir prioridades | Inicio de cada sprint | Meet + GitHub Projects | Tech Lead | Equipo de desarrollo |
| Daily Standup | sincronización diaria de avances y bloqueos | Diario | WhatsApp | Desarrollador | Equipo de desarrollo |
| Sprint Review | Demostrar pruebas completadas y resultados | Fin de cada sprint | Meet | Tech Lead | Equipo + Docente |
| Sprint Retrospective | Evaluar qué mejorar en el proceso | Fin de cada sprint | Meet | Tech Lead | Equipo de desarrollo |
| Reporte de defectos | Reporte de bugs encontrados en pruebas | Cuando se encuentre | GitHub Issues | Desarrollador | Tech Lead |
| Reunión con docente | Validar avances y recibir feedback | 2 veces en la semana | Meet + Documento de avances | Tech Lead | Docente |

Participantes del equipo:

1. Robert Edison Arisaca Mamani (Docente del curso)
2. Mestas Zegarra, Christian Raúl (Tech Lead)
3. Sequeiros Condori, Luis Gustavo (Desarrollador)
4. Jara Mamani, Mariel Alisson (Desarrollador)
5. Fernández Huarca, Rodrigo Alexander (Desarrollador)
6. Quispe Condori, Álvaro Raúl (Desarrollador)
7. Barrios Medina, Mathías Alonso (Desarrollador)

> [!IMPORTANT]
> Todos los acuerdos relevantes se documentan en la wiki del proyecto en GitHub. Se fomenta la comunicación asertiva y la colaboración proactiva. Los defectos se reportan como issues en GitHub con etiquetas de prioridad, y el estado de las pruebas se actualiza en GitHub Projects (columnas: Backlog, Ready, In Progress, In Review, Done). Se utiliza Scrum como metodología de trabajo, con sprints que incluyen planificación, revisión y retrospectiva.

## m3
- [[4 Registro de Riesgos]]
- [[5.1 Entregables de Prueba]]
- [[5.2 Técnicas de diseño de Prueba]]
- [[5.3 Criterio de Finalización y Prueba]]

## m4
- [[5.4 Métricas]]
- [[5.5 Requisitos del entorno de Pruebas]]
- [[6.1 Definición de la Estructura General de Pruebas]]
- [[7.1 Roles, Actividades y Responsabilidades]]
- [[8 Cronograma]]
