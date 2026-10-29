# Portada Fondo

> ⚠️ **ADVERTENCIA: solo para el Sony Walkman NW-A106.**
> Esta app está hecha y probada únicamente en ese modelo (Android 11, pantalla 720x1280). En otros dispositivos o pantallas el inicio minimalista puede verse mal, los botones pueden no responder y la app puede no funcionar. Instálala bajo tu propia responsabilidad. El inicio minimalista reemplaza tu pantalla de inicio; si algo falla, usa el comando de "Volver al launcher original" de la guía.

Convierte tu **Sony Walkman NW-A106** (Android 11, 720x1280) en un reproductor limpio: la portada de la canción que suena es el fondo de pantalla, y la pantalla de inicio solo muestra un reloj y una barra de música.

La app trae tres cosas:

1. **Fondo animado** con la portada de la canción en reproducción (8 estilos).
2. **Inicio minimalista**: reloj, fecha y una barra con portada, título, artista, progreso y botones anterior / pausa / siguiente. Tocar el reloj abre la lista de todas las apps.
3. **Widget "Ahora suena"** para launchers que admitan widgets.

Funciona con cualquier reproductor que publique una sesión de medios (el Reproductor de Sony, Spotify, etc.).

## Estilos de fondo

| Estilo | Qué hace |
|---|---|
| Portada completa | La portada llena la pantalla. **Recomendado con el inicio minimalista.** |
| Tarjeta flotante | Portada en una tarjeta sobre fondo desenfocado, con título y artista |
| Aurora | Luces de colores en movimiento tomadas de la portada |
| Vinilo | Disco que gira con la portada al centro |
| Bokeh | Burbujas de luz sobre la portada desenfocada |
| Reloj grande | Hora enorme sobre la portada desenfocada |
| Hora del día | Degradado que cambia entre amanecer, día, tarde y noche |
| Modo claro | Fondo luminoso con la portada en una tarjeta |

Los estilos que dibujan texto o formas (Tarjeta flotante, Vinilo, Reloj grande, etc.) se encima sobre el reloj y la barra del inicio minimalista. Con ese inicio usa "Portada completa".

---

# Guía paso a paso

## Lo que necesitas

- Un **Walkman NW-A106**. No está probado en ningún otro dispositivo.
- Una PC con Windows, Mac o Linux y un cable USB.
- Una cuenta de GitHub (gratis), solo para compilar la app.
- [Android platform-tools (ADB)](https://developer.android.com/tools/releases/platform-tools) descomprimido en una carpeta, por ejemplo `C:\platform-tools`.

## Paso 1 – Consigue el APK

No hace falta saber programar.

1. Entra a este repositorio y pulsa **Fork** (arriba a la derecha) para copiarlo a tu cuenta.
2. En tu copia abre la pestaña **Actions**. Si GitHub pide confirmar, pulsa *"I understand my workflows, go ahead and enable them"*.
3. Elige el flujo **Build APK** en la columna izquierda y pulsa **Run workflow** → **Run workflow**.
4. Espera 2–3 minutos hasta que aparezca una marca verde.
5. Abre esa ejecución, baja hasta **Artifacts** y descarga **portada-fondo-apk** (es un ZIP).
6. Descomprime el ZIP. Dentro hay un archivo `app-debug.apk`. Cópialo a la carpeta de platform-tools y renómbralo `portada.apk` para escribir menos.

> Cada compilación se firma con una clave distinta. Por eso, al instalar una versión nueva hay que **desinstalar la anterior** (paso 3).

## Paso 2 – Activa la depuración USB en el Walkman

1. **Ajustes → Acerca del dispositivo** y toca **Número de compilación** 7 veces.
2. Vuelve a **Ajustes → Sistema → Opciones de desarrollador** y activa **Depuración USB**.
3. Conecta el Walkman a la PC. Cuando salga el aviso "¿Permitir depuración USB?", marca *Permitir siempre* y acepta.
4. En la PC abre una terminal en la carpeta de platform-tools y comprueba:

```
adb devices
```

Debe aparecer tu dispositivo como `device`. Si dice `unauthorized`, revisa la pantalla del Walkman y acepta el aviso.

> **Importante:** mantén la pantalla del Walkman desbloqueada mientras sigues los pasos.

## Paso 3 – Instala la app

```
adb uninstall com.walkmanart
adb install -r portada.apk
```

(El primer comando puede decir `Failure`; es normal si no estaba instalada.)

## Paso 4 – Da acceso a notificaciones

Sin este permiso la app no puede leer la canción que suena.

Forma rápida:

```
adb shell cmd notification allow_listener com.walkmanart/com.walkmanart.MediaListenerService
```

Forma manual: abre **Portada Fondo** → **Dar acceso a notificaciones** → activa Portada Fondo.

## Paso 5 – Aplica el fondo animado

1. Abre **Portada Fondo** en el Walkman.
2. Elige un estilo (empieza por **Portada completa**).
3. Ajusta **Oscurecer fondo** si quieres.
4. Pulsa **Aplicar fondo animado** → **Establecer fondo de pantalla** → **Pantalla de inicio y de bloqueo**.

Reproduce una canción: la portada aparece de fondo.

## Paso 6 – (Opcional) Activa el inicio minimalista

Con ADB:

```
adb shell cmd package set-home-activity com.walkmanart/.HomeActivity
```

O desde el Walkman: **Portada Fondo → Usar inicio minimalista** y elige *Inicio minimalista*.

Cómo se usa:

- **Tocar el reloj o la fecha** abre la lista de todas las apps.
- **Tocar el título** de la barra abre el Reproductor de Sony.
- Los botones de la barra controlan la reproducción actual.

### Volver al launcher original de Sony

```
adb shell cmd package set-home-activity com.android.launcher3/.Launcher
```

O en el Walkman: **Ajustes → Apps → Apps predeterminadas → App de inicio** y elige *Launcher*.

## Paso 7 – (Opcional) Ahorra batería

Un cambio que se nota mucho: el tiempo de apagado de pantalla. Para ponerlo en 30 segundos:

```
adb shell settings put system screen_off_timeout 30000
```

En las pruebas, más de la mitad del consumo venía de tener la pantalla encendida, no de la app.

---

# Solución de problemas

| Problema | Qué hacer |
|---|---|
| `adb devices` no muestra nada | Prueba otro cable o puerto USB, desbloquea el Walkman y acepta el aviso de depuración |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Ejecuta `adb uninstall com.walkmanart` y vuelve a instalar |
| Sale la barra pero dice "Nada sonando" | Reproduce una canción y revisa que Portada Fondo tenga acceso a notificaciones (paso 4) |
| Fondo negro tras reinstalar | La reinstalación borra el fondo: repite el paso 5 |
| Los botones no responden | Asegúrate de que el reproductor esté abierto o en segundo plano; los botones envían teclas multimedia al reproductor activo |
| La pantalla de inicio queda en negro o rara | Ejecuta el comando de "Volver al launcher original" y vuelve a intentar |

# Para desarrolladores

- Kotlin, sin AndroidX y sin layouts XML (salvo el widget). `minSdk 26`, `targetSdk 30`.
- `ArtWallpaperService`: fondo animado (`WallpaperService`, dibujo con `Canvas`).
- `MediaHub`: estado compartido de reproducción (`MediaSessionManager` + `NotificationListenerService`).
- `HomeActivity`: inicio minimalista. `NowPlayingWidget`: widget.
- Se compila en GitHub Actions (`.github/workflows/build.yml`) con JDK 17 y Gradle 8.9.

Para compilar localmente: `gradle assembleDebug` y el APK queda en `app/build/outputs/apk/debug/`.
