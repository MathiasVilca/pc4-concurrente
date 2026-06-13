import utils.TCPServer50;
import java.util.HashMap;
import java.util.Map;

public class MainVentas {
    //BDs
    private static Map<String, Integer> inventario = new HashMap<>();
    private static Map<String, String> registroClientes = new HashMap<>();

    //Metricas de rendimiento básicas
    private static int totalPedidosProcesados = 0;
    private static long tiempoTotalProcesamiento = 0;

    public static void main(String[] args) {
        //Inicializar inventario (asumimos suscripciones entr usuarios)
        inventario.put("PREMIUM", 100);
        inventario.put("BASICO", 500);

        System.out.println("Iniciando Nodo de Ventas Automatizado en puerto 8192...");

        final TCPServer50[] serverRef = new TCPServer50[1];

        serverRef[0] = new TCPServer50(8192, new TCPServer50.OnMessageReceived() {
            @Override
            public void messageReceived(String message) {
                long inicioProcesamiento = System.currentTimeMillis();
                System.out.println("[NODO VENTAS RECIBIÓ]: " + message);
                //Registro de Clientes, Pedidos y Comprobantes
                if (message.contains("COMPRAR:")) { //evade prefijo
                    //FORMATO ESPERADO DE COMPRA: "COMPRAR:Mathias:PREMIUM"
                    String comandoReal = message.substring(message.indexOf("COMPRAR:"));
                    String[] partes = comandoReal.split(":");
                    if (partes.length == 3) {
                        String cliente = partes[1].trim();
                        String producto = partes[2].trim().toUpperCase();

                        registroClientes.put(cliente, "Activo"); // Registro de cliente

                        if (inventario.containsKey(producto) && inventario.get(producto) > 0) {
                            inventario.put(producto, inventario.get(producto) - 1);

                            //Generación de comprobante
                            String recibo = "=== COMPROBANTE ===\n" +
                                    "Cliente: " + cliente + "\n" +
                                    "Producto: " + producto + "\n" +
                                    "Estado: APROBADO (En seguimiento)\n" +
                                    "===================";
                            serverRef[0].broadcastMessage(1, recibo);
                            totalPedidosProcesados++;
                        } else {
                            serverRef[0].broadcastMessage(1, "Error: Producto agotado o no existe.");
                        }
                    }
                }
                //Reportes Automáticos y Metricas de Rendimiento
                else if (message.contains("REPORTE")) {
                    String reporte = "=== MÉTRICAS DE RENDIMIENTO ===\n" +
                            "Pedidos procesados: " + totalPedidosProcesados + "\n" +
                            "Tiempo prom. procesamiento: " + (totalPedidosProcesados > 0 ? (tiempoTotalProcesamiento/totalPedidosProcesados) : 0) + " ms\n" +
                            "Inventario PREMIUM: " + inventario.get("PREMIUM") + "\n" +
                            "===============================";
                    serverRef[0].broadcastMessage(1, reporte);
                }

                //Calculo de tiempo
                tiempoTotalProcesamiento += (System.currentTimeMillis() - inicioProcesamiento);
            }
        });

        serverRef[0].run();
    }
}