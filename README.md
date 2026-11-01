# Liamliz TV Hack

Aplicación Android nativa para administrar dispositivos Android/Android TV por ADB Wi-Fi. La IP y los puertos se introducen dentro de la APK; no se fija ningún dispositivo.

## Conexión

- **ADB TCP clásico:** introduce IP y puerto ADB (5555 solo si el equipo está configurado para ese modo).
- **Depuración inalámbrica moderna:** introduce el puerto temporal de vinculación y el código de seis dígitos que muestra el dispositivo; pulsa **Vincular dispositivo**. Después introduce el puerto ADB de conexión (es distinto del puerto de vinculación) y pulsa **Continuar**.
- Si aparece una solicitud de autorización ADB/RSA, acéptala en el dispositivo y vuelve a la app para pulsar **CONTINUAR**.

La app integra Kadb (Apache-2.0), cliente ADB Kotlin sin Termux ni binario adb externo. No se presupone conectividad ni un equipo específico.

Esta versión inicial solo realiza consultas de diagnóstico y lista paquetes. No desinstala aplicaciones ni modifica ajustes automáticamente. GitHub Actions compila un APK debug y lo publica como artefacto del workflow.
