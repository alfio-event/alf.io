  
**“AÑO DE LA RECUPERACIÓN Y CONSOLIDACIÓN DE LA ECONOMÍA PERUANA”**  
**FACULTAD DE INGENIERÍA DE PRODUCCIÓN Y SERVICIOS**  
**ESCUELA PROFESIONAL DE INGENIERÍA DE SISTEMAS**

**Plan de pruebas**

# **Índice** {#índice}

[**Índice	2**](#índice)

[**1\. Introducción	4**](#introducción)

[1.1. Alcance	4](#alcance)

[1.2. Referencias	4](#referencias)

[1.3. Glosario	4](#glosario)

[**2\. Contexto de las Pruebas	4**](#contexto-de-las-pruebas)

[2.1. Proyecto / Subprocesos de Prueba	4](#proyecto-/-subprocesos-de-prueba)

[2.2. Elementos de Prueba	5](#elementos-de-prueba)

[2.3. Alcance de la Prueba	6](#alcance-de-la-prueba)

[2.4. Suposiciones y Restricciones	6](#suposiciones-y-restricciones)

[2.5. Partes Interesadas	7](#partes-interesadas)

[**3\. Comunicación de las Pruebas	7**](#comunicación-de-las-pruebas)

[**4\. Registro de Riesgos	9**](#registro-de-riesgos)

[**5\. Estrategia de Prueba	11**](#estrategia-de-prueba)

[5.1. Subprocesos de prueba	11](#subprocesos-de-prueba)

[5.2. Entregables de Prueba	12](#entregables-de-prueba)

[5.3. Técnicas de diseño de Prueba	12](#técnicas-de-diseño-de-prueba)

[5.4. Criterio de Finalización y Prueba	13](#criterio-de-finalización-y-prueba)

[5.5. Métricas	13](#métricas)

[5.6. Requisitos del entorno de Pruebas	14](#requisitos-del-entorno-de-pruebas)

[5.6.1. Ambiente de pruebas	14](#ambiente-de-pruebas)

[5.6.2. Herramientas de Pruebas	14](#herramientas-de-pruebas)

[5.7. Re-testing y regresión de las Pruebas	14](#re-testing-y-regresión-de-las-pruebas)

[5.8. Criterios de Suspensión y Reanudación	15](#criterios-de-suspensión-y-reanudación)

[5.8.1. Criterios de suspensión	15](#criterios-de-suspensión)

[5.8.2. Criterio de reanudación	15](#criterio-de-reanudación)

[**6\. Actividades y Estimados de Prueba	16**](#actividades-y-estimados-de-prueba)

[6.1. Definición de la Estructura General de Pruebas	16](#definición-de-la-estructura-general-de-pruebas)

[**7\. Personal	17**](#personal)

[7.1. Roles, Actividades y Responsabilidades	17](#roles,-actividades-y-responsabilidades)

[7.2. Necesidades de Contratación	18](#necesidades-de-contratación)

[7.3. Necesidades de Entrenamiento	18](#necesidades-de-entrenamiento)

[**8\. Cronograma	18**](#cronograma)

**Control de versiones**

| Versión | Autor(es) | Descripción | Fecha |
| :---: | ----- | ----- | :---: |
| 1.0 | Equipo Cigarra | Versión inicial del documento | 30/05/25 |

1. # **Introducción** {#introducción}

   1. ## **Alcance** {#alcance}

      Este plan proporciona el marco necesario para planificar, gestionar y ejecutar las pruebas del sistema TEAMMATES, cubriendo frontend, backend e integración.

   2. ## **Referencias** {#referencias}

* ISO/IEC/IEEE 29119  
* Documento de Requisitos de TEAMMATES  
* Documento de Arquitectura de TEAMMATES  
* Documento de Despliegue de TEAMMATES  
* Documentación técnica de Angular y Spring


  3. ## **Glosario** {#glosario}

     En este documento se utilizan los siguientes términos abreviados:  
* **UAT:** User Acceptance Test (Pruebas de Aceptación del Usuario).  
* **API:** Application Programming Interface (Interfaz de Programación de Aplicaciones).  
* **GAE:** Google App Engine (Motor de Aplicaciones de Google).  
* **GCD:** Google Cloud Datastore  
* **REST:** Representational State Transfer (Transferencia de Estado Representacional).  
* **CRUD:** Create, Read, Update, Delete (Crear, Leer, Actualizar, Eliminar).  
* **DTO:** Data Transfer Object (Objeto de Transferencia de Datos).  
* **AJAX:** Asynchronous JavaScript and XML (JavaScript y XML Asíncronos).  
* **JPA:** Java Persistence API (API de Persistencia de Java).   
* **CSRF:** Cross-Site Request Forgery (Falsificación de solicitudes entre Sitios).


2. # **Contexto de las Pruebas** {#contexto-de-las-pruebas}

   1. ## **Proyecto / Subprocesos de Prueba** {#proyecto-/-subprocesos-de-prueba}

      El sistema TEAMMATES consta de los siguientes módulos principales:  
* Módulo de Gestión de Cursos  
  * Creación y administración de cursos  
  * Gestión de estudiantes e instructores  
  * Configuración de equipos de trabajo  
* Módulo de Sesiones de Feedback  
  * Creación de sesiones de evaluación  
  * Configuración de preguntas y tipos de respuesta  
  * Gestión de fechas y visibilidad  
* Módulo de Evaluaciones  
  * Envío y recepción de evaluaciones entre pares  
  * Procesamiento de respuestas  
  * Cálculo de estadísticas  
* Módulo de Reportes y Resultados  
  * Generación de reportes para instructores  
  * Visualización de resultados para estudiantes  
* Módulo de Notificaciones  
  * Envío de emails automatizados  
  * Recordatorios de sesiones

    

  2. ## **Elementos de Prueba** {#elementos-de-prueba}

     Se realizarán pruebas a los siguientes elementos:  
* Frontend (Angular):  
  * Componentes de interfaz de usuario  
  * Servicios de comunicación con API  
  * Validaciones del lado cliente  
* Backend (Java):  
  * Controladores REST API (WebApiServlet)  
  * Lógica de negocio (Logic component)  
  * Servicios de datos (Storage component)  
  * Servicios externos (Email, Task Queue)  
  * Autenticación y autorización  
* Integración:  
  * Comunicación Frontend-Backend vía REST  
* Infraestructura:  
  * Configuración de despliegue  
  * Manejo de sesiones  
  * Seguridad (CSRF protection)

    

  3. ## **Alcance de la Prueba** {#alcance-de-la-prueba}

     Incluido en el alcance:  
* Pruebas funcionales de todos los módulos mencionados en 2.1  
* Pruebas de integración entre componentes  
* Pruebas de API REST  
* Pruebas de interfaz de usuario  
* Pruebas de compatibilidad de navegadores

  Excluido del alcance:

* Pruebas de rendimiento bajo carga extrema (se externalizarán)  
* Pruebas de seguridad avanzadas (penetration testing)  
* Pruebas de infraestructura de Google Cloud Platform  
* Pruebas de migración de datos  
* Pruebas con servicios externos (Email, Task Queue)  
* Pruebas de integración con Google (GAE y GCD)  
* Pruebas de regresión  
* Pruebas de aceptación del usuario


  4. ## **Suposiciones y Restricciones** {#suposiciones-y-restricciones}

     Suposiciones:  
* El ambiente de pruebas será una réplica del ambiente de desarrollo.  
* Los datos de prueba representarán escenarios académicos reales.  
* Se dispondrá de cuentas de prueba para autenticación y autorización, cubriendo roles de estudiante, instructor, y administrador.

  Restricciones:

* Las pruebas de integración con servicios externos se limitarán al entorno de desarrollo.  
* Las pruebas de carga estarán limitadas a un máximo de 100 usuarios concurrentes debido a restricciones presupuestarias y de infraestructura.  
* La corrección de defectos dependerá de la disponibilidad del equipo de desarrollo, lo que puede retrasar los plazos de futuros Sprints.  
* La configuración del entorno local estará limitada por la complejidad de replicar Google App Engine y PostgreSQL, requiriendo configuraciones manuales adicionales.


  5. ## **Partes Interesadas** {#partes-interesadas}

| Rol | Responsabilidades |
| :---- | :---- |
| Instructor/Docente a cargo | Aprobación de criterios de aceptación académicos, realización de UAT, validación y aprobación del plan de prueba, definición de escenarios de uso real. |
| Test Lead | Liderazgo y coordinación del equipo de pruebas, planificación y supervisión de actividades de testing, comunicación con stakeholders, gestión de riesgos y escalaciones, aprobación de entregables de prueba. |
| Test Analyst | Análisis de requisitos para pruebas, diseño y ejecución de casos de prueba, reporte y seguimiento de defectos, pruebas manuales y exploratorias, validación de criterios de aceptación. |
| Test Architect | Definición de la arquitectura de pruebas, diseño de estrategias de testing, selección de herramientas y frameworks, establecimiento de estándares y mejores prácticas, supervisión técnica del proceso. |
| Test Design | Diseño detallado de casos de prueba, creación de datos de prueba, desarrollo de scripts de automatización, diseño de ambientes de testing, documentación técnica de pruebas. |

     

3. # **Comunicación de las Pruebas** {#comunicación-de-las-pruebas}

   En esta sección se explican las pautas para comunicar de manera efectiva durante el proceso de pruebas.  
   Se define cómo debe ser la comunicación dentro del equipo y con las personas o grupos externos que estén involucrados. Además, se especifica quién es responsable de comunicar qué, por qué medios se debe hacer, con qué frecuencia y qué hacer cuando surjan conflictos o desacuerdos.  
     
   **Objetivo**  
   Asegurar que todos los miembros del equipo estén informados sobre el avance de las pruebas, defectos encontrados, prioridades de resolución, y mejoras del proceso.  
     
   **Protocolo de Comunicación**  
* Comunicación Interna: Se usará la red social (WhatsApp) para conversaciones rápidas, reuniones sincrónicas por Google Meet, y documentos colaborativos (Google Docs / Hoja de cálculo / Plataforma Clik Cup) para registro de acuerdos.  
* Comunicación Externa: Se notificará al docente mediante los servicios de correo institucional o plataforma virtual de aprendizaje.  
* Resolución de Conflictos: Se gestionará en primera instancia por el Test Lead. Si no se resuelve, se eleva al Docente.


| Punto de Comunicación | Propósito | Frecuencia | Medios | Responsable | Audiencia |
| :---- | :---- | :---- | :---- | :---- | :---- |
| Reunión de inicio | Establecer objetivos y plan | Una vez (inicio del proyecto) | Presencial / Google Meet | Test Lead | Todo el equipo |
| Reuniones de Revisión | Verificar avances y defectos | En la semana 3 veces | Google Meet \+ Drive | Test Lead | Todo el equipo |
| Reportes de Estado | Estado general de pruebas | Diario (stand-up) | Drive, Clickup | Test Analyst Test Architect Test Design | Test Lead |
| Reporte de Defectos | Reporte de bugs críticos | Diario | WhatsApp, Clik Cup | Test Analyst Test Architect Test Design | Test Lead  |
| Reunión de Retrospectiva | Evaluar qué mejorar | Al final de cada sprint  | Presencial / Meet  | Test Lead / Instructor | Todo el equipo |
| Reunión con Docente | Validar avances y recibir feedback | 2 veces en la semana | Presencial / Meet  \+ Documento de avances | Instructor | Instructor |


  Participantes del equipo:

1. Robert Edison Arisaca Mamani (Docente del curso)  
2. Chino Pari, Joel Antonio (Test Lead)  
3. Marrón Lope, Misael Josías (Test Analyst)  
4. Chara Condori, Jean Carlo (Test Analyst)  
5. Escobedo Ocaña, Jorge Luis (Test Analyst)  
6. Puma Larico, Cristhian Willians (Test Architect)  
7. Véliz Saihua, Rodrigo Alejandro (Test Architect)  
8. Chancuaña Alvis, Klismann (Test Design)  
9. Ezcurra Paima, Maria Solange (Test Design)

   **Comentarios:**

   Todos los acuerdos relevantes se documentan en la carpeta compartida del equipo “Cigarra” en el Drive. Se fomenta la comunicación asertiva y la colaboración proactiva.

   

4. # **Registro de Riesgos** {#registro-de-riesgos}

   En la siguiente tabla se identifican los riesgos del proyecto, así como se determina la severidad de cada uno de los riesgos multiplicando el impacto por la probabilidad de ocurrencia.  
   El impacto y la probabilidad se determinan teniendo en cuenta una escala de 1 al 5, donde 5 es el más alto.  
   

| N° | Riesgos | Probabilidad (1-5) | Impacto (1-5) | Severidad (Prob\*Impct) | Plan de Mitigación |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | Dependencias externas (Google Services) no disponibles | 2 | 5 | 10 | Tener estrategia de pruebas offline. |
| 2 | Incompatibilidad entre versiones de Angular, Java y dependencias (Versiones de software) | 2 | 4 | 8 | Usar contenedores con versiones específicas. Mantener documentación de versiones exactas. |
| 3 | Falta de cuentas de Google válidas para pruebas de autenticación | 3 | 4 | 12 | Solicitar cuentas de prueba institucionales. |
| 4 | Diferencias en el comportamiento entre ambiente local y producción | 4 | 4 | 16 | Documentar limitaciones del ambiente local. |
| 5 | Fallos en la comunicación Frontend-Backend por configuración local incorrecta | 2 | 4 | 8 | Realizar pruebas de integración continuas. |
| 6 | Falta de datos de prueba representativos o insuficientes para cubrir escenarios complejos | 3 | 3 | 9 | Desarrollar una estrategia robusta de generación de datos de prueba. Realizar revisiones de datos de prueba. |
| 7 | Errores en la configuración de las herramientas de automatización de pruebas (Selenium, Jest, TestNG) | 3 | 4 | 12 | Adquirir conocimientos adicionales sobre estas herramientas y la manera adecuada de utilizarlas. Hacer revisiones periódicas de los scripts de prueba para asegurar su calidez. |
| 8 | Retrasos en la corrección de defectos debido a la disponibilidad del equipo  | 3 | 4 | 12 | Priorizar los defectos encontrados en base a su impacto y severidad. Asignar tiempo específico del equipo para la resolución de defectos de pruebas. |
| 9 | Retraso de algún integrante en sus responsabilidades o tareas | 2 | 4 | 8 | Realizar revisiones de progreso periódicas del equipo acorde al avance de ser necesario. Ofrecer apoyo y ayuda a los miembros que presenten dificultades antes de que el problema se agrave. |

   

   

5. # **Estrategia de Prueba** {#estrategia-de-prueba}

   

   1. ## **Subprocesos de prueba**  {#subprocesos-de-prueba}

* **Frontend:** Se evaluará la funcionalidad de los componentes visuales desarrollados en Angular, incluyendo formularios, validaciones del cliente y navegación entre pantallas.  
* **Backend:** Se verificarán los controladores REST, la lógica de negocio y los servicios internos implementados en Java, asegurando que cumplan los requisitos funcionales.  
* **Integración:** Se probará la comunicación entre frontend y backend mediante API REST, validando que los datos se transmitan correctamente en ambos sentidos.  
* **Reportes y notificaciones:** Se revisará que los reportes generen información precisa y que los correos electrónicos se envíen de forma automática y oportuna.  
* **Entorno y configuración:** Se comprobará que el sistema funcione correctamente en el entorno de pruebas configurado, incluyendo el manejo de sesiones y la seguridad básica.


  

  2. ## **Entregables de Prueba** {#entregables-de-prueba}

  Los principales entregables de prueba son:

* Plan de pruebas: Define el enfoque, objetivos y alcance de cada subproceso.  
* Casos de prueba: Documentan los pasos, datos de entrada y resultados esperados.  
* Matriz de trazabilidad: Relaciona los casos de prueba con los requisitos del sistema.  
* Registro de defectos: Detalla los errores encontrados, su estado y severidad.  
* Reportes de estado: Informan periódicamente el avance de la ejecución de pruebas.  
* Informe final: Resume los resultados obtenidos y emite conclusiones generales.

  3. ## **Técnicas de diseño de Prueba** {#técnicas-de-diseño-de-prueba}

* Partición de equivalencia: Permite cubrir diferentes clases de entrada con pocos casos.  
* Análisis de valores límite: Evalúa el comportamiento del sistema en los extremos de los rangos.  
* Casos de uso: Se construyen escenarios de prueba según los flujos del usuario.  
* Pruebas exploratorias: Se realizan de forma libre para detectar errores no previstos.  
* Transición de estados: Verifican los cambios entre distintos estados del sistema.


  4. ## **Criterio de Finalización y Prueba** {#criterio-de-finalización-y-prueba}

     Se consideran finalizadas las pruebas cuando se cumplen las siguientes condiciones:  
* Se ejecutaron el 100% de los casos de prueba definidos para los requisitos de prioridad alta, al menos un 85% para los requisitos de prioridad media y al menos un 75% para los requisitos de prioridad baja.  
* Los defectos críticos y de alta prioridad fueron corregidos y verificados  
* No existen defectos abiertos con prioridad alta  
* Se ha generado y revisado el informe final de pruebas  
* El Test Lead ha aprobado el informe final de pruebas

  5. ## **Métricas** {#métricas}

     Las siguientes métricas se recogerán durante el transcurso de ejecución de pruebas:  
* **Número de casos de prueba ejecutados**  
* **Número de casos de prueba por requisito**  
* **Número de casos de prueba resueltos**  
* **Número de casos de prueba resueltos por requisito**  
* **Tasa de éxito de pruebas:** Calculado de la siguiente forma: (Casos de prueba exitosos / Total de casos de prueba ejecutados) \*100%  
* **Tasa de detección de defectos:** Calculado de la siguiente forma: (Defectos detectados en las pruebas / Total de casos de prueba ejecutados)  
* **Tiempo promedio de resolución de defectos:** Tiempo promedio acontecido entre la deteccion y resolucion de un defecto  
* **Cobertura de requisitos:**  Calculado de la siguiente forma: (Cantidad de requisitos probados / Requisitos totales)  
* **Cantidad de líneas de código:** Cantidad de líneas de código efectuadas para los casos de prueba  
* **Esfuerzo por caso de prueba:** Calculado de la siguiente forma: 	(Total de horas dedicadas / Total de casos de prueba ejecutados)


  6. ## **Requisitos del entorno de Pruebas** {#requisitos-del-entorno-de-pruebas}

     

     1. ### **Ambiente de pruebas** {#ambiente-de-pruebas}

        

| Navegadores | Google Chrome Brave Opera GX |
| :---: | :---- |
| Sistemas Operativos | Windows 11 Home Windows 10 Pro |

        

     2. ### **Herramientas de Pruebas** {#herramientas-de-pruebas}

        

| Herramienta | Función |
| :---: | ----- |
| TestNG | Framework de pruebas unitarias y de integración en java para la parte backend |
| Jest | Framework de pruebas para realizar snapshot testing en la parte de frontend |
| Selenium | Automatización de pruebas funcionales en entornos web |

        

  7. ## **Re-testing y regresión de las Pruebas** {#re-testing-y-regresión-de-las-pruebas}

     Se deben realizar pruebas de confirmación (re-testing) para validar que los defectos corregidos han sido efectivamente solucionados. Estas pruebas se enfocarán en los casos que inicialmente fallaron y se ejecutarán bajo las mismas condiciones del reporte original.  
       
       
       
     **Estrategia de Regresión:**  
1. Se ejecutará una suite automatizada de regresión tras cada nueva integración o cambio relevante en el backend y frontend.  
2. Se realizarán pruebas de regresión manuales focalizadas en funcionalidades directamente impactadas por cambios recientes.  
3. Una regresión completa se ejecutará al finalizar el tercer ciclo de pruebas, previo a la entrega final del Sprint.  
4. Se contempla un mínimo de 3 ciclos de pruebas con incremento gradual de cobertura de regresión.

   

   8. ## **Criterios de Suspensión y Reanudación** {#criterios-de-suspensión-y-reanudación}

      1. ### **Criterios de suspensión** {#criterios-de-suspensión}

         Las pruebas serán suspendidas temporalmente si se presenta alguna de las siguientes condiciones:  
- Alguna funcionalidad clave descrita en los requisitos del sistema TEAMMATES no puede ejecutarse debido a errores bloqueantes.  
- Fallas en la comunicación entre los componentes principales del sistema (por ejemplo, problemas persistentes entre el frontend Angular y el backend Java).  
- El entorno de pruebas presenta inestabilidad o errores que impiden la obtención de resultados confiables.  
- No se dispone de datos de prueba adecuados para continuar con la ejecución.

  Durante la suspensión, el equipo documentará la situación en ClickUp y notificará al Test Lead para la coordinación con el equipo de desarrollo.


  2. ### **Criterio de reanudación** {#criterio-de-reanudación}

     Las pruebas se reanudarán cuando:  
- Los defectos críticos hayan sido corregidos y validados mediante re-testing.  
- El entorno de pruebas haya sido restaurado y validado por el Test Architect.  
- Se disponga de datos de prueba válidos que permitan la continuidad del proceso.  
- El Test Lead y el Instructor aprueben la reanudación formal mediante reunión de revisión.

  La reanudación será planificada para minimizar el impacto en el cronograma del Sprint.


6. # **Actividades y Estimados de Prueba** {#actividades-y-estimados-de-prueba}

   Este capítulo describe las actividades principales de prueba para el sistema TEAMMATES, junto con sus estimaciones de tiempo, diseñadas para validar las funcionalidades del frontend, backend, integración, y persistencia. A continuación, se presentan las actividades principales.  
   

   1. ## **Definición de la Estructura General de Pruebas** {#definición-de-la-estructura-general-de-pruebas}

      Establecer objetivos, alcance, y tipos de pruebas (unitarias, integración, funcionales, no funcionales, UAT simulada) basados en los requisitos de TEAMMATES. Priorizar casos de uso y módulos clave.  
        
   2. **Especificación Detallada de Casos de Prueba**  
      Diseñar casos de prueba detallados para APIs REST, persistencia, y autorización. Crear datos de prueba basados en escenarios académicos.  
        
   3. **Establecimiento del Entorno de Pruebas**  
      Configurar el entorno local con Java/Spring, Angular, emulación de Google App Engine, Datastore, y PostgreSQL. Instalar herramientas como JUnit y Postman.  
        
   4. **Primer Ciclo de Ejecución de Pruebas**  
      Ejecutar pruebas unitarias, integración y funcionales manuales. Reportar defectos al equipo.  
        
   5. **Segundo Ciclo de Ejecución de Pruebas**  
      Volver a ejecutar casos fallidos tras corrección de defectos. Ampliar pruebas exploratorias y validar migración.  
        
   6. **Tercer Ciclo de Ejecución de Pruebas**  
      Ejecutar pruebas de regresión, simulación de UAT (escenarios del Docente), y pruebas no funcionales.

   7. **Informe de Reporte de Estados y Finalización**  
      Generar reportes de resultados (defectos, cobertura, estado de pruebas) y un informe de finalización. Presentar hallazgos al docente, incluyendo recomendaciones.  
      

   

7. # **Personal** {#personal}

   Esta sección describe los recursos humanos necesarios para llevar a cabo el esfuerzo de pruebas. Se especifican los roles, responsabilidades, necesidades de contratación y los entrenamientos requeridos.

   1. ## **Roles, Actividades y Responsabilidades** {#roles,-actividades-y-responsabilidades}

   Para asegurar un proceso de pruebas bien organizado, se definen claramente los roles y responsabilidades del personal involucrado. Se utiliza una matriz RACI para indicar quién es Responsable (R), Aprobador/Responsable final (A), Consultado (C) e Informado (I) en cada actividad clave del proceso de pruebas donde:

- **R – Responsable:** Las personas que ejecutan la tarea o actividad, es decir, son quienes hacen el trabajo.  
- **A – Aprobador / Responsable final (Accountable):** La persona que tiene la autoridad final sobre la actividad y se asegura de que se complete correctamente. Solo puede haber un “A” por actividad.  
- **C – Consultado:** Personas que deben ser consultadas antes o durante la ejecución de la actividad. Son expertos o partes clave que brindan asesoría o información.  
- **I – Informado:** Personas que deben ser notificadas del avance o resultados de la actividad. No participan directamente, pero necesitan estar al tanto.


| Rol/Actividad | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| Instructor | I | I | I | I | I | I | I |
| Test Lead | A | A | I | A | A | A | R |
| Test Analyst	 | C | R | C | R | R | R | C |
| Test Design	 | R | C | C | C | C | C | C |
| Test Architect | C | I | R | I | I | I | I |

  2. ## **Necesidades de Contratación** {#necesidades-de-contratación}

  En base al análisis de la carga de trabajo estimada y el cronograma definido para las actividades de prueba del sistema TEAMMATES, se ha llegado a la conclusión de que el equipo de pruebas funcionales cuenta con los integrantes necesarios para ejecutar el plan de pruebas de manera efectiva.


  3. ## **Necesidades de Entrenamiento** {#necesidades-de-entrenamiento}

  Con el fin de garantizar una ejecución adecuada de las actividades de prueba, se requiere brindar una sesión de inducción técnica al sistema TEAMMATES a los miembros del equipo encargados de realizar las pruebas. Esta capacitación inicial tiene como finalidad proporcionar un entendimiento general del sistema, sus funcionalidades clave, flujos operativos y criterios de aceptación.

  La capacitación será de carácter obligatorio para los perfiles de Test Analyst, Test Design y cualquier otro integrante que participe directamente en la ejecución de pruebas funcionales y no funcionales.

  Se estima que la sesión tendrá una duración aproximada de una (1) hora, y puede ser impartida por un miembro del equipo técnico del proyecto.


8. # **Cronograma** {#cronograma}

   El cronograma con las actividades detalladas de las pruebas se encuentra documentado en la herramienta hoja de cálculo, donde se puede visualizar cada fase del proceso de validación del sistema TEAMMATES, incluyendo el análisis, diseño, configuración del entorno, ejecución en tres ciclos, y finalización. Este cronograma permite hacer seguimiento al progreso, asignación de tareas y cumplimiento de plazos, asegurando así una ejecución ordenada y controlada del plan de pruebas. Se muestra el cronograma en la Figura 1\.  
   ![][image2]

*Figura 1\. Cronograma de actividades de Prueba.*  
![][image3]  
*Figura 2\. Cronograma de actividades de Prueba a más detalle.*

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,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>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAj0AAAFqCAYAAADx8uvTAABEl0lEQVR4Xu29f3CV15mg2X9Nbc3U1k5N7f/6jypVQy1VTaEqx2yngireiAprzTTt9nRIqCFxqCihHI2NejzDbZxgioxm1pewEdNMKzYbM1YMQwyJxo6dxR0m2hYoYA9OcNmEdpAAESDEKCEGW/G75z0/vu98594LuuIKrnSfp+pt3e985+d3VX0ev+cT+SMxfPTRR/Lh9B/k5gcfEgRBEARBLMj4IxWetJAgCIIgCGKhxR+R4SEIgiAIohXij9ICgiAIgiCIhRhID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRFID0EQBEEQLRGzlp6Joz+V8lM7Zce2nbJz+0757z/4ofzq0LaKehqfaO+QNo2lnfKpx78ro7+qrFNfXJRXyt+qUl4t0rpX5ZXHu6rUq4x03un9mcW4tD1+JPqZ3i/GJ9o/I7vOVJZXizO7PyNtD36nojyNqYOb7Do+sXu84l4aU69/T7Y/97pcrnLvubL57n4dlZ35jp1vWi+NVx7vqCgjCIIgiLsds5aeX539rfw/u56XXf/hP8vu/+vbMvraCbn44taKehoqD184cFHOmA31sU+vkLbFX6qoU1e89yP5wtIVleXVIql7+eXN8rEZyIdGOu/nzlXWsfHmd28hKvdeep77XIc8+C/X37Lup+77hrxifp4a+HNp+9NvyWiVOp9Y2iVP/jQqQ3oIgiCIeRSzlp43jr4tR145VogL+/+6op6GysOXX82vX/+PD8oZ/fz+RVm0uMNIUKdcft9c/3rUZVbanaRc/ul33PUnvy7fv/ChtH16szz2uU45Yzdb3UjHZdeDHfJcz4O23nNv+zFMv0uWun7dxuw33V+/nvXvMhau/S7Tp5b/OM5iVJm3SoOdd+hn6YMy9epm36fLpHz7X/25XdOiP3va91cpPZ+6b4Wt/4Xnf2GvR3dvkj+x8+3KpKeyHx9m7F263j99VJ7o/fNMZM6//A3bp9aP12Dn3TMsUx9cl/0bc/n4/l+vt8/oUwOnnDz5Naig2HUe/YZsf9PV/YKZxxeGr5v7bm5hvp/92mYvPacq53vuR/LYn5nn+umn5Ykv+HGj7/tT28yzeP8X5rv7czvug3tun4UiCIIgiDuJWUvP66Nvy4+N6MRx8YUnKupppPKgm6zKw+jXupzs/OpHdlN95d91yHm91nrnvicPmQ3y/HvX5dTOzXaz1U23fOp6JDJeet6+LlPv/cLI0bfklO/3uVNXZcr0W6j7Zx3y+q9N3QtGrjb+yIhA1P7Md+VTg8WNN523k56L8tznVpi2H8rUqUEnBq+6+WmdU69flCmzhseWd/j+KqXnlJmD7W/x1212ZZFZ15M/Me1+ezETi8p+XP+vPG5E4sGn7RqefNAJyk0jKEtWbDYScVW+39tp55av46J89qAbb2p4k30+duwVm+wz+vLX3Jza2jfbuWTSY0TmY+VT7t7yb8iP33fPX+cW5vvjb6gsqfRcrJivPtdFPd+Tqbe/J59d7qQn/r6/bMrODJr2K74RzZUgCIIg5i5mLT3v/I8J+dlPzxTi4nc3VdTTSOVh9GudZlM9Io/pf/VHWZLz+x6VTzz+HXnFSMj5579kN1vb5v1xOfPLKNOSSE/o123cr8sTfpO1Eer+8jvyqfbOpG7cflwWfe31W8677c/M+LaffN72fiQ9o899XR76tMs8uXdoKqVne896+ZOVml1yotH2ue/J+WxMn02p6Mfd12f20PMX7edwvKWyGOajYZ9RiDM+W+Zj++ta/nrexxnXd6X05M970V+P+jpubtl8s+OtqxXz1Wf9xFE3B3e8Vfy+NW4efVr+xJQ9p8IUz5kgCIIg5iBmLT1vvzEhJ8fO2Hjj79+R87+8JOPf7qmop1F4N6arw8rDTX/c8v0L143UXJfnwnssRnB2PWQ2xDe/JR9rd5me8z8ZllduIT22j/dOSdsXdDN2/dpMz3u/qKgbMj1L/t2RLNPj5ln5zk0671M2C3VKtn/S9POe+fzbi1YUbr7+LXnyqM/erHxaRn/r2laXniPy2eev2raZ9Cz+jGzXTM97eaansh83p+e+oM/vWzJ17kfy5RVOUDSD09be5bJPb38vWoOba7wmFclR84xCpue5YSd6be2PynO/KkrPt/+lEawvPJpJVDY3P9/vP95ly6x8JfN90sxtycbvyeXXB+VTi3UOxe/71L785fJX/mqF/53I50kQBEEQjY5ZS8/50+9lMfLD/yGv//dT8u7fbqiop6Ebof2v+/vWy5f1L4PCEVZ4N2Zxp4waifjx1zT7sUKWPOTeS7l8+Gl3P7zTU0N6ntSXo9ujd3J++4vs3ZHCOz2/OpKN5wTm9tITzzu7Z4TDli/VjNWH9t0UvdZjncc+qXNZYY+eqkvPh25uy7+USc/+x12GpG35Z7JMT2U/Yexh+bK5p+/OfFuPl+w7PVftezbax6Ku6CVxK455dsutyWVgvt3j3sHRd3q0/LE/dWuNpcdm28Kz+yCXnjDfJ5972mV6fn2kYr6Xf/oteXB5h/xJz/ek3Ov7iL7vj/2rQTnznOt/Udem/H0sgiAIgpijmLX0/GzXv7Xx5s7H5Y1vfF5ef+pzcuI/PF5Rb26jeLxFEARBEARRK2YtPc0RSA9BEARBEDOLeS49BEEQBEEQMwukhyAIgiCIlgikhyAIgiCIlgikhyAIgiCIlgikhyAIgiCIlgikhyAIgiCIlgikhyAIgiCIlgikhyAIgiCIloi6pOfC1Q9k/MofBAAAAGC+UZf0rPj679P2AAAAAPOCuqTnY19DegAAAGB+gvQAAABAS4D0AAAAQEswJ9Lz739wU/7iW+/L/7719/bnmxO8/AwAAAD3loZLjwqP1ktDywEAAADuFQ2VnlR00miU+Nw48pQsW7M3L5i+IsePvJtfW6bk9JGTSRkAAAC0Kg2THj3CSiWnWtzuqOv+9g5ZtPXWsrJ5dUkOT5kPZ/fKyvaSyBs7ZMnirxYrXT4oDy/uKpYBAABAy9Iw6ak81npf3r3+kfx/L9WX7Vn2l5+XZcv70+LqBOkBAAAAuA0Nkx59abkgPf/5hvxWPpKf/rBYrvVqcrxfjuuPrZ2+4Lws6XtNrk2LXDvQJ+Om5MbVK6JHV0OPdOTSc7gkbd17ZdPih2TnO76pvzf+7Fppe/Q1XwgAAACtytxJj4l6pWfkSS87o/1yeFo/nJeNh/1NIzH68cY7B2XZfZ3StrRSeobWdUjb6pLsPnI+v2dEasniLvnZVd8PAAAAtCQNk57K463q0lPzeEvFZXF+VKXZmRsV0jMiG9s/LzJlfq6olJ7AjZf7ZDw5+tq5ukM2HckuAQAAoMVomPTc6YvMLz7aIW19I9n1ovY+efF6Kj035fjTD0nb6h2yudrx1lLTR7uJ+3ry463nPm/L7u97WS7Z7BEAAAC0Ig2THqV3b2W2Jw4AAACAe0VDpUepdsylUfNYCwAAAOAu0HDpAQAAAGhGkB4AAABoCZpIekalf816WVUezUpGyuuj+56jZfvv9Ti0TTm6OTPG9/cVrlfpuGtcmd7Tz0Pn4hoXZKi3OJcN+y8UrmtzQfT17Or9OjaENZ87ULwRkdUBAACAWdE80nP0gBWCkbITAxWgDb1FOXH1ypk4jO8vmzp3Kj1eXqxwGLnZP2o/FyXDlJfjNka2Zio9R3V+tfp15NJTu09dazVhAgAAgJnRPNLjCdKjpBkZi5GIIAlDZSNKZS8VvZqtKcuI/exESPsKbLDZHJetqdpvnGWpkBOVHidlFjOHID0uS7TezUvLkrYjdn6e5J6b83pf5udt+rFlcf9637SdsWgBAABABU0lPamMZNdeBOy/4qOZE5WOo2Kvg1ToUZjW0XLNxKgkDNnPDnt05vtIx+kvHDuNZp+DlGTSY2Lcy0cQkCBTeuSm2ZiR/Qei4zfXzpH3GwjXBelRMdI+e10/+ZHYqPRXyRIBAADAzGge6QmZkohUTiz2uMgIgD/6stLj22p9Jz1GKIx85P/UYRCM/P2aHFcWPocsUREnL9pOj9Ps8Zuda2g76kRHRStZg5Oy6v1m2ZxIejS0z34vPZk4kekBAAC4I5pHehpMkJ+7i5efArFUzZ5q7wIBAADAzFmA0uPe76mUjznGH8EBAABAc7IApQcAAACgEqQHAAAAWgKkBwAAAFoCpAcAAABaguaRnslhKfVukR4TpeGJ9K5F75de+K5MJuWTw+WKNgO9e2TMfjomA4PHCvdqUapV78Seiv4XGgMn0hJp8Lon5NC2PWkhAADAXaOppGfAb7Bjg1uSm45Dqe00mEZKz9hgkK7bU0/duQLpAQCAhU5TSo9mZ6zfmE23p7fsZMd+3mI2Z73nNtAsKxQ2Z1/HttnmRGLAZ49C1ufQoLuOObTNZ5i89IQ+Mnz/oV5Pb7R5+zGDkNn7pp9sHpMTMjDo5hnqFOpuGy7UdbfcT81g6f3JSV8nkjJ7z7bPM1rheei9geFh11by9Wl7e89IlvZpM2u+z6L0HLPPTeuF/vJnqOTPX8ewz0frmucSsmrhZ/4si210XiGzZ7HP8d7LHwAALFyaVnrCxhtvkk4WcunJsgdeSvL2utG7DTRsom4T9jKV/XQECXHSE4RlS17H968ZqHAvkM4xSFqevZnI5hBLz+RwLk5x3VR6HGFOeZt8ra7/fG5lJzYnXHv9GeZs1+TL8ufr5TCSnmw+ft1BTnLpirI2J/LnrxSlJ36WxTY6XiqkjcsqAQAAVNKE0uMyKnZT1ixCxO2kp+TFw9YNmR6/mbsNtbr0hM3WberFe5bQv6+Xy4jYORbrmzkNDleVnjAXJz15H5V13RxCnbHBPFMSyNd6zEtP3l8qPbHQhDKtr/NIn5OiAhXa23VXHPvpXMLcthSlJ2RxKp5lsY32777P/PmoCM71ESYAALQuzSM9MDsmU+m6GxQFDAAAYD6A9MxTsmO1Kv9DpnMP0gMAAPMPpAcAAABaAqQHAAAAWgKkBwAAAFoCpAcAAABagrqkZ8XXkR4AAACYn9QlPf/6v9xI2wMAAADMC+qSHoIgCIIgiPkaSA9BEARBEC0RSA9BEARBEC0RSA9BEARBEC0RSA9BEARBEC0RSA9BEARBEC0RdUnP+O+uyi9+dyX9CzAAAACApqcu6flnr3wtbQ8AAAAwL6hLev7pD59M2wMAAADMC5AeAAAAaAmQHgAAAGgJ5kR6vvrzH8iyn/zf8r++8nX789hvJtIqAAAAAHeVhkuPCo/WS0PLAQAAAO4VDZWef3H8uQrZiQMAAADgXtEw6dEjrFhwVr99St7+3YTsO7WjUF77qGtENrZ3SJvG8odk/IYrXbL4qzJ0uVgzZXywJLvPTsmiRw7KpfQmAAAAgDRQetJjrcfO/4Ps+92HIh/8Q6G89jGXSk9J5MaUjL+5T9rW7JXxtAoAAADALGmY9OhLy7Hc/NMf/heRP/xGXnm7mOnRetXx0uPRrM/DL1yRtm6VnxHZtPih7N793c/I6RsiO7s7ZfNxkbbFa2XnOzdl6wMdsvGwqfDWLnst01PS9sAuOZ21BAAAgFZlzqTn67/5UL7+97EE1S89635w00vPFRla1yG7j5wXPfVqW94lyzq7bax7/ryvI3K4r0NWDp6X8cG1WZZoWXvPbY/HAAAAYOHTMOlJj7dqxW2Pt65PyfjoXlm2/aQtDUKj3Hi5zx57LVreJ0Nnb2YtU+mR4/15pmcd7/kAAABAA6UnfZG5VszkReZFq3psRkdxQjMmm5aae/f1SP8bU3L8mz2yaLFe7zCtqkiPYZnWN32NTIX+AQAAoJVpmPQo/Mk6AAAANCsNlR6l1jFX7WMtAAAAgLmn4dIDAAAA0IwgPQAAANASID1NxtC5tAQAAAAaQfNIz9EDdsMfKfdFG/8F+9dZBY6Ws/vj+8uyobdcvD8Dxvf3RVcX3I9zB/y9av1dkKFy3GZU+vf7drfjaNSf+byhSrsN5VH34VzlvYDOCyECAACYPc0jPZ5Ueir+pyhUHLwkDJWNKJVVKoyU9K6XVWvKRpL0sxMN7SuwYY3eX28/F6XHc1vpcVJmMXMI0qN92n6D0Jh+MokRnUNRevqP5pduzut9fT9vU8eWxf3rfdPvjEULAAAAKmgq6amUkQtONLwI2KyPZk68POh1kIqRshMIJxVOEoYiwej3cqJt0nH618SipYxKf++BTEoy6TEx7uUjCEiQKZUzFaaR/QciUXPtMnQdkRCFMQvSo9Kkffa6flZlczNzitoCAABAfTSP9NQ4+qk40rHHRSolTlys9Pi2KjMhkzJk5CM+GnP9uOOy9Hir4ggtydYEedF2epymfTnpCW1HneioaCVrKGR6kn6zbE4kPRrap0qXkycvTmR6AAAA7ojmkZ4GEx8j3T28/BSoIlWzoChhAAAAUC8LUHrc+z2V8jHH+CM4AAAAaE4WoPQAAAAAVIL0AAAAQEuA9AAAAEBLgPQAAABAS4D0FJiQQ9v2pIUAAACwAGge6ZkcloHhibS0biaH98ihybS08ZQq5npMBgaPJWWGEypR5t624fSOZ0LG0qKUyVptG0yDx5kcrvavWwMAANwbmlZ6enq3mNhjN86B4WEpmesgM+GeMjZoPhuhmDRiMWkEw93bYjM2KhOHtrlr7V/76PFiEsr1WoVEuw5tLaavgWgcxzFftiWTnjCX0N+kzRa5MiszkfTYuabr8PPUenG/jglXNujnYOuUK6TO9TOczcH2Z9Yb6mZr9f/zHHYeBUGrNk4+z1AW1h4yYjpXlUwlyGb2vG1Z2T1zO27yXAAAAO4yTSs9usG6TdVIzwlfphu13cy9YBSyOsesuOSbr9tc8w02CEtaLl56cgmw41lZiX6KykK+YVs5ieYSZ3qC3OT9eLEya8nEJsuq+ExPkDIvYK7Ij23r5sKldQJZHUnGVZEJgpc9ownX94liNuxW42TVsuegfeTHgKn0hOehc8gzPVWeCwAAwF2maaXHFblMT5ZV8dKT38/v1ZKesMGODYasR7FcCdIThMYKQVXpyTfsorgoXnpOhAyIl7VIekpeQkJbJxQ6rpMIO6+C9HhpiPpIiY+QwrPI1qbZmRPRUVw030ODtx4ncqLonvh5x9ITskfu+cbPtdBv+lwAAADuMs0jPTVgk2wSIvkDAACYjyA9MDOQHgAAmOc0vfQAAAAANAKkBwAAAFoCpAcAAABaAqQHAAAAWoK6pOefvfK1tD0AAADAvKAu6Rn/3VU5c/3XaR8AAAAATU9d0kMQBEEQBDFfA+khCIIgCKIlAukhCIIgCKIlAukhCIIgCKIlAukhCIIgCKIloj7puTYuH107k74MDQAAAND01CU9v3/ln6ftAQAAAOYFdUnP9W//k7Q9AAAAwLwA6QEAAICWAOkBAACAlgDpAQAAgJZgTqTn5shGef+//m9y/Zn/xf78w69G0yoAAAAAd5WGSs+NH/6ftk6tAAAAALhXNFR6UslJQzNAAAAAAPeChkmPCk0sODd+9nfy0dW/kw//7o9nKD4jsrG9Iy0ssLF9rew+m5Y2gLN7ZWV7KS3NONzXIRsPi1w60i/3t3fK5uNpjVuzcvB8WgQAAAB3mYZJj32HJ5ab1/9Gpn9nblx9tlCu9apTlB4VjZWP9Utb+wrpfvZde7/N3NeQqZOy+yvd0ra0Ux7+5kkvLV8190py+HBJ2laXXN2/3Gv7uvSq9tMhi1YlYnNmr3Qv75DuUslLz5SMbF9r+u2WL77wblYtSE+Yo0rMyseeku6lHbK729/LxKmyj5X9+2Tr6hWy7EsH5fS0yKZVnXY+W4/ftPWXLHXr2nQkGxIAAAAaTMOkR19ajuXm+o+flY+m35ePxv9NsdzUq04V6bEZEi13GZ78Z5CQm/LiVzqKmRqVnm4nO9rH+ODa7Fre3CVDl91H18Znjnx7rd92X7cs63QRsOUqUbHI+OxNKj1pH1tH8rr2Xt+IDJV6ZGVnp53X+PRJWbZuhwy9NeVHAwAAgLmgYdKTZnp+v/+P5eYvLxov+bvZZ3puKz0iLz1Wh/Sceea20tP1bOVRVJ7pybmV9KR9hLo617bSiNzff1JuTIuTHlN+eLAkK5d2yMMvXCm0AwAAgMbRMOnRP0svZHRqRO0/X3fSE46wqknP4Se7/PHWmGxd05lnXm4hPcrpF/rc8daaHa6O59rhfllmZOOLW/3x1vQVebGv29Vdvy+rdyvpCUdk62r0oVJjj8JMna7tY3JNr/U4a+lDTnrO7cuO3l68GI8AAAAAjaRh0qOkgpNG7ZeYAQAAAOaWhkoP/04PAAAANCsNlZ4A/yIzAAAANBtzIj0AAAAAzQbS02QMnUtLAAAAoBE0kfSMSv+a9bKqHB2FnTtg//qpwNFyVKZtytHNmTG+v69wvUrHXRPKLrjreB6mbKh3fXQtsmH/hcJ1bS7IiPm/I2Udo2w/p2wIY5n11iKrAwAAALOiiaTHMVLuc9kOIzcb9leXniAAQ+UDJlR6nJQ4qdDPToS0r8AGKzZOXFLpsVjhMG1NG61XlBotP5BnYcwc+v19J0zr/Xwv2H5iQRmx8/Nou6P5pZvzel/fz9vUicfPBMz0G8YEAACA+mk66QmbvUrGuIxWlR4VCc2YaN1YKlRmrFQc1bJR6e9NMycu61IpPUGUQuYozSA56XHS4eoWBUTn6epkc4jaWVRmkmxNJjax9Hh07rr+HDMnsj0AAACzpnmkJ2RKLKNZBiUVhVxonLhY6fFtY+EY2n+gcJTksjTVpMeVhc9Dtt9UmHKh2WDERPsKAuTaejk7d8CMW8zGFDI9SRaomvRoaJ9OehQvTmR6AAAA7oimkR4ViiA6sfxUy/Qo4/vDEVbIzLi2QXpSWQp9V0pPLlhWYIxcxP04QsYmz7akx1uZoCQvIruxwvyK7/SEOcfHW9lz8Jme8Dx0vWnfAAAAMHOaRnoah8pDkJC7iH8XBwAAAJqTBSg9AAAAAJUgPQAAANASID0AAADQEiA9AAAA0BIgPSkn9qQlAAAAsABoLumZHJae3i0m4n8YsAambmn4FRnovXNJGRvcIgMn9NOEHBocTm9X5dC2PTKWlE0Oz2Dec0BpeCItug3HbAzos942LJPp7QytU//z1ec5GwZuOZcidu4msrXr70M0V51D4X7ASK37HZvdHAEAYP7SNNKjwuDEI+A35d4gF8eMkPjNym9cAycmrHyEugODe/wmp5u6a2NFZpsv9+3sZ7tJuv7C2Ie2uetDduc17Ux5ujlqXTu2l56w+YZ7GdnmqmVuDmHsvE3lGge2+XUVxj7m+9rj5r2tKGZuzRNZnSBkY4Nlu5YwD7cuLd9j5xoLwcCwex6ujs5jT/Z87fMx94MghedUGgzPOeKEe9ZhLBUZ/d7CdxvmOGDmpnVtuf8ZpCd/bmEevm9PNbm0a57Mn4uusSpRJs/NX9cYvgP/DHXOfo32e7Eyrs8vvz+ZPe/8+w2/e+F7TnG/O8Vxwu9JeF4AADB3NJH07Cn8P/5MgvwmajdAvZ50G6PWD5tNqKv/dV9demJx0Y1UN7U8UxPah40qbIahrzyj49rq+JpV0PJMJKS4GWvGyN2K5+AzJ2YNA6bvamsMwhWPnfVb4+gt3mC1buh3IJaSMIYfO9QN8w9C49au88w3c7tZm/shIxbG66kiPUG0Qh0ro9rGi0Imd9p/Demx2LWGeRRx372bf/qdhe8qZHpSYYqfYfx8qj4zg5aF8ex1uB/6saKV/46FupXSdcy19c8/9FNPdgsAAO6MppEe3QzS44lUCIJEpNIT6uaboK+bbUh53SAuKh+3kx53P99I7Rz9JhXap6IWKG5myRz8xld7jcWxs+OiGtIT+lDCHHTTDVkKix8jlzFHKilOZNwziqUnPN/bSU+4F/oNz8H9DAKp/Trp0fqZcGidaL75PBJMnfCdOIHLv6OQfaqUDk/2DL1gpnPw7TNpC9IUxMzfz77rwu+YW3dV9Hcnel7ZOEgPAMBdo3mkRzkRHwn5o59sU6gtPXZD0SOFbfl/+bvjgzxbodiMRXKsoBE2vMKRRjXpkehdEZ8pCkdVbk7Rhhf1XyE9fm2115iO7Y+3dN5evBwuwxDa5uNJtLmHYxh/tJZJiSsL4qLPLl/7raUne5/Gy2G8aWcS6GWiKD350ZWOF9avnzPpieZbmEcia6FOQewkf9cqXl+a8QpzcPjvoNcJqP0d2KZSnJe53xsvuf5+Ps/4e3X92XKtUyV7mY4Tyg5Nuixg/LsGAACNpbmkZ5ZkMlMl8wAzI8tozIAgHPVnKHKhqWc8AACARrAgpAcAAADgdiA9AAAA0BIgPQAAANASID0AAADQEtQnPc/8z2l7AAAAgHlBXdLz+1f+edoeAAAAYF5Ql/QQBEEQBEHM10B6CIIgCIJoiUB6CIIgCIJoiUB6CIIgCIJoiUB6CIIgCIJoiUB6CIIgCIJoiahLeqaeeCL96y8AAACAeUFd0vObT34ybQ8AAAAwL6hPejo70/YAAAAA8wKkBwAAAFoCpAcAAABagjmRnutPPy3X1q2T3zzwgP354c9/nlYBAAAAuKs0XHpUeLReGloOAAAAcK9oqPSkopNGXeJz8aQcfmcqLa2b4xfzz9deLcm1/HJBce2dkbQIAAAAIhomPXqElUpOtah91DUiG9s7pM1GSY5v75K2Rw6mlerkiizZftJ9vD4im1aXirdvyYjsPpuW5YwPrnVzXdwpXU++lt6+JYf7OmTl4Pm0+PZMn5T+N93HTX/R6Z/VCrlk1jn0SEexrmGleY6H08KY61ek++Nr7TpXrnhKDl9PKwAAACwcGiY99h2eRHA+MuUfvf6fCmVarzpOegKZGJw5KF98YIXd4Ed84qdruZOj8cMlaevem9W3XHxNNq0xQrD487Ln3HnZ6Hf90OZFm/k5L+ue3CErl6q0dLkKgakRW29ZqZRJzzJbr7OQJVLpceJyXnZ3m7F1LqvdfI6aew5/z3D6hT4nSNvH3Nr697lxvqRi97bs9BLzxQNOhu73883RvvT535QXH+1IMlb5OJde7Xcy9Jf7Muk5Ptjn1mAE6XiSPNvY7qRH3twlyzp3FW8CAAAsIBomPfrSckF6PvufZPoXv62QHq1XnSjTY8QhSE//A2tl5zs3bY1FW0+KvLVLNr58RW5oQYX0vG3qd8iSvtd8n156TBvbx/SUtD2wS06bcq1zbdrI2oE+Gfe1NTOkGRNbPtrvZMC0Pa2DTb8rm49nFTPpufHWM9K12EtPu5MdvefwMmL6uL/7GdvHTiMuOtc2vb76mmxcrvOekkuX3RqXLe+X4+ZZ7HzHdxG4/rKsa+8TfU6bdLwC0TjtXe7ZSJ7paVtekpeumg833pW2R1/L7iuZ9NjnH+YNAACw8GiY9BQzPQdl+vc35SMjDzL96zvK9Kxc/FThiEaFIpOUVHrO7jUbfdjEFSc9cZuV7T0ydDnPAGmbvP94Du54KxeYInmmxxPNJZUeexRWyt+5iY+3XnqsQy690CNtXzkol27kovKzAzuk++OR3Ni16fGc6XNNdemx4/g5KJn09OVjt1mpysmlJ2SSAAAAFiYNk55qf7X1+9crMz21X2auLj2bl3fKuhfO59mJ4/2yNZzR6JHM4j6XMVnRYd952bxCj478ezwh02PaZJmedUYuQrlSkB4nDzbT86o/3hrtl6GzLgsTcyvpufGDPjluJnzjHZ8FMn0sWq5ZGl9VMz1rnhG5uE8eXuxkZZGRIl2jvs8U5nPj5SgLlWV6TPmRp+ThwTEZv3xFLl3VuflMj1nnkihbUy3Ts0SzZRFkegAAoFVomPQ09kXmDisGXc+el2tv7JJu/37LVp+wcO+nGFmQKTn8ZLe0rd4hm8OLvOEdoOU9hXd6Qhv3XlAt6TGc2evfvfHHW2aMRYt926jaraRHj7Hc+zr9snG1zmtKjn+zx63tvh1O6B57KnvHR84elHUf77DvFzlRGfN1e/L+k0zMw/49J31PZzxIj/h3h0z5ovX5Oz0j29f6NayQ05p9iyi807O8v3gTAABgAdEw6VF+91d/VSE5ccyU8WfXyrInRxbsn5fPmumTsvNMWtgY+OstAABY6DRUepRqx1watY+1AAAAAOaehksPAAAAQDOC9AAAAEBL0ETSMyr9a9bLqvKouzxallXmeuhcsZaW5/+ujrYpRzdnxvj+/C+pFB1n1Rot83OwEfd7QYZ610fXIhv2Xyhc1+aCfQFax9QxKtZj2BDWfO5A8UZEVgcAAABmRfNIz9EDVghGyk4M+nsPRHITYaQniMP4/rJs6L1T6fHyEguH+dxfkBojPeW4zWhy/xYc1fmZ9vtHbb/V5CWXntp96lqrCRMAAADMjOaRHk/IoKQ/M4xEjJTLNnui9/RzQGWm/6irY8WkN82c5FmXtHwokqdKMVHpCSLk6halZ9QImquTzSFqF9B28Z+9h7W57FZxDk764nmY9VTMCwAAAGZKU0lPLCNBHKxc+KMuKwwqNCb0vl4H6Rkpu2Mp185lVYYy+ZDs2Kqa9PQnx07hsx5paZsgL1ZqfBYoSM8G369mpTQbM7I/zlDF0jNakakJ106yvPRoNkj79Jmu/EgM6QEAALgTmkZ63DsvTiBcBieXiQI2i+MEQ3HSk7+LE2QpezfIE/qulJ7Rwj0rTBUEecnFI0hPaOvmeaFCbNxY1d8VCmWx9GTPwWd6wvPgeAsAAODOaBrpaTT5EdPdRI+5UtyR2p1SeeQGAAAA9bAApcf9pVWlfMwx/ggOAAAAmpMFKD0AAAAAlSA9AAAA0BIgPQAAANASID0AAADQEjSP9EwOS6l3i/T07pGxqHgguZ4tAyeS623DMlksagAT9c31xB6z3i1paU0mh+v716e1frruCswcblenNDyRFiUcm9n3ZMaq4MRwWiKlOfluAACg1Wkq6RnQzdVsjLffZO+cZpCescEZiMIdMBPpqWcOM61XkyrSMzk59981AACA0rTSM9BblkPGSg5tc5uyy3JMmGvNjBxz0jK8x9YJG3fP4LGoQ62bb7J28482XSc9eZ2xwcosSo8Xo5L2a9o6STJj23Hcz+IcJjJhi2UjrCGVhjBv15+iP6M19u4pioudv1t7WJ9dt59nPqa7F9qGcp1rmG/AfZ7wa3M/tZ32OTa4xbYNayrMP8zF/k9n5HNxmOeSPaeIE6FOWIPD9j+ZX4fvJn1eNfsFAACYAU0lPfZ4K2zg/mdReoKc+E12UI/DNJwgFbMaxc1R7+mGn13b/qM6VbIQoT9bJ7tflJ7iHPKN2rVVKciP7PRznMWqLT06VlhjJBM6hyCH4tYTrzuM6eaTS4+7dpFLlVKUnVh63PNyP1PpyfuqFDB3RLnF9XeiKFj5MzT1B1Vay7Zu6D97Tv67yciOPmv0CwAAMAOaSnrCZq7MRHoyqajIdHhOuHdm8mzHhNuwTf3seMvX0Y1Xx4ilpLTNbcp2s4+kyIqOuWczQIU5TJg2QQhcmyAgNiPj6+X9+AxQEKdt8dqSNUbiFWShKDv+ZzRmkBaVnLgsrCmWQHe/fEvpCWLn6laXnnDPCmVvUfLyuYUsnqsbMj1hTeG7yfryc67s91iVbBAAAEB1mkd6mpAKiVpQcEwEAACtBdJzCxa29AAAALQWSA8AAAC0BEgPAAAAtARIDwAAALQESA8AAAC0BHVJz9QTT6TtAQAAAOYFdUnPzYsX5Q/nz6d9AAAAADQ99UkPQRAEQRDEPA2khyAIgiCIlgikhyAIgiCIlgikhyAIgiCIloi6peeDD6crygiCIAiCIJo96pKeq5c+kN9c/Ch9GRoAAACg6alLevb130jbAwAAAMwL6pKe/s/8Pm0PAAAAMC9AegAAAKAlQHoAAACgJUB6AAAAoCVouPS88u2btl4aWg4AAABwr2io9Oz7RnXhCQEAAABwr2iY9Jw//YcKyakWWq86I7KxvUPabJTk+PYuaXvkYFqpTq7Iku0n3cfrI7Jpdal4+5aMyO6zaVnO+OBaN9fFndL15Gvp7VtyuK9DVg7O4n+tfvqk9L/pPm76i07/rFbIJbPOoUc6inUNK81zPJwWxlx/2/WxtNtenv6btcI/SgAAAAuVhklPxbHW/mm5MvGRXBn7oFBe+5jLSU/zcHvpUXG5cWafPLy4vnnPVnouPf95JyVn9sr9pdfk9OUrcunc2zKeVvTcVnrOvSw3pkVuvLFDXrxurqfHpP+ttBIAAMDCoGHS8x8/W8zovPbzj+TUTz6UH20ulmu96kSZnu69mRj0P7BWdr7jRGnR1pMib+2SjS9fcZv/4ZKtaz/2qXi8bep3yJK+13yf52Wj7vqmje1jekraHtglp0251rlmNvxrB/oiaXAZE1s+2u+kx7Q9rYNNvyubj2cVc+l56xnpUunRubSvze45zsvu7g7bx/3dz9g+dnZ32rm26fXV12Tjcp33lFy67Na4bHm/HDfPYuc7vovA9ZdlXXuf6HPaVCFZ0TjtXVm2JkhP2/KSvHTVfLjxrrQ9+lpFNue0mW94Bm1r9taUKAAAgPnMnEnPD743LVc0e/Dr6bqkJxCkZ+XiTlnW2e3ikX1y1GzQp7NKifSc3Ws2+odk55lQwUmPSkjYyJe198jQZS9DimmTZ0PiObhMj7YN4697Ps/OFI63to8V5pJKj627vCvrJ8702HkbGTs82CddnV32aO+wka+21SXZfSTKBtm16fFctYxYNM7qZ7LSTHr6RrKyNitVOce/+XlpW/HV7Hplch8AAGCh0DDp+dvH3i/Ize5//b7817GPRK4X3/XRetWpJT1PFY5oYoGpLj1ro2OpSulZOQvpqUbI9GTcTnpKuXjE0vPSYx1y6YUeafvKQbl0IxeVnx3YId0fj+Qmkx7T55pbSI+fg3J76ZmSto8/5bJAHqQHAAAWKg2Tnsa+yNxhxaDr2fNy7Y1d0r3clW31e/eype563Gzah5/slrbVO2RzeJH3zEH54gMrzObeI3vO5XIT2oxM6VUt6RH7vozL3vjjLTPGosW+bVTtVtKjx1haf9mX+mXjand8dfybPW5t9+1w0vPYU3mW6OxBWfdxzRp1eVEZ83V78v6t2HRmVw/rGu2zWmGegz/eMpx+oc+WL1q/L5Oeke1r/RpWyOnprIs8W2UiPA97hAgAALAAaZj0KKngpFH7JeZKNi3vLLxDA9GLzHMFLzIDAMACpqHSo1T8FdcshAcAAACg0TRcehQVHH13R19a1p+1j7QAAAAA7g5zIj0AAAAAzQbS02QMnUtLAAAAoBE0j/QcPWA3/JFyn/3Z33ug+j+Sd7ScicH4/rJs6C0X78+A8f190dUF9+PcAft5SPsznzeURwt1hspxm1Hp3+/b3Y6j0fzM5w1V2mVjnau8F9C1IkQAAACzp3mkxxOkZ0Nvn6xas77wZ+IWFQcvCUNlI0pllQqVlfWmftnU9+Iirq/AhjV6f739XJQej5UebVNLepyUWcwcgvRon7bfIDRJW9tfQNsdzS/dnNf7+n7epo4ti/vX+6bfGYsWAAAAVNBU0hPLSJADu9F7EbACpJkTLw96HaRipOwEwrVzkjAUCUa/lxNtk0pP/xonWjaDs0b7G7WZpiAlmfSYGPfyEQQkyJRmpTQbM7I/zlC5dhm6jkiIgkQVpEelSfv0ma5V8dwKIgYAAAD10DzSkxz9BDmIMyMWe1ykUuLExUqPb6syE+oPGfmIs0ROHC5UkR5XZlHh6I2OuaI6VnhMOz1Os8dvdq6h7agTHRWtJBtTyPQkWaAsmxNJj4b2mR/veXEi0wMAAHBHNI30qFCEoyKVgZC5qXivx78jo1kVxUmFZmjiTE8uTYHQd6X0jFbci/txhIxNnm1Jj7cyQUneu3Fjhfk5oQmEOceZnuw5WOlxc3NCxzs9AAAAd0LTSE/jcO/3VMjSXOOP4AAAAKA5WYDSAwAAAFAJ0gMAAAAtAdIDAAAALQHSAwAAAC0B0gO3ZeBEWgIAADD/aB7pObGnsZvr5LCUBo+lpTNiYNuwTKaFd8yEjKVFM+DQtj31tTPrbirM91oankhLK5gc3pMWAQAANJSmkp6eSFIObdsiPb1b/NUxGTCfBwbdBjrg67mfE3JouBzVnbBtS1pX72u/5l7ct1LSMu3zRJ7JsPWM8ATpsde+38nh+B8rzMe0cqQbe6/KSRjD//RjH7KVkjZGTrJ5hTn25hu/jmfn56VH15+vUTnm5xmv3z0nt9ZjtswJhy+Pr7e5tbtx/drsPMpmvvoMdS6hnZ+DGe/QoGuXMjbo+g/PI15baditVduFdbm16jyM7B5w9d199wzsT207qGO771Tr1CWAAAAAEc0jPZ6xQd10c+HQjXDMbnySZQ1S6XEbofupm6WVDJ/pCXWLmaSiAMWbrb22MpHXqdzk86yNvXcitI2lJxcNFSmVk0IbLyXZ5p8Jia/jP4dMT3geTqCUXHri9YdMT5AQlZjs+RmC9IRuQr/aLozpxHFP1M49xyCDaSYse+ZRXYs+lyzTY/ocHM5k047p1xD6qPgZZYnCeiq/CwAAgJnRdNKjmYA4+6LohheyBFZ6/Kbrjq9S6XF1QuZowGcpgkw5jhUyBqHv7NpLT6iTtwtMZGWp9IQsTi4lgWIbnU+Qi0AsXqUgGH7+lXO4nfTk6wnPTylITzgK0/mrJHoByaXHtwuyWVN6/DO3uGeu2Dn47yH0XzxyrCY9bt6hbfguQ/9IDwAAzJamkZ7s2MNvqIXjDN0wNROyzW2u4b/6q0lPlmHRutHRUZxFUeJMkttIJ7LxZ3K8VYqP3zLpCUdk8XFRGLuyjevfyUUYO5CtMTneymWjhvSY8vh4y/bpn18+l5Dp8WuO1lj1eCuRnTBuQej82mxZvG4Vlm2h3+h7tmPm0qP17Pfgj/103bns5FmzVFABAABmStNIz63INspCluBeMruXku8VRdEAAABoTeaF9AAAAADcKUgPAAAAtARIDwAAALQESA8AAAC0BEgPAAAAtAR1Sc++/htpewAAAIB5QV3Sc/XSB/Kbix+lfQAAAAA0PXVJD0EQBEEQxHwNpIcgCIIgiJYIpIcgCIIgiJYIpIcgCIIgiJYIpIcgCIIgiJaIuqTnxsVrMn3+WvoyNAAAAEDTU5f0vLf5v6XtAQAAAOYFdUnPlf/jb9L2AAAAAPMCpAcAAABaAqQHAAAAWgKkBwAAAFqChkvPb795xNZLQ8sBAAAA7hUNlZ5r/3a4QnbiAAAAALhXNFR6UslJo65sz8WTcvidqbS0bo5fzD9fe7UkC/VfGbr2zkhaBAAAABENk570WOvqgctyc+gncn3v389QfEZkY3tHWngPGZHdZ9OynPHBtbJy8LzcOLNPHl5c37wP93XYtvVy6fnPyw39cGav3F96TU5fviKXzr0t42lFz8r2khxOCws4qbz2ckmO64fpMel/q1ABAABgwdAw6fnN+qGC3Nx4V+TDd434HPh/C+VarzpF6cnE4MxB+eIDK6TN3BvxiZ+u5R32evxwSdq692b1LRdfk01rOqVt8edlz7nzstHv+qHNizbzc17WPblDVi41ZYu7XIXA1Iitt6xUyqRnma3XWcgSBenRvnZ3m7F1LqvdfI6aew5/z3D6hT7bR9f2Mbe2/n1unC8dNHfflp1/0Wmvv3jAydD9fr452len+XlTXny0I8lY5eNcerXftmv7y32Z9Bwf7HNraF8hxyuSZzfl0g9KcslfLXv67cJdAACAhULDpOfKqt0V0nPliX+QP5hNNS7XetWpLj0rjSgs6+x28cg+KxSns0qJ9Jzdazb6h2TnmVDBSY8KSsiGLGvvkaHLuQxpmzwbEs/BZXq0bRh/3fN5dkbLrVx4kYnnovccTkZs3eVdWT9xpsfOe3pKDhsx6ersMn2qqFyxArX7SJQNsmsrSfqcHNE4q5/JSoP0tPXlR19ty/tdVifDSI9meqbd1cqK+wAAAAuDhklPmum5+rdmwzYb6UfvvnVHmZ6Vi58qHNHEAlNdetZGx1KV0rNyFtJTjTzT47md9JRy8Yil56XHOuTSCz3S9pWDculGLio/O7BDuj8eyU0mPabPNbeQHj8HZWbSo+RHeUgPAAAsVBomPR+euljM6NQIrVcdJxw2e2JCxaDr2fNy7Y1d0u2Perb6vdsd1ZhNXqbk8JPd0rZ6h2x+xItAOA5b3lM43gpt3BFZLekR+76My970exGYkkWLfduo2q2kR6bf9UdX/bJxtc5rSo5/s8et7b4dTnoeeyrPEp09KOs+7o7anKiM+bo9ef/Z8ZbjYX/kp0dW4+kxmilftD4/3hrZvtavYYWc9hkdy1vPuD4e6MuKFm09GVUAAABYODRMepRUcNKo/RJzJZuWd8pmUg4FsheZ5wpeZAYAgAVMQ6VHSf+KazbCAwAAANBoGi49igqOfcdn1W77s/aRFgAAAMDdYU6kBwAAAKDZQHqajKFzaQkAAAA0giaSnlHpX7NeVpVH7dUq/Wxiw/4LxWpHy9G/QKxtytHNmTG+P/9rJcWN5ctM//q5KB8XZKh3fVxQOa+aXLB/9TVS1jHKhb8AC2zwa5ZzB4o3IrI6AAAAMCuaSHocI+VIOKpJgJGSIABD5QMmVHqclDip0M9OhLSvwAYvUUoqPRY7lpEo2/cF23eOu87mZebQ76UnyJmdl5aZfmJBGbHz82i7o/mlm/N6X9/P20pXLnu2f71v+g1jAgAAQP00nfTEGZRYWjKMFKhIaMZE68ZSoTJjpeKolhmB6U2lyWVdKqUniJLPHKm4FDJITnqcdLi6RQEZlXFfJ5tD1M6iMpNkazKxiaXHo3Mfl7h+EDIAAACYDc0jPSFTUus6KndC48TFSo+vGwvH0P4DhaMkl6WpJj2urEDF2LnQbDBion0FAXJtVXrEytJQMudCpifJAlWTHg3t00mP4sWJTA8AAMAd0TTSo0IRv8czvt/JRQVWerR+OMIKGRrXNkhPmlUJfVdKz2jhnn33JhOOQMjY5NmW9HgrE5Rkzm6sML/iOz1hzvHxVvYcfKbnts8DAAAAZkTTSE/jUHkIEnIX8e/iAAAAQHOyAKUHAAAAoBKkBwAAAFoCpAcAAABaAqQHAAAAWgKkBwAAAFqCJpOeY2lB4zmxR0rDE2npHTOwbVgm08I7ZGxwT1rUUCaH43+AsYGcaMS8Z/K7cCx75gODrr6uqbStLAMn/I3JYf/hzsj6q8Jc/D4BAEDjaVLpmZCe3i3SYzYy3cQGzObfY6RibHCLLbcbndlYe3rLcmhShWOPiS1RP+LamzaHzL0xc31om2sbpEf7tXW0TFQw8vYqMId0LL+RhvGzjc9v6q4P13/aJvStY4V5Z9g5DGdt07kooezQ5DG/obufup5DurFnG23+rEJZ3JeVAP8s9LnZz9E9bW+fjZ+3u++ea47WCbJ4LFtX5VzcPX1edr7++Q+Y9Q+ccOO4fl3bdOwB80x0/MlQ7uccP8sildJjv28jOiV/XSk97nlpX05U3VzsvE/oPbf2Q4Nu7PDTfveT+p1F8y3My38PvY0QPgAAmAuaU3oyqdjjNv+wIUcbvW5GQQQG0k06yjQE6YmFJd/wlAl7P0Y3M60/Nuj6zTa5gvR4GTEb4YD2l7RxuI0/bMgZVtjc2m41F5fpSaXHb9iRMAWClMR96X29VvHKsxX5PZ2Lluuztpt9QXYCTkIUrafo3GrNxQlkkB43bsl/R0EQrPQkYwfJcfMMzyxfjxOlmErpcbIT1U2lJ7rOpWeLLdc5ht+PqtJjcX3Hv1OxjM1Z9gwAAO6YppSeeOOwmZ6KzcVtqIH0aClun25QYVMr1RANJWzih7b5TFIyvu0/ziZUtAl9xvOcyOcYjthuM5dMerQPP16euXI/4wyV7XOy2Fd4FuE5hkxLJkTR883wMpKTS4RKXaDWXNyYeabHPn+/xlhK0rHD54L0ROuplJ6JTNKCTGXPL8hNKj0WL8tRpqfWcw7z1jm5tdeQnij7BwAAzUlTSo9uLOGoIN4YwxGV3edstiQ+pojx7eNN1x9FBNHQfsOxiVI83nJHQkFyss04HGdsy0XCHYtUtgl922OxaBxLIj3pXALhnR57PKb9V5EefWa27WDt463wU+cZ7uVC5I+SvLS5+36M7KHGwuHHi55tNhd//OOez+2lJx27KD0TFcdbWh6yQoFwXBfGz/EClEqPn6P7zmpLT/hOSnGmx//OhfmGsd1z98/FhLbNBR0AAJqFJpOe5qBSogAAAGC+g/QAAABAS4D0AAAAQEuA9AAAAEBLgPQAAABAS1Cf9HTtTtsDAAAAzAvqkp4bF6/Jh+feS/sAAAAAaHrqkh6CIAiCIIj5GkgPQRAEQRAtEUgPQRAEQRAtEUgPQRAEQRAtEUgPQRAEQRAtEUgPQRAEQRAtEXVJz6eP/TL96y8AAACAeUFd0vM/vfTztD0AAADAvKAu6flH/+1naXsAAACAeQHSAwAAAC0B0gMAAAAtwZxIT8+b5+WPf3xa/vFLP7M///4319MqAAAAAHeVhkuPCo/WS0PLAQAAAO4VDZWeVHTSqEd8rr0zIscvpqW3YPqKXEvLbsmUnD5yUi5N5yX3P/12fgEAAAALioZJjx5hpZJTLWofdY3IxvYOabOxVoYe6ZAl20+mlSo5u1cO6883dsjQ5fTmLbh8UB5e3CVb3/DXpp/TkQDVh5m7nUQVzh60a6p5HwAAAO4KDZMefXcnl5t3ZdPZ9+T5qx+KTBdlSOtVx0lPYHe3EwX9ue7JHbJyaYdsOjxl7y1ZqmK0Qsb1IkjP4ZK5jsXJ1D8icv9y9/m4d60ufz1u2q1sL9m2xwf7ZJnpc8n6XXJ8Kh+zzUhRGNNy5qB88YEVtv1IVJxJj+lz8/aH7H1tNj64NpO43WfzsV+0Gazzsq7vKVm2uKOwxt3vvCu713XKstKIzVyNbF8ri0ydL77wrmh2yq09f04AAAAwMxomPfrScprVee0DkXcvvFso03rViYSle29Bepb0vSbXpsWWq+jc0OrTJ2XzcUmkJ3BT2r7yspWGGzZ7MyUPv3BF5K1dsvHlK659JD1ty0vy0lWRnWvM2I++lo95oM+OGeh/YK3sfOem/bxoa5yFyqVn46vGdqanZOWgO8pT8bGYsW1bc6/tgV1y2kjPosfcutx4Kjk3rSBpvXUqSueM4Pk6G5f3yNCbe+UlO3kAAACol4ZJTzHTo/GOyLUrFSI0m0xPOBqyknL5oAxdVvE4n4lGQXquj8imFaGfk7L1uJMUlRAVkEyMYukxwmF5tWQEqD8f09cJrFz8lBurglx6wv3QZ5CeeOyV7UZgLvv5S3GNQexCWdezxfegDg+WbEYIAAAA6qNh0lP5V1tX5N1z71RIT+2XmWcoPSoWmu04s7eK9Nw0P7qkbd1B38uIzZbI1IjLvBzvNxLkz6VqZHqWbD1ZU3o2L++UdS+cd5miAreXHh07y/SY+V0K0ia3lp627h1y/Kq7Fzg98FCxAAAAAG5Lw6RHSQUnjdrCAwAAADC3NFR6Vh37ZYXoxAEAAABwr2io9AT4F5kBAACg2ZgT6QEAAABoNpAeAAAAaAmaSHpGpX/NellVHrVXq/SziQ37LxSrHS1H/x6PtilHN2fG+P6+wrUby/dj+l+1pk+G9K++Mi7IUO/6uKByXjW5IP4P4mWkvF76jxZuWjb4Ncu5A8UbEVkdAAAAmBXNIz1HD1jRGCkXhSMIQ4aRknB/fH9ZNvTeufRYjmo/KjdlKx8bemMBMeXluI2RrZlKj+3XsWHN7aSndp+61qKIAQAAQD00j/R44gyKClAFRiJGymUrQ1pXPwdUZqxUWNEwYlIQF8VlXVLpsZkeFQ+VHSsgXn4yVHoOeNFx94rSMyrjvk42h6idRTNUhXv5Wl12qzimzn1c4uyOWQ/ZHgAAgFnTVNJTlJFok7dHTutd1keFxoTKg14H6dGjI63jpMIJzFAkGPbozPeRSo8liFIkIHqkpW2CvFipOefkJ0iPZm+0jh65aTZmZL/KSiBIj+s3lZ6QuSmIloqX9mmlR4UsZL6QHgAAgDuhaaRHhSB+j6fmcY4/LtL7ipMe/z5QdHwU3g0KhL4rpWd0Zu/0RPKiBOkJ/TrRuVAx53isVHrCnGPpyZ6Dz/Tc9nkAAADAjGga6Wk01d6dmXv0mCslf5H5TuBFZgAAgDtjwUoPAAAAQAzSAwAAAC0B0gMAAAAtAdIDAAAALQHSAwAAAC1Bk0nPMenp3WKj0QyccD8PDQ7LZPFWDSZkLC0KTFbrY0IObdtSpXzuOTR8zP6cHC7bdZaGJwr30+sKzHpqoX3Wwj7TE3vS4vmFWXtp0D2/agz07qn9ezBjJqRn27C8Onib32vzLG/7XQEAwKxpIuk5ZjeY5uEW0lMNs3kO3KMNa9KbVpCeukF60tLGcovnWwDpAQCYU5pGeiaH98ihSbfJukzPhM3KWBmym9Ixt8naLEvxnmZYipv9MVdnW74hFzZouwm5zIxuMsX/mte2ro6W6byUscF4cw/96zy9rHnpycQj28CO2XXpeGH+9trOJVnjLTdHU2fbcI05u7UWMj1RX+G6Qsr883Bik88hrqf3dLpjUZZCx9Kxq0lP+B4mh93z0zlr+5Kfb/Y9abtqcxL/O6BzMXW0fvjdiMcK/Ya52PrmOYRnbu/7dvrd2Tq+TTzvbCxPaB8ydoW1RvcH9HdUhUm/E589rPkd+jL7nM24Pb3uu4oFJ/6dcb/v4p5Ntf4AAGBWNJH0RFkK+//o3cZij7vsZhU2Iv1ZvBc2JrsJ+XJbJ9rMwubpmMikSNtl7ZVsk3GZHt3s3ZFbnPGIpcr/9Bt42GAzicjmHTJHuVRVrlHshliRYTJ107Xmcw5Hgon0SN5XuNb7cd/Z89C5RM8uloCQ6cnEyI+ViYCXh/CcinLgN27J55uPnx/JpesN6wgCHPrNv7+83yA2YdwgmHa86LuL6xT60nWb9YaxUmFM550J+Db//Zmft/0OE+mxY/ifhefmy9LvoaI/AACYFU0jPRb7X8FuU42vi//17X9G98LGFG+ScR3dUNyGNxH1X0N6RDcZt1EWpMJucoHa0pOJgd8Aby09Ulyjlxs730Q8whwq5hw9s4L0RH2Fa91MM7G0hLXlYhPaBArSE41VlJ5c3gZOuAxYmG/Y1Et+vrEgFOdUXG/4vlxf4fvx35/N4hTnWk160u+iWDdfu0pPmHMujHpdzp5zmHf4TmPpqfUdZtxCevLnJllZYW3x95j9TgEAwGxoLumBDLd5Lxxuu2Gf4BgHAADmFqQHAAAAWgKkBwAAAFoCpAcAAABaAqQHAAAAWgKkBwAAAFqCuqTn08d+mbYHAAAAmBfUJT1nf/e+/OL6zbQPAAAAgKanLukhCIIgCIKYr4H0EARBEATREoH0EARBEATREvH/A1iEdbhTalLoAAAAAElFTkSuQmCC>
