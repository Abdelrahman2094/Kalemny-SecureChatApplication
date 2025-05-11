/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */
//
//
import javax.net.ssl.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.KeyStore;
import java.util.*;
import javax.swing.Timer;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;


public class SecureChatGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private DefaultListModel<String> selectionModel;
    private JList<String> selectionList;
    private String username;
    private String currentRoom = "broadcast";
    private String privateRecipient = null;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private WebRTCHandler webRTCHandler;
    private JButton callButton;

    private final Map<String, JDialog> privateChatDialogs = new HashMap<>();
    private final Map<String, JDialog> roomChatDialogs = new HashMap<>();
    private final Map<String, JTextArea> privateChats = new HashMap<>();
    private final Map<String, JTextArea> roomChats = new HashMap<>();
    private final Set<String> shownFiles = new HashSet<>();


    public SecureChatGUI() {
        setSize(700, 450);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setVisible(false);

        inputField = new JTextField();
        inputField.setVisible(false);

        callButton = new JButton("Call");
        callButton.setVisible(false);

        inputField.addActionListener(e -> sendMessage());
        callButton.addActionListener(e -> webRTCHandler.initiateCall(privateRecipient != null ? privateRecipient : currentRoom));

        selectionModel = new DefaultListModel<>();
        selectionList = new JList<>(selectionModel);
        selectionList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                int index = selectionList.locationToIndex(evt.getPoint());
                if (index >= 0) {
                    String selected = selectionModel.getElementAt(index);
                    if (selected.startsWith("- ")) {
                        String item = selected.substring(2);
                        if (item.equals("broadcast") || item.startsWith("room")) {
                            openRoomChat(item);
                        } else {
                            openPrivateChat(item);
                        }
                    }
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(selectionList);
        listScroll.setPreferredSize(new Dimension(150, 0));
        listScroll.setBorder(BorderFactory.createTitledBorder("Users & Rooms"));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(listScroll, BorderLayout.CENTER);
        rightPanel.add(callButton, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel(" Room: "), BorderLayout.WEST);
        topPanel.add(new JLabel("Logged in as: Not connected"), BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        connectToServer(topPanel);
    }

    private void connectToServer(JPanel topPanel) {
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
            SSLSocket socket = (SSLSocket) factory.createSocket("localhost", 1234);

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            String[] options = {"Login", "Signup"};
            int choice = JOptionPane.showOptionDialog(this, "Welcome to Secure Chat", "Authentication",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

            if (choice == JOptionPane.CLOSED_OPTION) System.exit(0);

            JTextField userField = new JTextField();
            JPasswordField passField = new JPasswordField();
            Object[] message = {
                    "Username:", userField,
                    "Password:", passField
            };

            int result = JOptionPane.showConfirmDialog(this, message, options[choice], JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) System.exit(0);

            username = userField.getText().trim();
            String rawPassword = new String(passField.getPassword()).trim();
            if (username.isEmpty() || rawPassword.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.");
                System.exit(0);
            }

            out.writeObject(new Message(username, choice == 0 ? "LOGIN" : "SIGNUP", username + ":" + rawPassword));
            out.flush();

            Message response = (Message) in.readObject();
            if ("ERROR".equals(response.getType())) {
                JOptionPane.showMessageDialog(this, response.getContent(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            } else if ("TEXT".equals(response.getType())) {
                chatArea.append("[Server] " + response.getContent() + "\n");
                ((JLabel) topPanel.getComponent(1)).setText("Logged in as: " + username);
                webRTCHandler = new WebRTCHandler(username, out);
                startUserListUpdater();
                new Thread(this::listenForMessages).start();
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Connection error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void listenForMessages() {
    try {
        while (true) {
            Object raw = in.readObject();
            if (!(raw instanceof Message)) break;

            Message msg = (Message) raw;
            if ("FILE".equals(msg.getType())) {
                receiveFile(msg);
                continue;
            }
            switch (msg.getType()) {
                case "TEXT":
                    SwingUtilities.invokeLater(() -> {
                        String sender = msg.getSender();
                        String content = msg.getContent();

                        if (content.startsWith("Online users:")) {
                            updateSelectionList(content);
                            return;
                        }

                        if ("History".equals(sender)) {
                            if (msg.getRecipient() != null) {
                                JTextArea privateArea = getPrivateTextArea(msg.getRecipient());
                                privateArea.append(content + "\n");
                            } else if (msg.getRoom() != null) {
                                JTextArea roomArea = getRoomTextArea(msg.getRoom());
                                roomArea.append(content + "\n");
                            }
                            return;
                        }

                        if (msg.getRecipient() != null && !msg.getRecipient().isEmpty()) {
                            String peer = sender.equals(username) ? msg.getRecipient() : sender;
                            String label = sender.equals(username) ? "You" : sender;
                            JTextArea privateArea = getPrivateTextArea(peer);
                            privateArea.append(content + "\n");
                            return;
                        }

                        if (msg.getRoom() != null) {
                            JTextArea roomArea = getRoomTextArea(msg.getRoom());

                            if (sender.equals(username)) {
                                int close = content.indexOf("]");
                                int colon = content.indexOf(":", close);
                                if (close != -1 && colon != -1) {
                                    String timestamp = content.substring(0, close + 1);
                                    String afterName = content.substring(colon + 1).trim();
                                    roomArea.append("You " + timestamp + " " + afterName + "\n");
                                } else {
                                    roomArea.append("You: " + content + "\n");
                                }
                            } else {
                                roomArea.append(content + "\n");
                            }
                            return;
                        }

                        chatArea.append(sender + ": " + content + "\n");
                    });
                    break;

                case "CALL_REQUEST":
                    SwingUtilities.invokeLater(() -> webRTCHandler.handleIncomingCall(msg.getSender()));
                    break;
                case "CALL_ACCEPT":
                    SwingUtilities.invokeLater(webRTCHandler::handleCallAccepted);
                    break;
                case "CALL_REJECT":
                    SwingUtilities.invokeLater(() -> webRTCHandler.handleCallRejected(msg.getContent()));
                    break;
                case "CALL_END":
                    SwingUtilities.invokeLater(webRTCHandler::handleCallEnded);
                    break;
                case "OFFER":
                    SwingUtilities.invokeLater(() -> webRTCHandler.handleOffer(msg.getSignalingData()));
                    break;
                default:
                    chatArea.append("[System] " + msg.getSender() + ": " + msg.getType() + "\n");
                    break;
            }
        }   
    } 
    catch (Exception e) {
        chatArea.append("Disconnected from server.\n");
    }
}
    

private void receiveFile(Message msg) {
    try {
        File downloads = new File("downloads");
        if (!downloads.exists()) downloads.mkdir();

        File outFile = new File(downloads, msg.getFileName());
        try (FileOutputStream fos = new FileOutputStream(outFile, true)) {
            fos.write(msg.getFileData());
        }

        // ✅ Only display file panel once per file
        if (!shownFiles.contains(msg.getFileName())) {
            shownFiles.add(msg.getFileName());

            JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel fileLabel = new JLabel("📄 " + msg.getFileName());
            JButton openButton = new JButton("Open");
            JButton saveButton = new JButton("Save As");

            openButton.addActionListener(e -> {
                String filePath = outFile.getAbsolutePath();
                String fileName = outFile.getName().toLowerCase();
                
                // Try to open file using multiple approaches
                boolean success = false;
                
                // First attempt: Using Desktop API
                if (!success) {
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            // Wrap in EventQueue.invokeLater to avoid UI freezing
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    Desktop.getDesktop().open(outFile);
                                } catch (Exception ex) {
                                    // Exception caught inside EventQueue
                                }
                            });
                            success = true;
                        }
                    } catch (Exception ex) {
                        // Continue to next approach
                    }
                }
                
                // Second attempt: Using ProcessBuilder for specific file types
                if (!success) {
                    try {
                        ProcessBuilder pb = null;
                        if (fileName.endsWith(".pdf")) {
                            // For PDF files
                            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                                pb = new ProcessBuilder("cmd.exe", "/c", "start", "", filePath);
                            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                                pb = new ProcessBuilder("open", filePath);
                            } else { // Linux and others
                                pb = new ProcessBuilder("xdg-open", filePath);
                            }
                        } else if (fileName.endsWith(".txt") || fileName.endsWith(".log")) {
                            // For text files
                            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                                pb = new ProcessBuilder("notepad.exe", filePath);
                            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                                pb = new ProcessBuilder("open", "-a", "TextEdit", filePath);
                            } else { // Linux and others
                                pb = new ProcessBuilder("xdg-open", filePath);
                            }
                        } else {
                            // For other unknown file types, use system default if possible
                            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                                pb = new ProcessBuilder("cmd.exe", "/c", "start", "", filePath);
                            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                                pb = new ProcessBuilder("open", filePath);
                            } else { // Linux and others
                                pb = new ProcessBuilder("xdg-open", filePath);
                            }
                        }
                        
                        if (pb != null) {
                            pb.start();
                            success = true;
                        }
                    } catch (Exception ex) {
                        // Continue to last approach
                    }
                }
                
                // If all approaches fail, show message with file location
                if (!success) {
                    JOptionPane.showMessageDialog(this,
                        "Unable to open the file automatically.\n" +
                        "Please open it manually from this location:\n" + 
                        filePath,
                        "Open File", JOptionPane.INFORMATION_MESSAGE);
                    
                    // Try to at least open the containing folder
                    try {
                        if (Desktop.isDesktopSupported() && 
                            Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            Desktop.getDesktop().open(outFile.getParentFile());
                        }
                    } catch (Exception ex) {
                        // Silently ignore, we already showed the file path
                    }
                }
            });

            saveButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(msg.getFileName()));
                int result = chooser.showSaveDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    try (InputStream in = new FileInputStream(outFile);
                         OutputStream out = new FileOutputStream(selected)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        
                        // Show success message with file location
                        JOptionPane.showMessageDialog(this, 
                            "File saved successfully to:\n" + selected.getAbsolutePath(),
                            "Save Successful", JOptionPane.INFORMATION_MESSAGE);
                            
                        // Ask if user wants to open the saved file
                        int openResult = JOptionPane.showConfirmDialog(this,
                            "Would you like to open this file now?", 
                            "Open File", JOptionPane.YES_NO_OPTION);
                            
                        if (openResult == JOptionPane.YES_OPTION) {
                            // Try to open the file with multiple approaches
                            boolean opened = false;
                            
                            // First approach: Desktop API with EventQueue to prevent freezing
                            try {
                                if (Desktop.isDesktopSupported() && 
                                    Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            Desktop.getDesktop().open(selected);
                                        } catch (Exception ex) {
                                            // Exception caught inside EventQueue
                                        }
                                    });
                                    opened = true;
                                }
                            } catch (Exception ex) {
                                // Continue to next approach
                            }
                            
                            // Second approach: ProcessBuilder specific to OS and file type
                            if (!opened) {
                                try {
                                    String fileName = selected.getName().toLowerCase();
                                    String filePath = selected.getAbsolutePath();
                                    ProcessBuilder pb = null;
                                    
                                    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                                        pb = new ProcessBuilder("cmd.exe", "/c", "start", "", filePath);
                                    } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                                        pb = new ProcessBuilder("open", filePath);
                                    } else { // Linux and others
                                        pb = new ProcessBuilder("xdg-open", filePath);
                                    }
                                    
                                    if (pb != null) {
                                        pb.start();
                                        opened = true;
                                    }
                                } catch (Exception ex) {
                                    // Failed, continue to showing message
                                }
                            }
                            
                            // If all approaches fail, show message with file location
                            if (!opened) {
                                JOptionPane.showMessageDialog(this,
                                    "Unable to open the file automatically.\n" +
                                    "Please open it manually from this location:\n" + 
                                    selected.getAbsolutePath(),
                                    "Open File", JOptionPane.INFORMATION_MESSAGE);
                                
                                // Try to at least open the containing folder
                                try {
                                    if (Desktop.isDesktopSupported() && 
                                        Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                        Desktop.getDesktop().open(selected.getParentFile());
                                    }
                                } catch (Exception ex) {
                                    // Silently ignore, we already showed the file path
                                }
                            }
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, 
                            "Save failed: " + ex.getMessage() + "\nTry saving to a different location.",
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            filePanel.add(fileLabel);
            filePanel.add(openButton);
            filePanel.add(saveButton);

            getPrivateTextArea(msg.getSender()).append("[File received] " + msg.getFileName() + "\n");

            privateChatDialogs.get(msg.getSender()).add(filePanel, BorderLayout.NORTH);
            privateChatDialogs.get(msg.getSender()).revalidate();
        }
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Failed to receive file: " + e.getMessage());
    }
}



      

