# 8. Cronograma

El cronograma con las actividades detalladas para el ciclo de pruebas del proyecto se gestiona y centraliza a través de la sección de **Roadmap** de GitHub del equipo **Catarinas**. Esta vista permite realizar un seguimiento dinámico del progreso, plazos y asignaciones durante las dos semanas planificadas, tal como se presenta en la **Figura 1** y la **Figura 2**.

<img width="1579" height="450" alt="Fig 1. Cronograma de Actividades Parte-1" src="https://github.com/user-attachments/assets/0b237869-31f1-4ea4-ac04-a30e781c50e1" />

**Fig 1. Cronograma de Actividades Parte-1**

<img width="1744" height="400" alt="Fig 2. Cronograma de Actividades Parte-2" src="https://github.com/user-attachments/assets/4cef6495-92b1-4fa2-bf44-0ec4f4832287" />

**Fig 2. Cronograma de Actividades Parte-2**

## Plan de Trabajo

El plan de trabajo se organiza de forma secuencial en las siguientes etapas clave alineadas a los componentes de la plataforma:

* **Configuración Inicial del Repositorio (Fig. 1):** Redacción del plan de pruebas en la Wiki, flujo de trabajo (`INTERNAL_CONTRIBUTING.md`), plantillas de *Issues* / *Pull Requests* y despliegue de páginas.
* **Aseguramiento Estático y QA (Fig. 1):** Configuración de **Checkstyle/PMD** (Java) y **Biome** (Angular/Vite) para garantizar estándares de código.
* **Infraestructura de Pruebas (Fig. 1 y 2):** Diseño del plan de calidad en la Wiki y configuración técnica de la ejecución local y en entornos CI (**GitHub Actions**).
* **Ejecución de Pruebas Unitarias Backend & Frontend (Fig. 2):** Codificación de suites automatizadas distribuidas estratégicamente por capas:
  * **En Backend:** Cobertura sobre el núcleo de negocio (`Manager`, `SystemManager`, `UserManager`, `Wallet` y `Payment`), persistencia (`Repositories`), puntos de entrada (`Controllers API`) y procesos en segundo plano (`Utilities y Jobs`).
  * **En Frontend (Admin & Public):** Cobertura del panel de administración (`Services` y `Components`), seguridad pública (`Shared y Guards`), flujos core de compra (`Reservation y Payment`), salas de espera (`Waiting Room`) y componentes restantes para alcanzar el $\ge 85\%$ de cobertura global.
* **Cierre y Documentación (Fig. 2):** Consolidación de entregables mediante la redacción del Informe de Pruebas Unitarias y publicación de métricas de calidad en la Wiki.