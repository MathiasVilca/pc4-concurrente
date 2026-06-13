import utils.TCPServer50;

public class MainVentas {
    public static void main(String[] args) {
        // Instanciamos el servidor de ventas en el puerto 8192
        TCPServer50 server = new TCPServer50(8192, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                System.out.println("[NODO VENTAS] " + message);
            }
        });

        System.out.println("Iniciando Nodo de Ventas...");
        server.run();
    }
}