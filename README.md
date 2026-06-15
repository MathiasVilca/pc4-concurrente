# Dog Messenger

Proyecto de Programacion Concurrente y Distribuida: chat movil y desktop con sockets TCP, nodos separados, hilos, grupos, clonacion de historial y modulo de ventas.

## Requisitos

- Java JDK para compilar y ejecutar el backend.
- Android Studio para abrir/compilar el cliente Android.
- Telefono Android o emulador conectado a la misma red que el servidor.
- Red LAN o WIFI donde todos los equipos puedan alcanzar la IP del servidor.

Para Android, si Gradle falla con Java 25, usar el JDK incluido en Android Studio:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
```

## Puertos del cluster

- `8189`: nodo de mensajes, grupos, historial y clonacion.
- `8190`: nodo de archivos.
- `8191`: nodo de imagenes.
- `8192`: nodo de ventas, comprobantes y metricas.

## 1. Obtener la IP del servidor

En la maquina donde se ejecutara el backend:

```powershell
ipconfig
```

Buscar la IPv4 de la red activa, por ejemplo:

```text
192.168.1.25
```

Esa IP se coloca en el cliente Android y en el cliente desktop.

## 2. Compilar el backend

Desde la raiz del proyecto:

```powershell
javac -encoding UTF-8 -d backend-cluster\out backend-cluster\src\utils\*.java backend-cluster\src\*.java
```

## 3. Levantar los nodos del backend

Abrir cuatro terminales diferentes desde la raiz del proyecto.

Terminal 1, mensajes:

```powershell
java -cp backend-cluster\out MainMensajes
```

Terminal 2, archivos:

```powershell
java -cp backend-cluster\out MainArchivos
```

Terminal 3, imagenes:

```powershell
java -cp backend-cluster\out MainImagenes
```

Terminal 4, ventas:

```powershell
java -cp backend-cluster\out MainVentas
```

El nodo principal para el chat es `MainMensajes` en el puerto `8189`.

## 4. Ejecutar el cliente desktop

Compilar:

```powershell
javac -encoding UTF-8 -d cliente-desktop\out cliente-desktop\src\DogMessengerDesktop.java
```

Ejecutar:

```powershell
java -cp cliente-desktop\out DogMessengerDesktop
```

En la ventana desktop:

1. Escribir la IP del servidor.
2. Mantener puerto `8189`.
3. Usar un `idClientChat`, por ejemplo `0812`.
4. Presionar `Conectar`.
5. Usar `Imagen` para enviar una imagen al nodo `8191`.
6. Usar `Archivo` para enviar un archivo al nodo `8190`.

## 5. Ejecutar el cliente Android

Opcion desde Android Studio:

1. Abrir la carpeta `cliente-android`.
2. Esperar sincronizacion de Gradle.
3. Ejecutar la app en telefono o emulador.

Opcion por terminal:

```powershell
cd cliente-android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK generado:

```text
cliente-android\app\build\outputs\apk\debug\app-debug.apk
```

En la app Android:

1. Escribir la IP del servidor.
2. Usar puerto `8189`.
3. Escribir el mismo `idClientChat`, por ejemplo `0812`.
4. Presionar `Conectar`.

Al conectarse, Android abre conexiones al cluster:

- `8189`: mensajes.
- `8190`: archivos.
- `8191`: imagenes.
- `8192`: ventas.

## 6. Probar chat y grupos

Enviar mensajes desde Android y desktop. Los mensajes normales se cifran con AES antes de viajar por socket.

Para cambiar de grupo:

```text
/join ventas
```

Usar el mismo comando en los clientes que deben conversar dentro de ese grupo.

## 7. Probar clonacion de historial

Flujo recomendado para la exposicion:

1. Conectar Android con `idClientChat 0812`.
2. Enviar varios mensajes.
3. En desktop, usar el mismo servidor y presionar `Conectar`.
4. En Android presionar `Emitir QR de clonacion`.
5. Copiar el token generado, por ejemplo:

```text
DOGQR://CLONE/0812
```

6. Pegar el token en desktop y presionar `Clonar`.
7. El desktop recibira:

```text
HISTORY_BEGIN
HISTORY_ITEM
HISTORY_END
```

Cada item del historial se descifra con el mismo motor AES del chat normal.

Tambien se puede usar `Escanear QR` en Android. Ese boton intenta abrir una app lectora QR instalada en el telefono. Si no existe lector QR, se pega el token manualmente.

## 8. Probar ventas

Con el nodo `MainVentas` encendido en el puerto `8192`, desde Android:

1. Presionar `Comprar Premium`.
2. Ver el comprobante generado por el bot de ventas.
3. Presionar `Metricas` para ver pedidos procesados e inventario.

El comprobante tambien se sincroniza con el chat principal como mensaje del sistema.

## 9. Probar imagenes

Desde Android:

1. Conectarse al cluster.
2. Presionar `Foto`.
3. Seleccionar una imagen.

El nodo `MainImagenes` recibe los bytes por el puerto `8191` y muestra actividad en su terminal.

Desde Desktop:

1. Presionar `Imagen`.
2. Elegir un archivo de imagen.
3. Verificar la terminal de `MainImagenes`.

## 10. Probar archivos

Desde Android:

1. Conectarse al cluster.
2. Presionar `Archivo`.
3. Seleccionar cualquier archivo.

El nodo `MainArchivos` recibe los bytes por el puerto `8190` y muestra actividad en su terminal.

Desde Desktop:

1. Presionar `Archivo`.
2. Elegir un archivo.
3. Verificar la terminal de `MainArchivos`.

## 11. Limpieza antes de entregar

No subir archivos temporales o binarios. Antes de comprimir, revisar:

```powershell
git status --short
```

Evitar incluir:

- Carpetas `build`.
- Carpetas `out`.
- Carpeta `.gradle`.
- Archivos `.class`.
- APK si el docente pidio solo codigo fuente.

El comprimido final debe contener:

- Codigo fuente.
- PDF del informe.
- PDF de presentacion.
- Diagramas de arquitectura y protocolo.

## Diagrama textual de arquitectura

```text
Android App       Desktop Java
    |                 |
    | TCP 8189        | TCP 8189
    v                 v
 Nodo Mensajes / Historial / Grupos
    |
    +-- TCP 8190 Nodo Archivos
    +-- TCP 8191 Nodo Imagenes
    +-- TCP 8192 Nodo Ventas
```

## Protocolo usado

```text
1 byte  -> tipo
4 bytes -> longitud
N bytes -> payload
```

Tipos:

- `1`: texto.
- `2`: imagen.
- `3`: archivo.

Comandos internos:

- `IDENT:<idClientChat>`
- `CLONE:<idClientChat>`
- `JOIN:<grupo>`
