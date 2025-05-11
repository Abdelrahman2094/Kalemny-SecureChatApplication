/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */
//
//import javax.net.ssl.*;
//import java.io.*;
//import java.security.KeyStore;
//import java.util.Scanner;
//
//public class SecureChatClient {
//
//    private static final String SERVER_HOST = "localhost";
//    private static final int SERVER_PORT = 1234;
//    private ObjectOutputStream out;
//    private ObjectInputStream in;
//
//    public static void main(String[] args) {
//        new SecureChatClient().startClient();
//    }
//
//    public void startClient() {
//        try {
//            // ===== Load truststore manually =====
//            String keystorePath = "C:/BUE (CS)/Year2/NP/NP (Chat Application)/server.keystore";
//            char[] password = "password".toCharArray();
//
//            KeyStore trustStore = KeyStore.getInstance("JKS");
//            trustStore.load(new FileInputStream(keystorePath), password);
//
//            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//            tmf.init(trustStore);
//
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(null, tmf.getTrustManagers(), null);
//
//            SSLSocketFactory factory = sslContext.getSocketFactory();
//            SSLSocket socket = (SSLSocket) factory.createSocket(SERVER_HOST, SERVER_PORT);
//
//            out = new ObjectOutputStream(socket.getOutputStream());
//            out.flush();
//            in = new ObjectInputStream(socket.getInputStream());
//
//            Scanner scanner = new Scanner(System.in);
//
//            // ===== Send LOGIN message =====
//            System.out.print("Enter your username: ");
//            String username = scanner.nextLine().trim();
//            Message loginMsg = new Message(username, "LOGIN", "Login");
//            out.writeObject(loginMsg);
//            out.flush();
//
//            // ===== Listen for messages from server in a thread =====
//            new Thread(() -> {
//                try {
//                    Message msg;
//                    while ((msg = (Message) in.readObject()) != null) {
//                        System.out.println(msg.getSender() + ": " + msg.getContent());
//                    }
//                } catch (Exception e) {
//                    System.out.println("Disconnected from server.");
//                }
//            }).start();
//
//            // ===== Chat loop =====
//            String currentRoom = "default";
//
//            while (true) {
//                System.out.println("[room: " + currentRoom + "] Recipient (leave blank for group or type '/join roomName'): ");
//                String input = scanner.nextLine().trim();
//
//                // ===== Handle /join command =====
//                if (input.toLowerCase().startsWith("/join")) {
//                    String[] parts = input.split("\\s+");
//                    if (parts.length >= 2) {
//                        String roomName = parts[1];
//                        Message joinMsg = new Message(username, "JOIN", "");
//                        joinMsg.setRoom(roomName);
//                        out.writeObject(joinMsg);
//                        out.flush();
//                        currentRoom = roomName;
//                        System.out.println("You joined room: " + currentRoom);
//                    } else {
//                        System.out.println(" Usage: /join roomName");
//                    }
//                    continue;
//                }
//
//                // ===== Handle /exit to default room =====
//                if (input.equalsIgnoreCase("/exit")) {
//                    currentRoom = "default";
//                    System.out.println("You exited to default room.");
//                    continue;
//                }
//
//                // ===== Send normal message (private or group) =====
//                String recipient = input;
//                System.out.print("Message: ");
//                String text = scanner.nextLine().trim();
//                if (text.isEmpty()) continue;
//
//                Message message = new Message(username, "TEXT", text);
//                message.setRoom(currentRoom);
//                if (!recipient.isEmpty()) {
//                    message.setRecipient(recipient);
//                }
//
//                out.writeObject(message);
//                out.flush();
//            }
//
//        } catch (Exception e) {
//            System.out.println("Client error: " + e.getMessage());
//        }
//    }
//}




import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.Scanner;

public class SecureChatClient {
    private static final int PORT = 1234;
    private static final String HOST = "localhost";
    private static final int CHUNK_SIZE = 4096;

    public static void main(String[] args) {
        try {
            String keystorePath = "E:/uni files/Year 3/Semester 2/Network Programming/Project/SecureChatApp/resources/keystore.jks";
            char[] password = "123456".toCharArray();

            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(new FileInputStream(keystorePath), password);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket(HOST, PORT);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Scanner scanner = new Scanner(System.in);

            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Password: ");
            String passwordInput = scanner.nextLine();

            System.out.print("Login or Signup (L/S): ");
            String mode = scanner.nextLine().trim().toUpperCase();
            String type = mode.equals("S") ? "SIGNUP" : "LOGIN";

            out.writeObject(new Message(username, type, username + ":" + passwordInput));
            out.flush();

            Message response = (Message) in.readObject();
            System.out.println(response.getContent());

            if ("ERROR".equals(response.getType())) return;

            Thread listener = new Thread(() -> {
                try {
                    while (true) {
                        Object obj = in.readObject();
                        if (obj instanceof Message) {
                            Message msg = (Message) obj;
                            if ("FILE".equals(msg.getType())) {
                                receiveFile(msg);
                            } else {
                                System.out.println(msg.getSender() + ": " + msg.getContent());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected.");
                }
            });
            listener.start();

            while (true) {
                System.out.print("To [username] or 'broadcast': ");
                String recipient = scanner.nextLine().trim();
                System.out.print("Type 'file' to send a file or type your message: ");
                String input = scanner.nextLine();

                if ("file".equalsIgnoreCase(input)) {
                    System.out.print("Enter file path: ");
                    String filePath = scanner.nextLine().trim();
                    sendFile(username, recipient, filePath, out);
                } else {
                    Message msg = new Message(username, "TEXT", input);
                    if (!"broadcast".equalsIgnoreCase(recipient)) {
                        msg.setRecipient(recipient);
                    }
                    out.writeObject(msg);
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static void sendFile(String sender, String recipient, String filePath, ObjectOutputStream out) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            long fileSize = file.length();
            FileInputStream fis = new FileInputStream(file);

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                Message fileMsg = new Message(sender, "FILE", chunk, fileName, fileSize);
                fileMsg.setRecipient(recipient);
                out.writeObject(fileMsg);
                out.flush();
            }

            fis.close();
            System.out.println("✅ File sent successfully.");
        } catch (IOException e) {
            System.err.println("❌ Failed to send file: " + e.getMessage());
        }
    }

    private static void receiveFile(Message msg) {
        try {
            File downloadsDir = new File("downloads");
            if (!downloadsDir.exists()) downloadsDir.mkdir();

            File outFile = new File(downloadsDir, msg.getFileName());
            FileOutputStream fos = new FileOutputStream(outFile, true); // append mode for chunks
            fos.write(msg.getFileData());
            fos.close();

            System.out.println("📥 Received file chunk: " + msg.getFileName());
        } catch (IOException e) {
            System.err.println("❌ Failed to save file: " + e.getMessage());
        }
    }
}

