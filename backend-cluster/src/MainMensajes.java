import utils.TCPServer50;

public class MainMensajes {
    public static void main(String[] args) {
        System.out.println("Iniciando Nodo de Mensajes en puerto 8189...");

        // Usamos un array de 1 elemento para poder usarlo dentro del OnMessageReceived
        final TCPServer50[] serverRef = new TCPServer50[1];

        serverRef[0] = new TCPServer50(8189, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                // Separamos el nombre del grupo del mensaje real
                if (message.contains("|")) {
                    String[] partes = message.split("\\|", 2);
                    String grupo = partes[0];
                    String textoReal = partes[1];

                    System.out.println("[GRUPO " + grupo + "] " + textoReal);
                    // Retransmitimos solo a los miembros de ese grupo
                    serverRef[0].broadcastToGroup(grupo, 1, textoReal);
                }
            }
        });

        // Arrancamos el servidor
        serverRef[0].run();
    }
}