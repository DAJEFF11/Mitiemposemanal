# MiTiempoSemanal — Sprint 2

Aplicación Android en Kotlin para administrar categorías y tareas y registrar el tiempo dedicado a cada actividad.

## Configuración

- `applicationId`: `com.senati.mitiemposemanal.jchiroque`
- Minimum SDK: API 24
- Target/Compile SDK: API 36
- Versión: `0.2.0 (2)`
- Persistencia: SQLite mediante `SQLiteOpenHelper`

## Funcionalidades

### Sprint 1

- Cuatro categorías iniciales: Estudio, Trabajo, Descanso y Ocio.
- Creación, edición y eliminación confirmada de categorías.
- Nombres de categorías únicos, sin distinguir mayúsculas de minúsculas.
- Una categoría con tareas asociadas no puede eliminarse.
- Creación, edición y eliminación de tareas relacionadas con una categoría.
- Nombres de tareas obligatorios y únicos.

### Sprint 2

- Búsqueda en SQLite desde dos caracteres y actualización con cada cambio de texto.
- Selección de una tarea mostrando su categoría.
- Creación de una tarea inexistente, elección de categoría e inicio inmediato.
- Inicio y finalización con fecha y hora del teléfono.
- Un único evento en curso, incluso después de cerrar y volver a abrir la app.
- Historial con tarea, categoría, inicio, fin y duración.
- Migración de base de datos 1 → 2 sin eliminar categorías ni tareas.

## Prueba manual recomendada

1. Abre **Categorías** y confirma las cuatro categorías iniciales.
2. Crea una categoría; intenta crearla de nuevo y verifica el mensaje de duplicado.
3. Crea una tarea con categoría, edítala y comprueba el formato `Tarea · Categoría`.
4. En **Eventos**, escribe al menos dos letras y selecciona una sugerencia.
5. Inicia un evento, cierra la app por completo y vuelve a abrirla: debe continuar activo.
6. Finaliza el evento y verifica inicio, fin y duración en el historial.
7. Escribe una tarea inexistente, pulsa **Iniciar**, elige categoría y confirma que también aparece en **Tareas**.

## Compilación

```powershell
.\gradlew.bat assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.
