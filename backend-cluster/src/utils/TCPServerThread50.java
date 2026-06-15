package utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TCPServerThread50 implements Runnable {
    private Socket client;
    private TCPServer50 tcpserver;
    private int clientID;
    private volatile boolean running = false;
    private DataOutputStream mOut;
    private DataInputStream mIn;
    public String grupoActual = "General";
    private String idClientChat = "0812";
    private static final byte[] FILE_META_MAGIC = "DOGMETA1".getBytes(StandardCharsets.UTF_8);

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
                int tipo = mIn.readByte();
                int longitud = mIn.readInt();

                byte[] payload = new byte[longitud];
                mIn.readFully(payload);

                if (tcpserver.getMessageListener() != null) {
                    if (tipo == 1) {
                        processTextMessage(new String(payload, "UTF-8"));
                    } else {
                        processBinaryMessage(tipo, longitud, payload);
                    }
                }
            }
        } catch (EOFException e) {
            System.out.println("TCP Server S: Cliente " + clientID + " desconectado correctamente");
        } catch (IOException e) {
            if (isNormalDisconnect(e)) {
                System.out.println("TCP Server S: Cliente " + clientID + " desconectado correctamente");
            } else {
                System.out.println("TCP Server S: Error del cliente " + clientID + ": " + e.getMessage());
            }
        } catch (RuntimeException e) {
            if (isNormalDisconnect(e)) {
                System.out.println("TCP Server S: Cliente " + clientID + " desconectado correctamente");
            } else {
                System.out.println("TCP Server S: Error del cliente " + clientID + ": " + e.getMessage());
            }
        } finally {
            stopClient();
        }
    }

    private boolean isNormalDisconnect(Exception e) {
        String message = e.getMessage();
        return message == null
                || message.toLowerCase().contains("connection reset")
                || message.toLowerCase().contains("socket closed")
                || message.toLowerCase().contains("se ha anulado una conexion")
                || message.toLowerCase().contains("forcibly closed");
    }

    private void processTextMessage(String texto) {
        if (texto.startsWith("GET_IMAGE:") || texto.startsWith("GET_FILE:")) {
            sendStoredBinary(texto.substring(texto.indexOf(":") + 1).trim());
            return;
        }

        if (texto.startsWith("JOIN:")) {
            this.grupoActual = texto.substring(5).trim();
            System.out.println("Cliente " + clientID + " se unio al grupo: " + this.grupoActual);
            sendText("SISTEMA: Unido al grupo " + this.grupoActual);
            return;
        }

        if (texto.startsWith("IDENT:")) {
            this.idClientChat = normalizeChatId(texto.substring(6));
            System.out.println("Cliente " + clientID + " validado como idClientChat " + this.idClientChat);
            sendText("SISTEMA: ClienteAsignado:" + clientID);
            sendText("SISTEMA: Usuario Validado idClientChat " + this.idClientChat);
            return;
        }

        if (texto.startsWith("CLONE:")) {
            this.idClientChat = normalizeChatId(texto.substring(6));
            System.out.println("Cliente " + clientID + " solicita clonar idClientChat " + this.idClientChat);
            sendText("SISTEMA: Clonando historial de idClientChat " + this.idClientChat);
            tcpserver.sendHistory(this.idClientChat, this);
            return;
        }

        String mensajeChat = "Cliente " + clientID + ": " + texto;
        tcpserver.saveHistory(this.idClientChat, mensajeChat);
        tcpserver.getMessageListener().messageReceived(this.grupoActual + "|" + mensajeChat);
    }

    private void processBinaryMessage(int tipo, int longitud, byte[] payload) throws IOException {
        String extension = (tipo == 2) ? ".jpg" : ".dat";
        String etiqueta = (tipo == 2) ? "Imagen" : "Archivo";
        FilePayload filePayload = decodeFilePayload(payload);
        String ownerClientId = normalizeFileOwner(filePayload.ownerClientId);
        String fileName = "recibido_cliente_" + ownerClientId + "_" + System.currentTimeMillis() + extension;

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            fos.write(filePayload.content);
        }

        String detalleArchivo = "[" + etiqueta + " recibido: " + fileName + " (" + filePayload.content.length + " bytes)]";
        String eventoArchivo = "Cliente " + ownerClientId + ": " + detalleArchivo;
        tcpserver.saveHistory(this.idClientChat, eventoArchivo);
        tcpserver.getMessageListener().messageReceived(this.grupoActual + "|" + eventoArchivo);
        sendText("BINARY_OK:" + detalleArchivo);
    }

    private void sendStoredBinary(String requestedName) {
        try {
            String safeName = new File(requestedName).getName();
            if (!safeName.startsWith("recibido_cliente_")) {
                sendText("ERROR: Archivo no permitido");
                return;
            }

            File file = new File(safeName);
            if (!file.exists() || !file.isFile()) {
                sendText("ERROR: Archivo no encontrado " + safeName);
                return;
            }

            byte[] data = Files.readAllBytes(file.toPath());
            int tipo = isImageFile(safeName) ? 2 : 3;
            sendMessage(tipo, data);
            System.out.println("TCP Server S: Enviado archivo solicitado " + safeName + " (" + data.length + " bytes)");
        } catch (IOException e) {
            sendText("ERROR: No se pudo enviar archivo: " + e.getMessage());
        }
    }

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".webp");
    }

    private FilePayload decodeFilePayload(byte[] payload) {
        if (!hasFileMetadata(payload)) {
            return new FilePayload(null, null, payload);
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            byte[] magic = new byte[FILE_META_MAGIC.length];
            int magicRead = bais.read(magic, 0, magic.length);
            if (magicRead != FILE_META_MAGIC.length) {
                return new FilePayload(null, null, payload);
            }

            DataInputStream metadataIn = new DataInputStream(bais);
            int nameLength = metadataIn.readInt();
            if (nameLength <= 0 || nameLength > 4096 || nameLength > bais.available()) {
                return new FilePayload(null, null, payload);
            }

            byte[] nameBytes = new byte[nameLength];
            metadataIn.readFully(nameBytes);
            String fileName = new String(nameBytes, StandardCharsets.UTF_8);

            String ownerClientId = null;
            if (fileName.startsWith("CLIENTE_CHAT=") && fileName.contains("|")) {
                String[] parts = fileName.split("\\|", 2);
                ownerClientId = parts[0].substring("CLIENTE_CHAT=".length()).trim();
                fileName = parts[1];
            }

            byte[] content = new byte[bais.available()];
            metadataIn.readFully(content);
            return new FilePayload(fileName, ownerClientId, content);
        } catch (IOException e) {
            return new FilePayload(null, null, payload);
        }
    }

    private String normalizeFileOwner(String ownerClientId) {
        if (ownerClientId == null || ownerClientId.trim().isEmpty()) {
            return String.valueOf(clientID);
        }

        return ownerClientId.trim().replaceAll("[^0-9A-Za-z_-]", "_");
    }

    private boolean hasFileMetadata(byte[] payload) {
        if (payload.length < FILE_META_MAGIC.length + 4) {
            return false;
        }

        for (int i = 0; i < FILE_META_MAGIC.length; i++) {
            if (payload[i] != FILE_META_MAGIC[i]) {
                return false;
            }
        }

        return true;
    }

    private static class FilePayload {
        private final String fileName;
        private final String ownerClientId;
        private final byte[] content;

        private FilePayload(String fileName, String ownerClientId, byte[] content) {
            this.fileName = fileName;
            this.ownerClientId = ownerClientId;
            this.content = content;
        }
    }

    private String normalizeChatId(String value) {
        String clean = value.trim();
        return clean.isEmpty() ? "0812" : clean;
    }

    public void sendText(String message) {
        try {
            sendMessage(1, message.getBytes("UTF-8"));
        } catch (IOException e) {
            System.out.println("Error preparando texto para cliente " + clientID + ": " + e.getMessage());
        }
    }

    public void sendMessage(int tipo, byte[] data) {
        try {
            if (mOut != null) {
                synchronized (mOut) {
                    mOut.writeByte(tipo);
                    mOut.writeInt(data.length);
                    mOut.write(data);
                    mOut.flush();
                }
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
        } finally {
            tcpserver.removeClient(this);
        }
    }
}
