import utils.TCPServer50;

public class MainMensajes {
    public static void main(String[] args) {
        System.out.println("Iniciando Nodo de Mensajes en puerto 8189...");

        // Usamos un array de 1 elemento para poder usarlo dentro del OnMessageReceived
        final TCPServer50[] serverRef = new TCPServer50[1];

        serverRef[0] = new TCPServer50(8189, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                // Servidor imprime texto cifrado
                System.out.println("[NODO MENSAJES] " + message);

                //Retransmisión (prueba)
                serverRef[0].broadcastMessage(1, message);
            }
        });

        // Arrancamos el servidor
        serverRef[0].run();
    }
}