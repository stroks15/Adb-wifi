# Liamliz TV Hack — APK Android independiente

La APK es una aplicación Android nativa. No requiere Termux, un servidor Node, un navegador ni otra aplicación auxiliar.

## Funciones incluidas

- Conexión ADB directa mediante IP/host y puerto configurables.
- Descubrimiento automático mDNS de servicios ADB `_adb._tcp` y `_adb-tls-connect._tcp` en la Wi-Fi local; los resultados se pueden seleccionar para conectar. No se confunden los puertos de pairing con los puertos de conexión.
- Estados explícitos: conectando, conectado y verificado, autorización pendiente o conexión fallida.
- Vinculación de Depuración inalámbrica mediante puerto temporal y código de seis dígitos (Android 11+ en teléfonos; Android TV requiere Android 13+).
- Botón para reintentar después de aceptar la autorización RSA en el dispositivo destino.
- Identidad ADB persistente (certificado y clave privada) guardada en preferencias privadas de la app; las copias de seguridad están desactivadas.
- Diagnóstico de fabricante, modelo, versión Android/SDK, memoria, almacenamiento, uptime, resolución y densidad.
- Listado de paquetes de terceros.
- Control remoto D-pad: arriba, abajo, izquierda, derecha, OK, atrás e inicio.
- Perfil reversible que cambia exclusivamente las tres escalas de animación a 0.5x. Conserva los valores previos, incluida la distinción entre valores definidos y no definidos, y permite restaurarlos en el mismo dispositivo.

## Conexión

1. Activa Depuración ADB o Depuración inalámbrica en el dispositivo destino.
2. Introduce su IP y puerto de conexión.
3. En teléfonos Android 11+ o Android TV 13+, usa primero el puerto de vinculación y el código de seis dígitos que aparecen en la pantalla de vinculación.
4. Después usa el puerto de conexión que aparece en la pantalla principal de Depuración inalámbrica. No son el mismo puerto.
5. Si Android muestra una confirmación RSA, acéptala en el destino y pulsa CONTINUAR en la APK.

Ambos equipos deben poder comunicarse en la misma red. La APK no puede aceptar por el usuario una confirmación de seguridad que aparece en el dispositivo destino. El puerto 5555 es solo un valor inicial para ADB TCP clásico; no se presupone que todos los dispositivos lo utilicen.

## Descubrimiento de red y USB OTG

El escáner usa DNS-SD/mDNS estándar para encontrar destinos que anuncian ADB. No es un barrido indiscriminado de todas las IP: depende de que el router permita multicast y de que el destino anuncie el servicio ADB. Si el router aísla clientes o bloquea multicast, usa IP/puerto manual. El descubrimiento no elude la autorización ADB.

USB OTG como host ADB todavía no está implementado en esta versión. La dependencia Kadb Android 1.3.0 usada para mantener la compilación con SDK 36 solo ofrece el transporte TCP; las versiones actuales de Kadb documentan USB Android, pero requieren migrar la dependencia y validar la compilación con un SDK compatible. La app no mostrará OTG como conectado ni simulará una sesión USB.

## Seguridad y límites

- La clave de identidad ADB se guarda en el almacenamiento interno privado de la aplicación; las copias de seguridad Android están desactivadas para no exportar esa identidad.
- No se incluye ejecución root, comandos arbitrarios, desinstalación/deshabilitación de paquetes, cambios de DNS, actualizaciones, Chromecast/Miracast ni ajustes del kernel.
- El perfil de animación es la única modificación del sistema incluida y puede restaurarse.
- El dispositivo destino debe tener ADB habilitado y autorizar esta instalación. La app no puede activar ADB de forma remota en un dispositivo que lo tenga deshabilitado.

## Compilación

GitHub Actions ejecuta clean testDebugUnitTest assembleDebug con JDK 17, Gradle 9.3.1, Android Gradle Plugin 9.1.1 y Android SDK Platform 36, Kotlin 2.4.21 y Kadb Android 1.3.0. La app tiene minSdk 23 y targetSdk 35. El artefacto Liamliz-TV-Hack-debug contiene una APK debug firmada automáticamente para pruebas e instalación manual.


## Dependencias y licencia

Kadb Android se publica bajo Apache-2.0. Su implementación de pairing incluye la dependencia transitiva com.github.Flyfish233:spake2-java:1.0.5, publicada en JitPack y marcada GPL-3.0. Antes de distribuir esta APK como producto cerrado o integrarla en un proyecto con licencia incompatible, revisa las obligaciones de esa dependencia o sustituye la implementación de pairing por una alternativa con licencia compatible. El repositorio de este proyecto no recibe una licencia nueva automáticamente.


La compilación 1.4.0 usa Kadb Android 1.3.0 porque las versiones 2.1.x declaran compileSdk mínimo 37 y el paquete Android 37 no estuvo disponible en el repositorio SDK del runner de CI durante esta compilación. Se mantienen compileSdk 36 y se fija BouncyCastle 1.86; HiddenApiBypass permanece en la versión transitiva 6.1 que incluye Kadb 1.3.0, porque 6.2 declara compileSdk 37.
