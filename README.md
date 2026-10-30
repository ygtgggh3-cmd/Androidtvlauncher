# Launcher TV Lite

Launcher estilo Android TV para TV boxes viejos (~512 MB de RAM, Android 4.4+).

- Sin dependencias (sin AndroidX/Compose/Leanback), sin layouts XML
- Cuadrícula con cuadros que se agrandan al enfocar, reloj, accesos a Wi-Fi y Ajustes
- Manejo con control remoto: OK abre la app; mantener OK o tecla Menú muestra Abrir / Info / Desinstalar
- Se registra como pantalla de inicio (HOME)

## Obtener el APK

1. Sube este proyecto a un repositorio de GitHub (incluida la carpeta `.github`).
2. Pestaña **Actions** → **Compilar APK** → **Run workflow**.
3. Al terminar, descarga `LauncherTVLite-apk` desde la sección *Artifacts*.

Con Android Studio también sirve: abrir la carpeta y ejecutar `Build > Build APK(s)`.
