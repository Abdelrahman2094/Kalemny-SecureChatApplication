/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */


import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientHandler implements Runnable {
    private static final Map<String, ClientHandler> loggedInUsers = new ConcurrentHashMap<>();
    private static final Map<String, String> credentials = new ConcurrentHashMap<>();
    private static final File credentialsFile = new File("resources/users.txt");

    static {
        if (credentialsFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(credentialsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        credentials.put(parts[0], parts[1]);
                    }
                }
                System.out.println("Loaded users: " + credentials.keySet());
            } catch (IOException e) {
                System.err.println("Failed to load credentials: " + e.getMessage());
            }
        }
    }

    private final SSLSocket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final List<ClientHandler> clients;
    private String username;
    private String currentRoom = "default";

    private static final File chatDir = new File("resources/chats");
    static {
        if (!chatDir.exists()) chatDir.mkdirs();
    }

    public ClientHandler(SSLSocket socket, List<ClientHandler> clients) throws IOException {
        this.socket = socket;
        this.clients = clients;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            Message authMsg = (Message) in.readObject();
            if ("SIGNUP".equals(authMsg.getType())) {
                String[] parts = authMsg.getContent().split(":", 2);
                String uname = parts[0];
                String pwd = parts[1];
                if (credentials.containsKey(uname)) {
                    send(new Message("Server", "ERROR", "Username already exists."));
                    return;
                } else {
                    credentials.put(uname, pwd);
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(credentialsFile, true))) {
                        writer.write(uname + ":" + pwd);
                        writer.newLine();
                    } catch (IOException e) {
                        System.err.println("Failed to save new user: " + e.getMessage());
                    }

                    this.username = uname;
                    loggedInUsers.put(username, this);
                    loadChatHistory("broadcast");

                    send(new Message("Server", "TEXT", "✅ Signup successful. Welcome, " + username));
                    System.out.println("User signed up: " + username);
                }
            } else if ("LOGIN".equals(authMsg.getType())) {
                String[] parts = authMsg.getContent().split(":", 2);
                String uname = parts[0];
                String pwd = parts[1];
                if (credentials.containsKey(uname) && credentials.get(uname).equals(pwd)) {
                    this.username = uname;
                    loggedInUsers.put(username, this);
                    loadChatHistory("broadcast");

                    send(new Message("Server", "TEXT", "✅ Logged in as " + username));
                    System.out.println("Logged in: " + username);
                } else {
                    send(new Message("Server", "ERROR", "Invalid username or password."));
                    return;
                }
            } else {
                send(new Message("Server", "ERROR", "Authentication required."));
                return;
            }

            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                String type = msg.getType();

                switch (type) {
                    case "JOIN":
                        currentRoom = msg.getRoom();
                        System.out.println(username + " joined room: " + currentRoom);
                        send(new Message("Server", "TEXT", "✅ You joined room: " + currentRoom));
                        loadChatHistory(currentRoom);
                        break;

                    case "GET_USERS":
                        StringBuilder userList = new StringBuilder("Online users:\n");
                        for (String user : loggedInUsers.keySet()) {
                            userList.append("- ").append(user).append("\n");
                        }
                        send(new Message("Server", "TEXT", userList.toString()));
                        break;

                    case "TEXT":
                        String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
                        String formattedContent = "[" + timestamp + "] " + msg.getSender() + ": " + msg.getContent();
                        msg.setContent(formattedContent);
                        if (msg.getRecipient() == null || msg.getRecipient().isEmpty()) {
                            broadcast(msg);
                        } else {
                            sendPrivate(msg);
                        }
                        break;

                    case "SEND_HISTORY":
                        String target = msg.getRecipient();
                        loadChatHistory(target);
                        break;

                    case "CALL_REQUEST":
                    case "CALL_ACCEPT":
                    case "CALL_REJECT":
                    case "CALL_END":
                    case "OFFER":
                    case "ANSWER":
                    
                        forwardToRecipient(msg);
                        break;

                    case "FILE":  // ✅ NEW: Handle file messages
                        sendPrivate(msg);
                        break;

                    default:
                        send(new Message("Server", "ERROR", "Unknown message type: " + type));
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Disconnected: " + username);
        } finally {
            clients.remove(this);
            if (username != null) {
                loggedInUsers.remove(username);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.out.println("Failed to send to " + username);
        }
    }

    private void loadChatHistory(String target) {
        String fileName;
        boolean isPrivate = !target.startsWith("room") && !target.equals("broadcast");

        if (isPrivate) {
            fileName = "private_" + (username.compareTo(target) < 0 ? username + "_" + target : target + "_" + username) + ".txt";
        } else {
            fileName = "room_" + target + ".txt";
        }

        File chatFile = new File("resources/chats/" + fileName);
        if (!chatFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(chatFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Message msg = new Message("History", "TEXT", line);
                if (isPrivate) msg.setRecipient(target);
                else msg.setRoom(target);
                send(msg);
            }
        } catch (IOException e) {
            System.err.println("Failed to load chat history for " + target + ": " + e.getMessage());
        }
    }

    private void saveChatToFile(String chatFile, String messageLine) {
        try {
            String timestamp = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
            String formattedLine = "[" + timestamp + "] " + messageLine;

            Files.write(Paths.get("resources/chats/" + chatFile),
                    (formattedLine + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to save chat to file: " + chatFile);
        }
    }

    private void broadcast(Message msg) {
        String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
        String sender = msg.getSender();
        String formatted = "[" + timestamp + "] " + sender + ": " + msg.getContent();
        String room = msg.getRoom() != null ? msg.getRoom() : "broadcast";
        saveChatToFile("room_" + room + ".txt", formatted);

        for (ClientHandler client : clients) {
            if (client.currentRoom.equals(room)) {
                client.send(msg);
            }
        }
    }

    
//    private void sendPrivate(Message msg) {
//        String recipient = msg.getRecipient();
//        
//        // ✅ NEW: Handle file transfer
//        if ("FILE".equals(msg.getType())) {
//            ClientHandler receiver = loggedInUsers.get(recipient);
//            if (receiver != null) {
//                receiver.send(msg);  // send file directly
//            } else {
//                send(new Message("Server", "ERROR", "User not found: " + recipient));
//            }
//            return;
//        }
//
//        ClientHandler receiver = loggedInUsers.get(recipient);
//        String line = msg.getSender() + ": " + msg.getContent();
//
//        String fileName = "private_" + (msg.getSender().compareTo(recipient) < 0
//                ? msg.getSender() + "_" + recipient
//                : recipient + "_" + msg.getSender()) + ".txt";
//
//        saveChatToFile(fileName, line);
//
//        if (receiver != null) {
//            receiver.send(msg);
//        } else {
//            send(new Message("Server", "ERROR", "User not found: " + recipient));
//        }
//
//        if (!msg.getSender().equals(recipient)) {
//            this.send(msg);
//        }
//    }
    
    
private void sendPrivate(Message msg) {
    String recipient = msg.getRecipient();
    ClientHandler receiver = loggedInUsers.get(recipient);

    // ✅ Handle file transfer
    if ("FILE".equals(msg.getType())) {
        if (receiver != null) {
            receiver.send(msg);  // Send file directly
        } else {
            send(new Message("Server", "ERROR", "User not found: " + recipient));
        }
        return;
    }

    // ✅ Handle text message: format with timestamp
    String timestamp = new SimpleDateFormat("HH:mm").format(new Date());
    String logLine = "[" + timestamp + "] " + msg.getSender() + ": " + msg.getContent();

    String fileName = "private_" + (msg.getSender().compareTo(recipient) < 0
            ? msg.getSender() + "_" + recipient
            : recipient + "_" + msg.getSender()) + ".txt";
    saveChatToFile(fileName, logLine);

    // ✅ Send to recipient if online
    if (receiver != null) {
        receiver.send(msg);
    } else {
        send(new Message("Server", "ERROR", "User not found: " + recipient));
    }

    // ✅ Echo back to sender (for UI update)
    if (this.username.equals(msg.getSender())) {
        this.send(msg);
    }
}
    
    
    

    private void forwardToRecipient(Message msg) {
        if (msg.getRecipient().startsWith("room") || msg.getRecipient().equals("broadcast")) {
            for (ClientHandler client : clients) {
                if (!client.username.equals(this.username) && client.currentRoom.equals(msg.getRecipient())) {
                    client.send(msg);
                }
            }
        } else {
            ClientHandler recipient = loggedInUsers.get(msg.getRecipient());
            if (recipient != null) {
                recipient.send(msg);
            } else {
                send(new Message("Server", "ERROR", "Recipient not found: " + msg.getRecipient()));
            }
        }
    }
}