private void sendMessage() {
    String text = inputField.getText().trim();
    inputField.setText("");
    if (text.isEmpty()) return;

    try {
        Message msg = new Message(username, "TEXT", text);
        msg.setRoom(currentRoom);

        if (privateRecipient != null) {
            msg.setRecipient(privateRecipient);
        }

        out.writeObject(msg);
        out.flush();
        // Let the server echo it back
    } catch (IOException ex) {
        chatArea.append("Error sending message.\n");
    }
}


private void openPrivateChat(String user) {
    privateRecipient = user;
    currentRoom = "broadcast";

    try {
        Message historyRequest = new Message(username, "SEND_HISTORY", "");
        historyRequest.setRecipient(user);
        out.writeObject(historyRequest);
        out.flush();
    } catch (IOException e) {
        chatArea.append("[Error requesting history with " + user + "]\n");
    }

    if (privateChatDialogs.containsKey(user)) {
        privateChatDialogs.get(user).setVisible(true);
        return;
    }

    JTextArea area = new JTextArea();
    area.setEditable(false);
    privateChats.put(user, area);

    JTextField input = new JTextField();
    JButton sendFileBtn = new JButton("Send File");
    JButton callBtn = new JButton("Call");

    input.addActionListener(e -> {
        String text = input.getText().trim();
        input.setText("");
        if (!text.isEmpty()) {
            try {
                Message msg = new Message(username, "TEXT", text);
                msg.setRecipient(user);
                msg.setRoom("broadcast");
                out.writeObject(msg);
                out.flush();
            } catch (IOException ex) {
                area.append("[Failed to send]\n");
            }
        }
    });

    callBtn.addActionListener(e -> webRTCHandler.initiateCall(user));

    sendFileBtn.addActionListener(e -> {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    Message fileMsg = new Message(username, "FILE", chunk, file.getName(), file.length());
                    fileMsg.setRecipient(user);
                    out.writeObject(fileMsg);
                    out.flush();
                }
                area.append("[File sent] " + file.getName() + "\n");
            } catch (IOException ex) {
                area.append("[Failed to send file]\n");
            }
        }
    });

    JDialog dialog = new JDialog(this, "Chat with " + user);
    dialog.setLayout(new BorderLayout());
    dialog.add(new JScrollPane(area), BorderLayout.CENTER);
    
    // Create a bottom panel for input field and buttons
    JPanel bottomPanel = new JPanel(new BorderLayout());
    
    // Create a panel for the buttons
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttonPanel.add(callBtn);
    buttonPanel.add(sendFileBtn);
    
    // Add input field and button panel to bottom panel
    bottomPanel.add(input, BorderLayout.CENTER);
    bottomPanel.add(buttonPanel, BorderLayout.EAST);
    
    // Add bottom panel to dialog
    dialog.add(bottomPanel, BorderLayout.SOUTH);
    
    dialog.setSize(400, 300);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);

    privateChatDialogs.put(user, dialog);
}


