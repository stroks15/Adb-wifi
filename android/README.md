# Módulo Android — Liamliz TV Hack

Este módulo es el punto de partida del launcher nativo. El panel ADB y el APK son componentes separados: el APK no puede ejecutar ADB en el host local ni asumir permisos root.

## Configuración

Crea/abre este directorio como proyecto Android en Android Studio. El manifiesto declara la actividad como HOME y LEANBACK_LAUNCHER para que Android TV pueda ofrecerla como pantalla de inicio. La selección como launcher predeterminado depende del firmware.

El listado de aplicaciones se obtiene dinámicamente del PackageManager; no se asume que los paquetes de Xuper, SmartTube u otros coincidan en todos los equipos. Para Android 11+, revisa las reglas de visibilidad de paquetes antes de ampliar el catálogo.

No se incluyen URLs de canales de TV inventadas. Añade fuentes autorizadas y verificadas desde una pantalla de configuración.
