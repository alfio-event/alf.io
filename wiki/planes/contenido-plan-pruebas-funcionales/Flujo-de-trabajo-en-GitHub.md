# Flujo de trabajo en GitHub

Se procuró que la organización para la realización de pruebas sea lo más ágil
posible. Para ello, cada Requerimiento o Historia de Usuario será detallada en
una `Issue` de GitHub y sus respectivas pruebas a través de `Sub-Issues`.

Cada `Milestone` debe de contener los siguientes dimensiones de cada prueba:
- Alcance.
- Criterios de entrada y salida.
- Nivel de prueba.
- Técnica de prueba.
- Entorno y Datos.

Para cubrir la parte de planificación de iteración se usará las `Milestone` de
GitHub para poder organizar las iteraciones.


- **Objetivo del Ciclo:**
  Define qué valor de negocio o módulo funcional estará terminado al cerrar el hito. Esto evita que el `Milestone` sea solo una bolsa de tareas sueltas.
- **Plazos de Ejecución:**
  Establece la fecha de inicio y la fecha de vencimiento. La planificación de la iteración mira hacia el final de una sola iteración y se ocupa del backlog de esa iteración. La fecha límite obliga al equipo a definir qué tareas entran y cuáles se postergan.
- **Alcance Comprometido:**
  Enumera a alto nivel las historias de usuario principales (issues) que conforman este hito. Durante la planificación de la iteración, el equipo selecciona historias de usuario del backlog de entregas priorizado, las elabora, realiza un análisis de riesgos y estima el trabajo necesario.
- **Criterios de Cierre (Definición de Terminado):**
  Debe especificar explícitamente qué debe ocurrir para dar por cerrado el Milestone. Esto varía según el tipo de hito:Si es una Iteración (Sprint): Detalla que todas las características deben estar integradas, probadas a nivel de funcionalidad, y que cualquier defecto no crítico que no pueda repararse en ese tiempo ha sido devuelto al backlog general. Además, define los límites aceptables de calidad, como la densidad de defectos permitida y la confirmación de que los riesgos residuales son comprensibles y aceptables.
