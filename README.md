# 🐄 Sistema de Rastreo Inteligente para Ganado con Geocercas y Notificaciones

Aplicación Android diseñada para rancheros y propietarios de ganado, enfocada en la localización en tiempo real de animales como vacas, caballos, ovejas, cabras y cerdos. Este sistema aborda la problemática local de animales que escapan de sus áreas de pastoreo y provocan accidentes viales o se extravían dentro de zonas urbanas.

---

## 📲 Funcionalidades principales

- 📡 **Recepción de datos desde rastreadores LoRa**
  - Comunicación vía BLE con un ESP32 recolector que recibe datos de múltiples dispositivos ESP32 rastreadores por LoRa
  - Cada rastreador transmite su ID y ubicación GPS (latitud y longitud)

- 🗺️ **Visualización de animales en el mapa**
  - Muestra en Google Maps todos los rastreadores con sus ubicaciones actuales
  - Indicador visual de estado:
    - ✅ Dentro del área de pastoreo (con checkmark)
    - ❌ Fuera del área (notificación inmediata)
    - ⚠️ Inactivo por falta de actualización (> 2 minutos)
      - Icono rojo si está fuera del área
      - Icono gris si está dentro

- 📐 **Definición de áreas de pastoreo**
  - Creación de múltiples geocercas mediante polígonos editables en el mapa
  - Detección automática de violaciones al perímetro establecido
  - Interfaz de edición visual con marcadores dinámicos

- 📍 **Interacción con la ubicación del usuario**
  - Centrado automático entre los rastreadores y el usuario
  - Botón para abrir la mejor ruta en Google Maps hacia un rastreador seleccionado

- 📝 **Gestión de rastreadores**
  - Edición del nombre y descripción de cada rastreador (ej. “Vaca 14 - Rancho El Girasol”)
  - Guardado de datos en base local con:
    - ID
    - Nombre
    - Descripción
    - Última ubicación conocida
    - Fecha y hora de la última actualización

---

## 🛠️ Tecnologías utilizadas

- **Lenguaje:** Kotlin  
- **UI tradicional:** XML + ConstraintLayout + Fragments  
- **Base de datos local:** Room  
- **Conectividad inalámbrica:** BLE (Bluetooth Low Energy)  
- **Mapas y geolocalización:** Google Maps SDK · Maps Utils · Fused Location Provider  
- **Arquitectura:** ViewModel + LiveData  
- **DI:** Hilt  
- **Animaciones:** Lottie  
- **Mensajería interna:** LocalBroadcastManager

---

## 🔧 Estructura del sistema

```mermaid
graph TD
    A[ESP32 Rastreadores<br/>(LoRa)] -->|ID + Ubicación| B[ESP32 Recolector<br/>(BLE)]
    B -->|Datos vía BLE| C[Aplicación Android]
    C --> D[Base de datos local (Room)]
    C --> E[Mapa con Geocercas]
    C --> F[Notificaciones al usuario]
    C --> G[Ruta al rastreador (Google Maps)]
