# Minimal

Launcher de Android minimalista: un reloj, una barra de letras y nada más. Iconos en escala de grises, sin cajón de apps, sin widgets.

<p>
  <img src="docs/home.png" width="200" alt="Inicio: reloj, barra de letras y favoritos">
  <img src="docs/letter.png" width="200" alt="Apps filtradas por la letra g">
  <img src="docs/search.png" width="200" alt="Búsqueda">
  <img src="docs/menu.png" width="200" alt="Menú de una app">
</p>

## Cómo se usa

| Gesto | Qué hace |
|---|---|
| Tocar o deslizar por la **barra de letras** | Muestra solo las apps que empiezan por esa letra |
| **Deslizar hacia arriba** en el inicio | Buscar (la tecla → abre el primer resultado) |
| **Deslizar hacia abajo** en el inicio | Abre el panel de notificaciones |
| **Doble toque** en el inicio | Bloquea la pantalla |
| Tocar la **hora** / la **fecha** | Abre las alarmas / el calendario |
| **Mantener pulsada** una app | Menú: `favorito · info · ocultar · desinstalar` |
| Tocar la **★** de la barra | Muestra tus favoritos (quitarlos: mantener pulsada la app → `favorito`) |
| **Mantener pulsada** la fecha | Ajustes de acceso a notificaciones (para los puntos) |
| **Atrás** o **Inicio** | Vuelve a la vista limpia |

- Las **apps ocultas** no salen en la lista ni en la barra, pero sí en la búsqueda (en gris); desde su menú puedes volver a mostrarlas.
- Un **•** junto a una app indica que tiene notificaciones.
- **Personalizar** (mantener pulsada la hora → apariencia): estilo de reloj (fino, grueso, día, partido, analógico, apilado, progreso del día, binario), **tema** (un color de acento: neutro, tomado del fondo de pantalla, ámbar, salvia, mar, rosa o lila), fondo y tamaños. En *inicio* la barra de letras puede ir a la derecha, a la izquierda (zurdos) o abajo.

## Permisos opcionales

Dos funciones necesitan que las actives tú en el teléfono:

- **Bloquear con doble toque** → Ajustes → Accesibilidad → Aplicaciones instaladas → *Minimal: doble toque para bloquear*. Solo se usa para bloquear la pantalla; no lee nada.
- **Puntos de notificación** → Ajustes → Notificaciones → Acceso a notificaciones → *Minimal*. Solo mira qué apps tienen notificaciones, no su contenido.

En Samsung, si el ajuste aparece como restringido: Ajustes → Aplicaciones → Minimal → ⋮ → *Permitir ajustes restringidos*.

## Compilar e instalar

Necesitas Android Studio (trae el JDK y el SDK).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug        # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # compila e instala en el teléfono conectado
```

Conectar el teléfono por wifi (Opciones de desarrollador → Depuración inalámbrica):

```bash
adb pair IP:PUERTO_DE_VINCULACION     # una sola vez, pide el código
adb devices                           # debería aparecer el teléfono
```

Luego pulsa **Inicio** y elige **Minimal → Siempre**, o en Ajustes → Aplicaciones → Aplicaciones predeterminadas → Aplicación de inicio.

## Personalizar

Todo vive en [`MainActivity.kt`](app/src/main/java/com/jparedes/minimallauncher/MainActivity.kt). Los colores y la tipografía están arriba del archivo (`Bg`, `Fg`, `Dim`, `RowText`).

Kotlin + Jetpack Compose, una sola Activity, sin dependencias aparte de `activity-compose` y `compose-foundation`.

## Créditos

Tipografía [Jost](https://github.com/indestructible-type/Jost) — SIL Open Font License, ver [`FONT-LICENSE-Jost.txt`](FONT-LICENSE-Jost.txt).