private void openRoomChat(String room) {
    privateRecipient = null;
    currentRoom = room;

    try {
        Message joinMsg = new Message(username, "JOIN", "");
        joinMsg.setRoom(room);
        out.writeObject(joinMsg);
        out.flush();
    } catch (IOException e) {
        chatArea.append("Failed to join room: " + room + "\n");
    }

    if (roomChatDialogs.containsKey(room)) {
        roomChatDialogs.get(room).setVisible(true);
        return;
    }

    JTextArea area = new JTextArea();
    area.setEditable(false);
    roomChats.put(room, area);

    JTextField input = new JTextField();
    JButton callBtn = new JButton("Call Group");

    input.addActionListener(e -> {
        String text = input.getText().trim();
        if (!text.isEmpty()) {
            try {
                Message msg = new Message(username, "TEXT", text);
                msg.setRoom(room);
                out.writeObject(msg);
                out.flush();
                area.append("You: " + text + "\n");
                input.setText("");
            } catch (IOException ex) {
                area.append("[Failed to send]\n");
            }
        }
    });

    callBtn.addActionListener(e -> webRTCHandler.initiateCall(room));

    JDialog dialog = new JDialog(this, "Room: " + room);
    dialog.setLayout(new BorderLayout());
    dialog.add(new JScrollPane(area), BorderLayout.CENTER);
    
    // Create a bottom panel for input field and call button
    JPanel bottomPanel = new JPanel(new BorderLayout());
    
    // Create a panel for the call button
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttonPanel.add(callBtn);
    
    // Add input field and button panel to bottom panel
    bottomPanel.add(input, BorderLayout.CENTER);
    bottomPanel.add(buttonPanel, BorderLayout.EAST);
    
    // Add bottom panel to dialog
    dialog.add(bottomPanel, BorderLayout.SOUTH);
    
    dialog.setSize(400, 300);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);

    roomChatDialogs.put(room, dialog);
}

