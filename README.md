# TV Launcher AI

Proyecto inicial de launcher para TV Box genérica con Android 9 y arquitectura ARM de 32 bits (`armeabi-v7a`).

## Compilación automática
Cada push a `main`/`master`, o una ejecución manual de GitHub Actions, intenta compilar la APK. Descarga la APK desde el artefacto `TV-Launcher-AI-debug` de la ejecución.

## Instalar y probar
1. Instalar `app-debug.apk` en la TV Box.
2. Pulsar Home y elegir **TV Launcher AI** si Android muestra el selector.
3. Si el firmware no permite establecer un launcher predeterminado, eso requiere una prueba específica en el dispositivo.

Esta versión inicial muestra las aplicaciones instaladas que Android publica como lanzables. No promete saltarse restricciones del firmware ni forzar la selección predeterminada.
