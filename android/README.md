# Liamliz TV Hack — APK Android independiente

La APK es una aplicación Android nativa. No requiere Termux, un servidor Node, un navegador ni otra aplicación auxiliar.

## Funciones incluidas

- Conexión ADB directa mediante IP/host y puerto configurables.
- Vinculación de Depuración inalámbrica Android 11+ mediante puerto temporal y código de seis dígitos.
- Botón para reintentar después de aceptar la autorización RSA en el dispositivo destino.
- Identidad ADB persistente en almacenamiento privado de la app para evitar que cada inicio genere una huella distinta.
- Diagnóstico de fabricante, modelo, versión Android/SDK, memoria, almacenamiento, uptime, resolución y densidad.
- Listado de paquetes de terceros.
- Control remoto D-pad: arriba, abajo, izquierda, derecha, OK, atrás e inicio.
- Perfil reversible que cambia exclusivamente las tres escalas de animación a 0.5x. Conserva los valores previos, incluida la distinción entre valores definidos y no definidos, y permite restaurarlos en el mismo dispositivo.

## Conexión

1. Activa Depuración ADB o Depuración inalámbrica en el dispositivo destino.
2. Introduce su IP y puerto de conexión.
3. En Android 11+, usa primero el puerto de vinculación y el código de seis dígitos que aparecen en la pantalla de vinculación.
4. Después usa el puerto de conexión que aparece en la pantalla principal de Depuración inalámbrica. No son el mismo puerto.
5. Si Android muestra una confirmación RSA, acéptala en el destino y pulsa CONTINUAR en la APK.

Ambos equipos deben poder comunicarse en la misma red. La APK no puede aceptar por el usuario una confirmación de seguridad que aparece en el dispositivo destino. El puerto 5555 es solo un valor inicial para ADB TCP clásico; no se presupone que todos los dispositivos lo utilicen.

## Seguridad y límites

- La clave de identidad ADB se guarda en el almacenamiento interno privado de la aplicación; las copias de seguridad Android están desactivadas para no exportar esa identidad.
- No se incluye ejecución root, comandos arbitrarios, desinstalación/deshabilitación de paquetes, cambios de DNS, actualizaciones, Chromecast/Miracast ni ajustes del kernel.
- El perfil de animación es la única modificación del sistema incluida y puede restaurarse.
- El dispositivo destino debe tener ADB habilitado y autorizar esta instalación. La app no puede activar ADB de forma remota en un dispositivo que lo tenga deshabilitado.

## Compilación

GitHub Actions ejecuta clean testDebugUnitTest assembleDebug con JDK 17, Gradle 9.3.1, Android Gradle Plugin 9.1.1 y Android SDK Platform 37 y Kotlin 2.4.21. El artefacto Liamliz-TV-Hack-debug contiene una APK debug firmada automáticamente para pruebas e instalación manual.


## Dependencias y licencia

Kadb Android se publica bajo Apache-2.0. Su implementación de pairing incluye la dependencia transitiva com.github.Flyfish233:spake2-java:1.1.1, publicada en JitPack y marcada GPL-3.0. Antes de distribuir esta APK como producto cerrado o integrarla en un proyecto con licencia incompatible, revisa las obligaciones de esa dependencia o sustituye la implementación de pairing por una alternativa con licencia compatible. El repositorio de este proyecto no recibe una licencia nueva automáticamente.
