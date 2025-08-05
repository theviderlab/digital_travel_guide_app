# Especificaciones Técnicas para Aplicación Android con Realidad Aumentada y Detección de Landmarks

## Introducción

La aplicación permitirá a los usuarios visualizar marcadores de realidad aumentada (RA) que señalen puntos de interés turísticos (landmarks). Los marcadores estarán posicionados basándose en los resultados obtenidos al ejecutar el modelo `pipeline.onnx` usando ONNX Runtime, que proporciona bounding boxes con clasificación de landmarks.

## Requisitos del Sistema

* Android Studio (última versión estable)
* ARCore SDK
* ONNX Runtime para Android
* OpenCV para manipulación de imágenes (opcional)

## Arquitectura del Proyecto

### Estructura Modular

El proyecto deberá seguir una estructura modular y clara:

* **Modelo**:

  * Gestión de inferencia ONNX (pipeline.onnx).
  * Manejo de resultados (bounding boxes y clases).

* **Vista (View)**:

  * Renderizado de cámara en tiempo real.
  * Superposición de marcadores RA con leyendas.

* **Controlador (Controller)**:

  * Gestión de la cámara.
  * Captura de frames.
  * Comunicación con el modelo ONNX.

## Componentes

### 1. Cámara y Captura de Imagen

* Usar la cámara trasera del dispositivo.
* Implementar captura continua de frames para análisis en tiempo real.
* Frecuencia recomendada: 1 frame por segundo para equilibrio entre rendimiento y precisión.

### 2. Inferencia del Modelo ONNX

* Usar ONNX Runtime para ejecutar `pipeline.onnx`.
* Preprocesar cada frame capturado antes de la inferencia.
* Resultado esperado: tensor con bounding boxes `[x1, y1, x2, y2]`, clases y scores de confianza.

### 3. Postprocesamiento

Este paso no es necesario en la aplicación, ya que el modelo `pipeline.onnx` devuelve directamente las bounding boxes filtradas y escaladas al tamaño original.

### 4. Realidad Aumentada con ARCore

* Inicializar la sesión ARCore.
* Posicionar automáticamente marcadores 3D según las bounding boxes.
* Mantener fija la posición del marcador siempre que la cámara no experimente movimientos bruscos.
* Ante movimientos bruscos (giros rápidos, sacudidas), recalcular posición de marcadores con una nueva inferencia del modelo.

### 5. Posicionamiento de Marcadores

* Usar la posición central del bounding box para proyectar marcadores en RA.
* Intentar estimar distancia relativa al landmark detectado:

  * Estimar la distancia mediante técnicas simples, como tamaño relativo del bounding box respecto a la resolución del frame.

### 6. Leyendas para Marcadores

* Asociar leyendas claramente visibles al marcador, mostrando la clase del landmark detectado.
* Las leyendas deben tener un tamaño ajustado para una lectura clara en cualquier tamaño de pantalla.

## Flujo de Trabajo

1. Iniciar la aplicación y activar cámara y sesión ARCore.
2. Capturar frame de cámara.
3. Ejecutar preprocesamiento y modelo ONNX en el frame.
4. Recibir tensor con bounding boxes y clases.
5. Realizar postprocesamiento (filtrado y escalado).
6. Posicionar marcador en RA según el bounding box.
7. Mostrar leyenda con la clase.
8. Actualizar marcadores únicamente ante movimientos bruscos.

## Modularidad

Cada componente (Cámara, Inferencia, Postprocesamiento, ARCore) deberá implementarse en módulos independientes con interfaces claras:

* **CámaraModule**: Manejo exclusivo de la cámara y captura de imágenes.
* **InferenceModule**: Gestión de inferencia y pre/post procesamiento.
* **ARModule**: Gestión exclusiva de ARCore, posicionamiento y visualización de marcadores.

## Documentación y Buenas Prácticas

* El código deberá estar claramente comentado y documentado.
* Cada módulo debe incluir documentación sobre cómo usar y modificar funcionalidades específicas.
* Aplicar buenas prácticas de desarrollo Android: separación de responsabilidades, uso eficiente de recursos, manejo de excepciones y errores.

## Consideraciones Adicionales

* Garantizar rendimiento aceptable en dispositivos Android de gama media o superior.
* Realizar pruebas exhaustivas con diferentes condiciones de iluminación y escenarios reales.

## Entregables

* Código fuente organizado y bien documentado.
* Archivo APK funcional para pruebas iniciales.
* Documentación clara sobre instalación, uso y mantenimiento del proyecto.
