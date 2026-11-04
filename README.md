# MinimalLauncher

Launcher de Android: lista alfabética de apps, texto blanco sobre negro, sin iconos. Tap = abrir.

## Compilar

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Instalar

1. En el teléfono: Ajustes → Opciones de desarrollador → Depuración USB (activar).
2. Conecta por USB y ejecuta:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(O abre la carpeta en Android Studio y dale a Run.)

3. Pulsa el botón Inicio y elige **Minimal** → "Siempre".
   Si no pregunta: Ajustes → Apps → Apps predeterminadas → App de inicio → Minimal.

Para volver al launcher anterior, elige el otro en ese mismo ajuste.
