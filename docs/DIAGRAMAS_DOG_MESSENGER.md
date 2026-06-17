# Diagramas Dog Messenger

Este documento contiene diagramas listos para copiar a Mermaid Live Editor, Markdown con soporte Mermaid, Obsidian, GitHub o herramientas similares. Se pueden exportar como imagen e incluir en el informe y la presentacion.

## 1. Arquitectura General

```mermaid
flowchart LR
    A[Cliente Android<br/>Kotlin] -->|TCP 8189<br/>texto cifrado| M[Nodo Mensajes<br/>MainMensajes]
    D[Cliente Desktop<br/>Java Swing] -->|TCP 8189<br/>texto cifrado| M

    A -->|TCP 8191<br/>imagen bytes| I[Nodo Imagenes<br/>MainImagenes]
    D -->|TCP 8191<br/>imagen bytes| I

    A -->|TCP 8190<br/>archivo bytes| F[Nodo Archivos<br/>MainArchivos]
    D -->|TCP 8190<br/>archivo bytes| F

    A -->|TCP 8192<br/>COMPRAR / REPORTE| V[Nodo Ventas<br/>MainVentas]

    I -->|GET_IMAGE<br/>bytes imagen| A
    I -->|GET_IMAGE<br/>bytes imagen| D

    M --> H[(Historial en RAM<br/>por idClientChat)]
    V -->|comprobante / metricas| A
    V -->|sincroniza evento| M
```

## 2. Cluster de Nodos

```mermaid
flowchart TB
    subgraph Backend Java
        M[8189<br/>Mensajes, grupos,<br/>historial y clonacion]
        F[8190<br/>Archivos]
        I[8191<br/>Imagenes y descarga<br/>GET_IMAGE]
        V[8192<br/>Ventas, comprobantes<br/>y metricas]
    end

    C1[Android] --> M
    C2[Desktop] --> M
    C1 --> F
    C2 --> F
    C1 --> I
    C2 --> I
    C1 --> V
```

## 3. Protocolo TCP

```mermaid
flowchart LR
    P[Paquete TCP] --> T[1 byte<br/>Tipo]
    P --> L[4 bytes<br/>Longitud]
    P --> B[N bytes<br/>Payload]

    T --> T1[1 = Texto UTF-8]
    T --> T2[2 = Imagen]
    T --> T3[3 = Archivo]

    B --> C1[Comando interno<br/>IDENT, JOIN, CLONE, GET_IMAGE]
    B --> C2[Mensaje cifrado AES]
    B --> C3[Bytes de imagen o archivo]
```

Ejemplo de trama:

```text
tipo      = 1
longitud  = 24
payload   = IDENT:0812
```



## 4. Hilos y Concurrencia

```mermaid
flowchart TB
    S[ServerSocket.accept] --> C1[Socket Cliente 1]
    S --> C2[Socket Cliente 2]
    S --> C3[Socket Cliente 3]

    C1 --> H1[Thread Cliente 1]
    C2 --> H2[Thread Cliente 2]
    C3 --> H3[Thread Cliente 3]

    H1 --> R[(Recursos compartidos<br/>CopyOnWriteArrayList<br/>ConcurrentHashMap)]
    H2 --> R
    H3 --> R
```

