# Digital Travel Guide App

Aplicación Android que muestra marcadores de realidad aumentada sobre landmarks turísticos detectados por un modelo ONNX.

## Dependencias

- [ARCore](https://developers.google.com/ar) `com.google.ar:core:1.39.0`
- [ONNX Runtime para Android](https://onnxruntime.ai/) `com.microsoft.onnxruntime:onnxruntime-android:1.19.0`
- [OpenCV para Android](https://opencv.org/) `org.opencv:opencv-android:4.8.0`
- [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) `1.3.0`
- Android SDK 24 o superior (compilado con SDK 34)

## Estructura del proyecto

```
app/
  build.gradle
  src/main/
    java/com/example/travelguide/
      MainActivity.kt
      camera/CameraModule.kt
      inference/InferenceModule.kt
      ar/ARModule.kt
    assets/pipeline.onnx
```

## Compilar y apuntar `pipeline.onnx`

1. Entrenar o convertir tu modelo a formato ONNX y nombrarlo `pipeline.onnx`.
2. Copiar el archivo a `app/src/main/assets/` para que se empaquete con la aplicación.
3. En Android Studio ejecutar **Build > Make Project** o desde la línea de comandos:
   ```bash
   gradle assembleDebug
   ```
   El modelo se cargará desde los assets mediante `InferenceModule`.

## Ejecutar en dispositivo o emulador ARCore

### Dispositivo físico
1. Conectar un dispositivo compatible con ARCore y activar el modo desarrollador.
2. Ejecutar la app desde Android Studio (**Run > Run 'app'**) o con:
   ```bash
   gradle installDebug
   ```
3. Al iniciar se abrirá la cámara; los marcadores se actualizarán al detectar landmarks.

### Emulador con ARCore
1. En Android Studio abrir **Device Manager** y crear un *Virtual Device* que utilice una imagen con **Google Play** y soporte ARCore (API 30+).
2. Iniciar el emulador, asegurarse de que `ARCore` esté actualizado desde la Play Store.
3. Ejecutar la app como en un dispositivo físico.

## Notas
- El rendimiento depende de las capacidades del dispositivo y del modelo ONNX utilizado.
- Para reemplazar el modelo basta con actualizar `pipeline.onnx` y recompilar.
