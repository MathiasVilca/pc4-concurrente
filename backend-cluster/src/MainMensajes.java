import utils.TCPServer50;

public class MainMensajes {
    public static void main(String[] args) {
        // Instanciamos el servidor en el puerto 8189
        TCPServer50 server = new TCPServer50(8189, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                // Esto es lo que hará el servidor cuando reciba un texto válido
                System.out.println("[NODO MENSAJES] " + message);
            }
        });

        // Arrancamos el servidor
        server.run();
    }
}