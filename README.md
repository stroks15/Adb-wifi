# Liamliz TV Hack — ADB Wi-Fi + Launcher

Proyecto para diagnosticar y controlar un Android TV por ADB Wi-Fi desde un equipo que esté en la misma red. Incluye panel web local y una base nativa Android para el launcher.

## Importante: límites de seguridad

- El servidor ADB debe ejecutarse **en el equipo que tenga ADB instalado y conectividad LAN con la TV**. Un despliegue en Vercel/GitHub no puede alcanzar la red privada de tu casa.
- El servidor escucha únicamente en `127.0.0.1`; no lo publiques en Internet ni lo expongas a la LAN sin autenticación.
- La optimización automática solo aplica ajustes reversibles de animación y guarda los valores anteriores. No desinstala paquetes, no escribe en parámetros del kernel, no cambia DNS ni elimina datos/cachés de forma automática.
- Las acciones de limpieza y desinstalación pueden afectar apps o datos. Se mantienen fuera del flujo automático; cualquier acción avanzada debe revisarse y confirmarse individualmente.
- ADB por TCP/IP puede requerir autorización en la TV. Acepta la huella RSA solo en tu dispositivo y red de confianza.

## Requisitos

- Node.js 18 o superior.
- Android Platform Tools (`adb`) instalado y disponible en PATH.
- TV y equipo de control en la misma red Wi-Fi/Ethernet.
- Depuración USB activada y, según Android, depuración inalámbrica o ADB TCP/IP habilitado.

## Ejecutar el panel

```bash
npm install
npm start
```

Abre http://127.0.0.1:3000.

1. En la TV activa las opciones de desarrollador y la depuración ADB.
2. Introduce la IP local que aparece en Ajustes > Red.
3. Pulsa **Conectar** y acepta la autorización en la TV.
4. Ejecuta primero **Diagnóstico**.
5. Si los resultados son correctos, usa **Aplicar perfil reversible** para cambiar las escalas de animación a 0.5x. El panel permite restaurar los valores previos.

Puerto ADB por defecto: 5555. Si tu TV muestra otro puerto, cambia el campo del panel.

## Arquitectura

- `server.js`: API local, validación estricta de IP/puerto, ejecución de ADB sin shell interpolado y registro de resultados.
- `public/index.html`: panel adaptable, consola y D-pad.
- `android/`: módulo Android inicial para el launcher HOME.

## Limitaciones del dispositivo

Los comandos disponibles dependen de la versión de Android, fabricante, permisos y kernel. Un resultado correcto de ADB no garantiza que cada ajuste sea soportado. En muchos TV Box sin root no es posible modificar Low Memory Killer. No se presupone root.

## Seguridad de red

No abras el puerto 3000 en el router. No uses este servidor como backend público. Si necesitas acceso desde otro equipo, crea primero una VPN privada y añade autenticación fuerte y TLS.
