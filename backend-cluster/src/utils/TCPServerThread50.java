package utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class TCPServerThread50 implements Runnable {
    private Socket client;
    private TCPServer50 tcpserver;
    private int clientID;
    private volatile boolean running = false;
    private DataOutputStream mOut;
    private DataInputStream mIn;
    public String grupoActual = "General";

    public TCPServerThread50(Socket client, TCPServer50 tcpserver, int clientID) {
        this.client = client;
        this.tcpserver = tcpserver;
        this.clientID = clientID;
    }

    @Override
    public void run() {
        running = true;
        try {
            mOut = new DataOutputStream(client.getOutputStream());
            mIn = new DataInputStream(client.getInputStream());
            System.out.println("TCP Server C: Conectado cliente ID " + clientID);

            while (running) {
                // LEER CABECERA (Protocolo)
                // 1 byte: Tipo (1=Texto, 2=Imagen, 3=Archivo)
                int tipo = mIn.readByte();
                // 4 bytes: Longitud del contenido
                int longitud = mIn.readInt();

                // Leer el Payload (el contenido real)
                byte[] payload = new byte[longitud];
                mIn.readFully(payload);

                // Procesar el mensaje según su tipo
                if (tcpserver.getMessageListener() != null) {
                    if (tipo == 1) {
                        String texto = new String(payload, "UTF-8");
                        // Si es un comando de cambio de grupo, no se cifra y se procesa aquí
                        if (texto.startsWith("JOIN:")) {
                            this.grupoActual = texto.substring(5).trim();
                            System.out.println("Cliente " + clientID + " se unió al grupo: " + this.grupoActual);
                        } else {
                            // Pasamos el grupo y el mensaje al nodo principal separados por un "|"
                            tcpserver.getMessageListener().messageReceived(this.grupoActual + "|" + "Cliente " + clientID + ": " + texto);
                        }
                    } else {
                        // Para imágenes (2) o archivos (3), guardamos los bytes en disco
                        String extension = (tipo == 2) ? ".jpg" : ".dat";
                        String fileName = "recibido_cliente_" + clientID + "_" + System.currentTimeMillis() + extension;

                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName)) {
                            fos.write(payload);
                        }
                        tcpserver.getMessageListener().messageReceived("Archivo guardado con éxito: " + fileName + " (" + longitud + " bytes)");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("TCP Server S: Error o desconexión del cliente " + clientID + ": " + e.getMessage());
        } finally {
            stopClient();
        }
    }

    public void sendMessage(int tipo, byte[] data) {
        try {
            if (mOut != null) {
                mOut.writeByte(tipo);
                mOut.writeInt(data.length);
                mOut.write(data);
                mOut.flush();
            }
        } catch (IOException e) {
            System.out.println("Error enviando mensaje al cliente " + clientID + ": " + e.getMessage());
        }
    }

    public void stopClient() {
        running = false;
        try {
            if (client != null && !client.isClosed()) {
                client.close();
            }
        } catch (IOException e) {
            System.out.println("Error al cerrar socket del cliente " + clientID);
        }
    }
}