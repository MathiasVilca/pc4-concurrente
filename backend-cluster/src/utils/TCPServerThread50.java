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
                        tcpserver.getMessageListener().messageReceived("Cliente " + clientID + ": " + texto);
                    } else {
                        // Para imágenes o archivos (Día 2), pasamos los bytes puros
                        tcpserver.getMessageListener().messageReceived("Cliente " + clientID + " envió archivo/imagen de " + longitud + " bytes.");
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