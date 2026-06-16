import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DogMessengerDesktop extends JFrame {
    private final JTextField txtIp = new JTextField("127.0.0.1");
    private final JTextField txtPort = new JTextField("8189");
    private final JTextField txtIdChat = new JTextField("0812");
    private final JTextField txtToken = new JTextField("DOGQR://CLONE/0812");
    private final JTextField txtMessage = new JTextField();
    private final JPanel chatPanel = new JPanel();
    private final JScrollPane chatScrollPane = new JScrollPane(chatPanel);

    private Socket socket;
    private DataOutputStream out;
    private volatile boolean running = false;
    private String assignedChatClientId = null;

    public DogMessengerDesktop() {
        super("Dog Messenger Desktop");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 520);
        setLocationRelativeTo(null);

        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        add(buildTopPanel(), BorderLayout.NORTH);
        add(chatScrollPane, BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 3, 6, 6));
        JButton btnConnect = new JButton("Conectar");
        JButton btnDisconnect = new JButton("Desconectar");
        JButton btnEmitQr = new JButton("Emitir QR");
        JButton btnClone = new JButton("Clonar");

        btnConnect.addActionListener(e -> connect());
        btnDisconnect.addActionListener(e -> disconnect());
        btnEmitQr.addActionListener(e -> emitQrToken());
        btnClone.addActionListener(e -> cloneHistory());

        panel.add(new JLabel("IP"));
        panel.add(txtIp);
        panel.add(btnConnect);
        panel.add(new JLabel("Puerto"));
        panel.add(txtPort);
        panel.add(btnDisconnect);
        panel.add(new JLabel("idClientChat"));
        panel.add(txtIdChat);
        panel.add(btnEmitQr);
        panel.add(new JLabel("QR/Token"));
        panel.add(txtToken);
        panel.add(btnClone);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JButton btnSend = new JButton("Enviar");
        JButton btnImage = new JButton("Imagen");
        JButton btnFile = new JButton("Archivo");
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 6, 6));

        btnSend.addActionListener(e -> sendChatMessage());
        btnImage.addActionListener(e -> chooseAndSendBinary(8191, 2, "imagen"));
        btnFile.addActionListener(e -> chooseAndSendBinary(8190, 3, "archivo"));
        txtMessage.addActionListener(e -> sendChatMessage());

        buttonPanel.add(btnSend);
        buttonPanel.add(btnImage);
        buttonPanel.add(btnFile);

        panel.add(txtMessage, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);
        return panel;
    }

    private void connect() {
        try {
            if (running) {
                append("Ya existe una conexion activa.");
                return;
            }

            String ip = txtIp.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            socket = new Socket(InetAddress.getByName(ip), port);
            out = new DataOutputStream(socket.getOutputStream());
            running = true;
            new Thread(this::readLoop, "dog-desktop-reader").start();
            sendPlain("IDENT:" + getChatId());
            append("Conectado como idClientChat " + getChatId());
        } catch (Exception e) {
            append("Error al conectar: " + e.getMessage());
        }
    }

    private void readLoop() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
            while (running) {
                int tipo = in.readByte();
                int length = in.readInt();
                byte[] payload = new byte[length];
                in.readFully(payload);

                if (tipo == 1) {
                    String message = new String(payload, StandardCharsets.UTF_8);
                    SwingUtilities.invokeLater(() -> {
                        String formattedMessage = formatIncoming(message);
                        if (!formattedMessage.isBlank()) {
                            append(formattedMessage);
                        }
                    });
                }
            }
        } catch (Exception e) {
            if (running) {
                SwingUtilities.invokeLater(() -> append("Conexion cerrada: " + e.getMessage()));
            }
        } finally {
            disconnect();
        }
    }

    private void sendChatMessage() {
        String message = txtMessage.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        if (!running) {
            append("Conectate antes de enviar.");
            return;
        }

        try {
            if (message.startsWith("/join ")) {
                sendPlain("JOIN:" + message.substring(6).trim());
                append("--- Te has movido al grupo: " + message.substring(6).trim() + " ---");
            } else {
                sendPlain(AESCryptoDesktop.encrypt(message));
            }
            txtMessage.setText("");
        } catch (Exception e) {
            append("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void emitQrToken() {
        String token = "DOGQR://CLONE/" + getChatId();
        txtToken.setText(token);
        append("QR emitido: " + token);
    }

    private void cloneHistory() {
        if (!running) {
            append("Conectate antes de clonar.");
            return;
        }

        String id = extractIdFromToken(txtToken.getText());
        txtIdChat.setText(id);
        try {
            sendPlain("CLONE:" + id);
            append("Solicitando historial de idClientChat " + id + "...");
        } catch (Exception e) {
            append("Error clonando historial: " + e.getMessage());
        }
    }

    private void sendPlain(String message) throws Exception {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            out.writeByte(1);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        }
    }

    private void chooseAndSendBinary(int port, int tipo, String label) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            byte[] data = Files.readAllBytes(chooser.getSelectedFile().toPath());
            String fileName = chooser.getSelectedFile().getName();
            String chatMessage = sendBinaryToNode(port, tipo, fileName, data);
            if (running) {
                sendPlain(chatMessage);
            }
        } catch (Exception e) {
            append("Error enviando " + label + ": " + e.getMessage());
        }
    }

    private String sendBinaryToNode(int port, int tipo, String fileName, byte[] data) throws Exception {
        String ip = txtIp.getText().trim();
        byte[] payload = packBinary(fileName, data);
        try (Socket binarySocket = new Socket(InetAddress.getByName(ip), port);
             DataOutputStream binaryOut = new DataOutputStream(binarySocket.getOutputStream())) {
            binarySocket.setSoTimeout(5000);
            binaryOut.writeByte(tipo);
            binaryOut.writeInt(payload.length);
            binaryOut.write(payload);
            binaryOut.flush();

            DataInputStream binaryIn = new DataInputStream(binarySocket.getInputStream());
            int responseType = binaryIn.readByte();
            int responseLength = binaryIn.readInt();
            byte[] responsePayload = new byte[responseLength];
            binaryIn.readFully(responsePayload);
            String response = new String(responsePayload, StandardCharsets.UTF_8);

            if (responseType == 1 && response.startsWith("BINARY_OK:")) {
                return response.substring("BINARY_OK:".length());
            }

            String displayLabel = tipo == 2 ? "Imagen" : "Archivo";
            return "[" + displayLabel + " recibido: recibido_cliente_local_" + System.currentTimeMillis() + " (" + data.length + " bytes)]";
        }
    }

    private byte[] packBinary(String fileName, byte[] data) throws Exception {
        String owner = assignedChatClientId == null ? "sin_id" : assignedChatClientId;
        byte[] nameBytes = ("CLIENTE_CHAT=" + owner + "|" + fileName).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream payloadOut = new DataOutputStream(baos)) {
            payloadOut.write("DOGMETA1".getBytes(StandardCharsets.UTF_8));
            payloadOut.writeInt(nameBytes.length);
            payloadOut.write(nameBytes);
            payloadOut.write(data);
        }
        return baos.toByteArray();
    }

    private String formatIncoming(String message) {
        if (message.startsWith("SISTEMA: ClienteAsignado:")) {
            assignedChatClientId = message.substring("SISTEMA: ClienteAsignado:".length()).trim();
            return message;
        }

        if (message.startsWith("HISTORY_BEGIN:")) {
            return "--- Inicio historial " + message.substring("HISTORY_BEGIN:".length()) + " ---";
        }

        if (message.startsWith("HISTORY_END:")) {
            return "--- Fin historial " + message.substring("HISTORY_END:".length()) + " ---";
        }

        if (message.startsWith("HISTORY_ITEM:")) {
            return "[Historial] " + formatChatPayload(message.substring("HISTORY_ITEM:".length()));
        }

        return formatChatPayload(message);
    }

    private String formatChatPayload(String message) {
        if (!message.startsWith("Cliente ")) {
            return message;
        }

        String[] parts = message.split(": ", 2);
        if (parts.length != 2) {
            return message;
        }

        try {
            return parts[0] + ": " + AESCryptoDesktop.decrypt(parts[1]);
        } catch (Exception e) {
            return message;
        }
    }

    private String extractIdFromToken(String token) {
        String clean = token.trim();
        if (clean.startsWith("DOGQR://CLONE/")) {
            return clean.substring("DOGQR://CLONE/".length()).trim();
        }
        if (clean.startsWith("CLONE:")) {
            return clean.substring("CLONE:".length()).trim();
        }
        return clean.isEmpty() ? "0812" : clean;
    }

    private String getChatId() {
        String value = txtIdChat.getText().trim();
        return value.isEmpty() ? "0812" : value;
    }

    private void append(String text) {
        JLabel label = new JLabel("<html>" + escapeHtml(text) + "</html>");
        label.setAlignmentX(LEFT_ALIGNMENT);
        chatPanel.add(label);
        extractImageName(text);
        scrollToBottom();
    }

    private void extractImageName(String text) {
        Pattern pattern = Pattern.compile("\\[Imagen recibido: ([^\\s\\]]+) \\([0-9]+ bytes\\)\\]");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            downloadAndAppendImage(matcher.group(1));
        }
    }

    private void downloadAndAppendImage(String imageName) {
        new Thread(() -> {
            try {
                byte[] imageBytes = requestBinary(8191, "GET_IMAGE:" + imageName);
                ImageIcon icon = new ImageIcon(imageBytes);
                if (icon.getIconWidth() <= 0) {
                    SwingUtilities.invokeLater(() -> appendSmallText("No se pudo mostrar imagen " + imageName));
                    return;
                }

                int targetWidth = 360;
                int width = icon.getIconWidth();
                int height = icon.getIconHeight();
                int targetHeight = Math.max(120, (height * targetWidth) / Math.max(width, 1));
                Image scaledImage = icon.getImage().getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaledImage);

                SwingUtilities.invokeLater(() -> {
                    JLabel imageLabel = new JLabel(scaledIcon);
                    imageLabel.setAlignmentX(LEFT_ALIGNMENT);
                    chatPanel.add(imageLabel);
                    scrollToBottom();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> appendSmallText("No se pudo descargar imagen " + imageName + ": " + e.getMessage()));
            }
        }, "dog-desktop-image-loader").start();
    }

    private byte[] requestBinary(int port, String command) throws Exception {
        String ip = txtIp.getText().trim();
        byte[] data = command.getBytes(StandardCharsets.UTF_8);

        try (Socket requestSocket = new Socket(InetAddress.getByName(ip), port);
             DataOutputStream requestOut = new DataOutputStream(requestSocket.getOutputStream());
             DataInputStream requestIn = new DataInputStream(requestSocket.getInputStream())) {
            requestSocket.setSoTimeout(8000);
            requestOut.writeByte(1);
            requestOut.writeInt(data.length);
            requestOut.write(data);
            requestOut.flush();

            int responseType = requestIn.readByte();
            int responseLength = requestIn.readInt();
            byte[] responsePayload = new byte[responseLength];
            requestIn.readFully(responsePayload);

            if (responseType != 2) {
                throw new IllegalStateException(new String(responsePayload, StandardCharsets.UTF_8));
            }

            return responsePayload;
        }
    }

    private void appendSmallText(String text) {
        JLabel label = new JLabel("<html><small>" + escapeHtml(text) + "</small></html>");
        label.setAlignmentX(LEFT_ALIGNMENT);
        chatPanel.add(label);
        scrollToBottom();
    }

    private void scrollToBottom() {
        chatPanel.revalidate();
        chatPanel.repaint();
        SwingUtilities.invokeLater(() -> chatScrollPane.getVerticalScrollBar().setValue(chatScrollPane.getVerticalScrollBar().getMaximum()));
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    private void disconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DogMessengerDesktop().setVisible(true));
    }

    private static class AESCryptoDesktop {
        private static final byte[] KEY = "DogMessenger2026".getBytes(StandardCharsets.UTF_8);
        private static final SecretKeySpec SECRET_KEY_SPEC = new SecretKeySpec(KEY, "AES");

        static String encrypt(String message) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY_SPEC);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        }

        static String decrypt(String encryptedBase64) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY_SPEC);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        }
    }
}
