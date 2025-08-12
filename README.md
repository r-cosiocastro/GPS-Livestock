# Sistema de Rastreo Inteligente para Ganado con Geocercas y Notificaciones

Aplicación Android diseñada para rancheros y propietarios de ganado, enfocada en la localización en tiempo real de animales como vacas, caballos, ovejas, cabras y cerdos. Este sistema aborda la problemática local de animales que escapan de sus áreas de pastoreo y provocan accidentes viales o se extravían dentro de zonas urbanas.

---

## Funcionalidades Principales

### Recepción de Datos desde Rastreadores LoRa
- Comunicación vía BLE con un ESP32 recolector que recibe datos de múltiples dispositivos ESP32 rastreadores por LoRa
- Cada rastreador transmite su ID y ubicación GPS (latitud y longitud)

### Visualización de Animales en el Mapa
- Muestra en Google Maps todos los rastreadores con sus ubicaciones actuales
- Indicador visual de estado:
  - **Verde**: Dentro del área de pastoreo (con checkmark)
  - **Rojo**: Fuera del área (notificación inmediata)
  - **Amarillo**: Inactivo por falta de actualización (> 2 minutos)
    - Icono rojo si está fuera del área
    - Icono gris si está dentro del área

### Definición de Áreas de Pastoreo
- Creación de múltiples geocercas mediante polígonos editables en el mapa
- Detección automática de violaciones al perímetro establecido
- Interfaz de edición visual con marcadores dinámicos

### Interacción con la Ubicación del Usuario
- Centrado automático entre los rastreadores y el usuario
- Botón para abrir la mejor ruta en Google Maps hacia un rastreador seleccionado

### Gestión de Rastreadores
- Edición del nombre y descripción de cada rastreador (ej. "Vaca 14 - Rancho El Girasol")
- Guardado de datos en base local con:
  - ID único del dispositivo
  - Nombre personalizado
  - Descripción detallada
  - Última ubicación conocida
  - Fecha y hora de la última actualización

---

## Tecnologías Utilizadas

### Desarrollo
- **Lenguaje:** Kotlin
- **Interfaz de Usuario:** XML + ConstraintLayout + Fragments
- **Base de Datos Local:** Room Database
- **Conectividad Inalámbrica:** BLE (Bluetooth Low Energy)

### Mapas y Geolocalización
- **Google Maps SDK:** Para la visualización de mapas
- **Maps Utils:** Utilidades adicionales para mapas
- **Fused Location Provider:** Para obtener la ubicación del usuario

### Arquitectura y Patrones
- **Arquitectura:** ViewModel + LiveData
- **Inyección de Dependencias:** Hilt
- **Animaciones:** Lottie
- **Mensajería Interna:** LocalBroadcastManager

---

## Instalación y Configuración

### Prerrequisitos
- Android Studio
- SDK de Android (API nivel mínimo: 21)
- Clave de API de Google Maps
- Dispositivos ESP32 con módulos LoRa y GPS

### Configuración
1. Clona el repositorio
2. Configura la clave de API de Google Maps en el archivo `local.properties`
3. Compila e instala la aplicación en tu dispositivo Android
4. Configura los dispositivos ESP32 rastreadores según la documentación del hardware

---

## Uso

1. **Conexión Bluetooth**: Conecta la aplicación al ESP32 recolector vía BLE
2. **Visualización**: Observa la ubicación de todos los rastreadores en el mapa
3. **Configuración de Geocercas**: Define las áreas de pastoreo dibujando polígonos en el mapa
4. **Gestión de Animales**: Asigna nombres y descripciones a cada rastreador
5. **Monitoreo**: Recibe notificaciones cuando un animal salga del área permitida

---

## Contribuciones

Las contribuciones son bienvenidas. Por favor, abre un issue para discutir los cambios que te gustaría realizar antes de crear un pull request.

## Licencia

Este proyecto está bajo la Licencia MIT. Consulta el archivo LICENSE para más detalles.
