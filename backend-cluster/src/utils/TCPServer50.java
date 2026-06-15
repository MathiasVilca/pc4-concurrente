package utils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TCPServer50 {
    private int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private List<TCPServerThread50> clientThreads = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, CopyOnWriteArrayList<String>> chatHistory = new ConcurrentHashMap<>();
    private int nrcli = 0;
    private OnMessageReceived messageListener;

    public TCPServer50(int port, OnMessageReceived messageListener) {
        this.port = port;
        this.messageListener = messageListener;
    }

    public OnMessageReceived getMessageListener() {
        return this.messageListener;
    }

    public void run() {
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Servidor TCP iniciado en el puerto " + port + "...");

            while (running) {
                Socket client = serverSocket.accept();
                nrcli++;
                System.out.println("Nuevo cliente aceptado. Total: " + nrcli);

                TCPServerThread50 clientThread = new TCPServerThread50(client, this, nrcli);
                clientThreads.add(clientThread);

                // Ejecución concurrente usando hilos nativos
                new Thread(clientThread).start();
            }
        } catch (IOException e) {
            System.out.println("Error en el servidor: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void saveHistory(String idClientChat, String message) {
        chatHistory.computeIfAbsent(idClientChat, key -> new CopyOnWriteArrayList<>()).add(message);
    }

    public void sendHistory(String idClientChat, TCPServerThread50 clientThread) {
        List<String> history = chatHistory.getOrDefault(idClientChat, new CopyOnWriteArrayList<>());
        clientThread.sendText("HISTORY_BEGIN:" + idClientChat + ":" + history.size());
        for (String oldMessage : history) {
            clientThread.sendText("HISTORY_ITEM:" + oldMessage);
        }
        clientThread.sendText("HISTORY_END:" + idClientChat);
    }

    public void removeClient(TCPServerThread50 clientThread) {
        clientThreads.remove(clientThread);
    }

    public void broadcastMessage(int tipo, String message) {
        try {
            byte[] data = message.getBytes("UTF-8");
            for (TCPServerThread50 clientThread : clientThreads) {
                clientThread.sendMessage(tipo, data);
            }
        } catch (IOException e) {
            System.out.println("Error en broadcast: " + e.getMessage());
        }
    }

    public void broadcastToGroup(String grupo, int tipo, String message) {
        try {
            byte[] data = message.getBytes("UTF-8");
            for (TCPServerThread50 clientThread : clientThreads) {
                // El filtro mágico de grupos
                if (clientThread.grupoActual.equals(grupo)) {
                    clientThread.sendMessage(tipo, data);
                }
            }
        } catch (IOException e) {
            System.out.println("Error en broadcast a grupo: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (TCPServerThread50 clientThread : clientThreads) {
                clientThread.stopClient();
            }
        } catch (IOException e) {
            System.out.println("Error al cerrar el servidor: " + e.getMessage());
        }
    }

    public interface OnMessageReceived {
        void messageReceived(String message);
    }
}
