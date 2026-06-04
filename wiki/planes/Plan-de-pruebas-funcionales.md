Se desarrolló el plan de pruebas contemplando el estándar ISTQB [^1].

En el presente documento se detallan los siguientes aspectos generales y su
aplicación en el proyecto

- [[Alcance]]: Qué se va a probar y qué no se va a probar. Define las funcionalidades, módulos o requisitos incluidos y excluidos del plan.
- [[Analisis-de-riesgo-de-calidad]]: Identifica funcionalidades con mayor probabilidad de fallar o con mayor impacto para el negocio. Sirve para priorizar qué probar primero y con mayor profundidad.
- [[Criterios de entrada y salida]]: Condiciones para iniciar y finalizar las pruebas.
- [[Niveles de prueba]]: Qué niveles se ejecutarán: pruebas unitarias, de
  integración, de sistema, aceptación, E2E, etc..
- [[Técnicas de Prueba]]: Cómo se diseñarán los casos de prueba. Por ejemplo: partición de equivalencia, análisis de valores límite, tablas de decisión, transición de estados, pruebas exploratorias, etc.
- [[Entorno y Datos]]: Hardware, software, bases de datos, navegadores, servidores y datos de prueba necesarios para ejecutar las pruebas.
- [[Métodos y Herramientas]]: Procesos y herramientas utilizadas. Por ejemplo: JUnit para unitarias, Selenium para automatización, Postman para APIs, GitHub Actions para CI/CD, etc.

Y la planificación y realización de las pruebas de detalla en [[Flujo de trabajo en GitHub]].


[^1]: International Software Testing Qualifications Board (ISTQB), _Certified Tester Foundation Level Extension Syllabus – Agile Tester_, Version 2014.
