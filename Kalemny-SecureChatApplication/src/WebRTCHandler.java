/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */



import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;


public class WebRTCHandler {
    private final String username;
    private final ObjectOutputStream out;
    private String remotePeer;
    private boolean isInCall = false;
    private JFrame statusFrame;
    private String currentRoomId;
    private static final Map<String, String> activeRooms = new HashMap<>();
    
    /**
     * Constructor for WebRTCHandler
     */
    public WebRTCHandler(String username, ObjectOutputStream out) {
        this.username = username;
        this.out = out;
    }
    
    /**
     * Initiate a call to another user
     */
    public void initiateCall(String remotePeer) {
        if (isInCall) {
            JOptionPane.showMessageDialog(null, 
                "You're already in a call. Please end the current call first.",
                "Call in Progress", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        try {
            // Send call request message
            Message callRequest = new Message(username, "CALL_REQUEST", "");
            callRequest.setRecipient(remotePeer);
            out.writeObject(callRequest);
            out.flush();
            
            this.remotePeer = remotePeer;
            
            // Show waiting dialog
            JOptionPane.showMessageDialog(null, 
                "Calling " + remotePeer + "...\nWaiting for response.",
                "Calling", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                "Failed to initiate call: " + e.getMessage(),
                "Call Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Handle incoming call request
     */
    public void handleIncomingCall(String caller) {
        if (isInCall) {
            rejectCall(caller, "Already in another call");
            return;
        }
        
        int response = JOptionPane.showConfirmDialog(null, 
            "Incoming call from " + caller + ". Accept?",
            "Incoming Call", JOptionPane.YES_NO_OPTION);
            
        if (response == JOptionPane.YES_OPTION) {
            this.remotePeer = caller;
            acceptCall();
        } else {
            rejectCall(caller, "Call rejected by user");
        }
    }
    
    /**
     * Accept an incoming call
     */
    private void acceptCall() {
        try {
            // Send accept message
            Message acceptMessage = new Message(username, "CALL_ACCEPT", "");
            acceptMessage.setRecipient(remotePeer);
            out.writeObject(acceptMessage);
            out.flush();
            
            // For the receiver, we'll wait for the WebRTC offer before opening the browser
            isInCall = true;
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                "Failed to accept call: " + e.getMessage(),
                "Call Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Reject an incoming call
     */
    private void rejectCall(String caller, String reason) {
        try {
            Message rejectMessage = new Message(username, "CALL_REJECT", reason);
            rejectMessage.setRecipient(caller);
            out.writeObject(rejectMessage);
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send call rejection: " + e.getMessage());
        }
    }
    
    /**
     * Handle call acceptance from remote peer
     */
    public void handleCallAccepted() {
        isInCall = true;
        
        // Generate a room ID based on usernames
        currentRoomId = generateRoomId(username, remotePeer);
        activeRooms.put(username + "_" + remotePeer, currentRoomId);
        
        // Create WebRTC call URL with room ID and send offer
        String webrtcUrl = "https://meet.jit.si/" + currentRoomId;
        
        try {
            // Send offer with room info
            Message offerMessage = new Message(username, "OFFER", "");
            offerMessage.setRecipient(remotePeer);
            offerMessage.setSignalingData(webrtcUrl);
            out.writeObject(offerMessage);
            out.flush();
            
            System.out.println("Created room ID: " + currentRoomId + " for call with " + remotePeer);
            
            // Open the WebRTC application in default browser
            openCallInBrowser(webrtcUrl);
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                "Failed to establish call: " + e.getMessage(),
                "Call Error", JOptionPane.ERROR_MESSAGE);
            endCall();
        }
    }
    
    /**
     * Handle receiving WebRTC offer with room URL
     */
    public void handleOffer(String webrtcUrl) {
        // Extract room ID from URL
        String roomId = webrtcUrl.substring(webrtcUrl.lastIndexOf('/') + 1);
        currentRoomId = roomId;
        activeRooms.put(remotePeer + "_" + username, currentRoomId);
        
        System.out.println("Received room ID: " + currentRoomId + " for call with " + remotePeer);
        
        // Open the WebRTC application in default browser
        openCallInBrowser(webrtcUrl);
    }
    
    /**
     * Open call in system browser and show call status window
     */
    private void openCallInBrowser(String url) {
        try {
            // Open system browser with WebRTC URL
            Desktop.getDesktop().browse(new URI(url));
            System.out.println("Opened browser with URL: " + url);
            
            // Create a small status window to control the call - in EDT
            SwingUtilities.invokeLater(() -> {
                // First close any existing status frame
                if (statusFrame != null) {
                    statusFrame.dispose();
                }
                
                statusFrame = new JFrame("Call with " + remotePeer);
                statusFrame.setSize(300, 100);
                statusFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                
                // Add window close handler
                statusFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent windowEvent) {
                        System.out.println("Call window closing event triggered");
                        endCall();
                    }
                });
                
                // End call button
                JButton endCallButton = new JButton("End Call");
                endCallButton.addActionListener(e -> {
                    System.out.println("End Call button clicked");
                    endCall();
                });
                
                // Status label
                JLabel statusLabel = new JLabel("In call with " + remotePeer);
                statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
                
                // Layout
                statusFrame.setLayout(new BorderLayout());
                statusFrame.add(statusLabel, BorderLayout.CENTER);
                statusFrame.add(endCallButton, BorderLayout.SOUTH);
                
                // Center on screen
                statusFrame.setLocationRelativeTo(null);
                statusFrame.setVisible(true);
                
                System.out.println("Created call status window for room: " + currentRoomId);
            });
            
        } catch (IOException | URISyntaxException e) {
            JOptionPane.showMessageDialog(null, 
                "Failed to open browser for call: " + e.getMessage() + 
                "\nPlease navigate manually to: " + url,
                "Browser Launch Error", JOptionPane.ERROR_MESSAGE);
            
            // Make sure we clean up if browser fails to open
            closeCallSession();
        }
    }
    
    /**
     * Handle call rejection from remote peer
     */
    public void handleCallRejected(String reason) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, 
                "Call rejected: " + reason,
                "Call Rejected", JOptionPane.INFORMATION_MESSAGE);
        });
        