private JTextArea getPrivateTextArea(String user) {
    if (!privateChats.containsKey(user)) openPrivateChat(user);
    return privateChats.get(user);
}

private JTextArea getRoomTextArea(String room) {
    if (!roomChats.containsKey(room)) openRoomChat(room);
    return roomChats.get(room);
}

private void updateSelectionList(String content) {
    java.util.List<String> users = new ArrayList<>();
    for (String line : content.split("\n")) {
        if (line.startsWith("- ")) users.add(line.substring(2));
    }

    selectionModel.clear();
    selectionModel.addElement("\uD83D\uDCC1 Rooms");
    selectionModel.addElement("- broadcast");
    selectionModel.addElement("- room1");
    selectionModel.addElement("- room2");
    selectionModel.addElement("- room3");
    selectionModel.addElement("\uD83D\uDC65 Users");
    for (String user : users) {
        selectionModel.addElement("- " + user);
    }
}

private void startUserListUpdater() {
    Timer timer = new Timer(1000, e -> {
        try {
            if (out == null) {
                System.err.println("❌ Output stream (out) is null.");
                return;
            }
            if (username == null) {
                System.err.println("❌ Username is null.");
                return;
            }

            Message ping = new Message(username, "GET_USERS", "");
            out.writeObject(ping);
            out.flush();
        } catch (IOException ex) {
            System.err.println("❌ Failed to send GET_USERS: " + ex.getMessage());
        } catch (Exception ex) {
            System.err.println("❌ Unexpected error in user updater: " + ex.getMessage());
            ex.printStackTrace();
        }
    });
    timer.start();
}

public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new SecureChatGUI().setVisible(true));
}
}


