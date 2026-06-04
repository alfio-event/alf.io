# 4. Registro de Riesgos

En esta sección se identifican los riesgos que afectan directamente al proceso de ejecución de las pruebas unitarias por parte del equipo. No se consideran riesgos del producto, sino aquellos factores internos o de gestión que podrían impedir el cumplimiento del plan.

La severidad se calcula como: **Probabilidad (1-5) * Impacto (1-5)**.

| N° | Riesgo | Probabilidad | Impacto | Severidad | Plan de Mitigación |
| :--- | :--- | :---: | :---: | :---: | :--- |
| 1 | Desconocimiento profundo de Spring Boot | 4 | 4 | 16 | Revisión de código por pares. |
| 2 | Plazo de entrega ajustado (10 de junio) | 5 | 4 | 20 | Priorización de los módulos críticos según su impacto en el negocio y estimado de 10 horas semanales de trabajo. |
| 3 | Conflictos en la integración (merging) por alta velocidad de trabajo | 4 | 3 | 12 | Uso estricto de ramas `category/task`, comunicación constante y validación automática en Pull Requests. |
| 4 | Dificultades técnicas con las herramientas de automatización | 2 | 3 | 6 | Soporte técnico interno por parte de los encargados de estas herramientas. |
