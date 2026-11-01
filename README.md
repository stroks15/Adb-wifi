# Liamliz TV Hack

Proyecto con panel web local y APK Android nativa para diagnóstico y control ADB Wi-Fi.

## APK Android independiente

La app Android está en android/ y no depende del panel web, Node.js ni Termux. Incluye conexión ADB por IP/puerto, pairing de Wireless Debugging, autorización/reintento, identidad ADB persistente, diagnóstico, listado de paquetes, D-pad y un perfil reversible de animaciones. Consulta android/README.md.

## Compilación

GitHub Actions genera una APK debug firmada en cada cambio de android/** o del workflow. Descarga el artefacto Liamliz-TV-Hack-debug desde la ejecución exitosa en Actions.

La compilación de la rama de desarrollo usa JDK 17, Gradle 9.3.1, Android Gradle Plugin 9.1.1, Kotlin 2.4.21 y Kadb Android 1.3.0. AndroidX está habilitado explícitamente.

## Panel web

El panel web sigue siendo una interfaz local separada que requiere Node.js y ADB instalado en el equipo que ejecuta el servidor. La APK nativa no necesita ni utiliza ese servidor.
