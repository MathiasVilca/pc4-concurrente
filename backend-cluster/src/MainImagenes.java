import utils.TCPServer50;

public class MainImagenes {
    public static void main(String[] args) {
        // Instanciamos el servidor de imágenes en el puerto 8191
        TCPServer50 server = new TCPServer50(8191, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                System.out.println("[NODO IMAGENES] " + message);
            }
        });

        System.out.println("Iniciando Nodo de Imágenes...");
        server.run();
    }
}