# Dog Messenger - Arquitectura y Protocolo

## Nodos del cluster

El backend se divide en cuatro procesos Java independientes:

- `MainMensajes` en el puerto `8189`: chat principal, grupos, cifrado de extremo a extremo en cliente y clonacion de historial.
- `MainArchivos` en el puerto `8190`: recepcion de archivos binarios.
- `MainImagenes` en el puerto `8191`: recepcion de imagenes binarias.
- `MainVentas` en el puerto `8192`: compras, comprobantes, seguimiento basico y metricas.

Cada conexion entrante se atiende con un hilo nativo (`TCPServerThread50`). Las colecciones compartidas del nodo de mensajes usan estructuras concurrentes para evitar corrupcion al recibir varios clientes al mismo tiempo.

## Protocolo TCP

Cada paquete viaja con una cabecera binaria propia:

```text
1 byte  -> tipo
4 bytes -> longitud del payload
N bytes -> payload
```

Tipos soportados:

- `1`: texto UTF-8.
- `2`: imagen.
- `3`: archivo.

Ejemplo de paquete de texto:

```text
tipo = 1
longitud = 28
payload = "Cliente 1: <mensaje cifrado>"
```

## Comandos internos

Los comandos internos viajan como texto plano porque son instrucciones para el servidor:

- `IDENT:<idClientChat>`: valida la sesion actual con un identificador de chat.
- `CLONE:<idClientChat>`: solicita al servidor el historial guardado para ese identificador.
- `JOIN:<grupo>`: cambia al cliente al grupo indicado.

Los mensajes normales se cifran en el cliente con AES antes de enviarse. El servidor solo guarda y retransmite el bloque cifrado.

## Clonacion de chats

El cliente movil y el cliente desktop emiten un token de clonacion:

```text
DOGQR://CLONE/0812
```

Ese token representa el contenido que se muestra como QR en la demo. Al pegarlo o leerlo desde otro dispositivo, el cliente extrae el `idClientChat`, abre su tunel TCP, envia `CLONE:<idClientChat>` y recibe:

```text
HISTORY_BEGIN:<idClientChat>:<cantidad>
HISTORY_ITEM:<mensaje anterior>
HISTORY_ITEM:<mensaje anterior>
HISTORY_END:<idClientChat>
```

Cada `HISTORY_ITEM` se procesa con el mismo descifrador AES del chat normal.

## Flujo de despliegue

1. Levantar los nodos Java del backend en la maquina servidor.
2. Conectar Android y Desktop a la IP LAN/WIFI del servidor.
3. En ambos clientes usar el mismo `idClientChat` para compartir sesion.
4. Enviar mensajes cifrados y usar `/join nombreGrupo` para demostrar grupos.
5. Emitir el token QR en un cliente y clonar desde el otro.
6. Probar `Comprar Premium` y `Metricas` desde Android contra el nodo `8192`.
