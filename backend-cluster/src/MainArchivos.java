import utils.TCPServer50;

public class MainArchivos {
    public static void main(String[] args) {
        // Instanciamos el servidor de archivos en el puerto 8190
        TCPServer50 server = new TCPServer50(8190, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                System.out.println("[NODO ARCHIVOS] " + message);
            }
        });

        System.out.println("Iniciando Nodo de Archivos...");
        server.run();
    }
}