        isInCall = false;
        this.remotePeer = null;
        this.currentRoomId = null;
    }
    
    /**
     * Handle call end from remote peer
     */
    public void handleCallEnded() {
        if (isInCall && remotePeer != null) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, 
                    "Call ended by " + remotePeer,
                    "Call Ended", JOptionPane.INFORMATION_MESSAGE);
                
                // Make sure to close the call session
                closeCallSession();
            });
        } else {
            System.out.println("Received call end message but no active call.");
        }
    }
    
    /**
     * End the current call
     */
    public void endCall() {
        if (isInCall && remotePeer != null) {
            try {
                // Send call end message
                Message endMessage = new Message(username, "CALL_END", "");
                endMessage.setRecipient(remotePeer);
                out.writeObject(endMessage);
                out.flush();
                
                System.out.println("Sent call end message to: " + remotePeer);
            } catch (IOException e) {
                System.err.println("Failed to send call end message: " + e.getMessage());
            }
            
            // First make sure to close the call session
            closeCallSession();
        } else {
            System.out.println("No active call to end.");
        }
    }
    
    /**
     * Close the call session and clean up resources
     */
    private void closeCallSession() {
        if (!isInCall) return;
        
        System.out.println("Closing call session for room: " + currentRoomId);
        
        // Remove active room tracking
        if (currentRoomId != null) {
            activeRooms.remove(username + "_" + remotePeer);
            activeRooms.remove(remotePeer + "_" + username);
        }
        
        // Close status frame on EDT
        if (statusFrame != null) {
            SwingUtilities.invokeLater(() -> {
                statusFrame.dispose();
                statusFrame = null;
            });
        }
        
        isInCall = false;
        remotePeer = null;
        currentRoomId = null;
        
        System.out.println("Call session closed");
    }
    
    /**
     * Generate a consistent room ID from two usernames
     */
    private String generateRoomId(String user1, String user2) {
        // Sort usernames to ensure consistent room ID regardless of who initiates
        String[] users = {user1, user2};
        java.util.Arrays.sort(users);
        
        // Create a room ID with a fixed value instead of timestamp
        // to ensure it's truly consistent for both parties
        return "SecureChat_" + users[0] + "_" + users[1] + "_" + 
               Math.abs((users[0] + users[1]).hashCode() % 10000);
    }
    
    /**
     * Check if currently in a call
     */
    public boolean isInCall() {
        return isInCall;
    }
}