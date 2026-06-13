import utils.TCPServer50;

public class MainVentas {
    public static void main(String[] args) {
        System.out.println("Iniciando Nodo de Ventas Automatizado en puerto 8192...");
        final TCPServer50[] serverRef = new TCPServer50[1];

        serverRef[0] = new TCPServer50(8192, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                System.out.println("[NODO VENTAS RECIBIÓ]: " + message);

                // Logica del Bot de Ventas
                if (message.contains("COMPRAR")) {
                    String[] partes = message.split(":");
                    if (partes.length >= 2) {
                        String producto = partes[1].trim();
                        String recibo = "=== COMPROBANTE DE PAGO ===\n" +
                                "Producto: " + producto + "\n" +
                                "Estado: Aprobado\n" +
                                "===========================";

                        // El servidor responde automáticamente con el recibo
                        serverRef[0].broadcastMessage(1, recibo);
                    }
                }
            }
        });

        serverRef[0].run();
    }
}