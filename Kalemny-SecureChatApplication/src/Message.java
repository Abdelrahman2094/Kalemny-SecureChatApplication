/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */


import java.io.Serializable;

public class Message implements Serializable {
    private String sender;
    private String room;                // Optional: for group/room-based messages
    private String type;                // MessageType constant as String
    private String content;             // Text content
    private String recipient;
    private String signalingData;
    private String fileName;   // Add this
    private long fileSize;     // Add this
    
    
public void setFileData(byte[] fileData) {
    this.fileData = fileData;
}

public Message(String sender, String type, byte[] fileData, String fileName, long fileSize) {
    this.sender = sender;
    this.type = type;
    this.fileData = fileData;
    this.fileName = fileName;
    this.fileSize = fileSize;
}

    
    public String getFileName() {
    return fileName;
}
public void setFileName(String fileName) {
    this.fileName = fileName;
}

public long getFileSize() {
    return fileSize;
}
public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
}

    public void setContent(String content) {
    this.content = content;
}

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setSignalingData(String signalingData) {
        this.signalingData = signalingData;
    }

    public String getSignalingData() {
        return signalingData;
    }
    private byte[] fileData;            // For file transfers

    // Constructor for text-based message
    public Message(String sender, String type, String content) {
        this.sender = sender;
        this.type = type;
        this.content = content;
    }

    // Constructor for file-based message
    public Message(String sender, String type, byte[] fileData) {
        this.sender = sender;
        this.type = type;
        this.fileData = fileData;
    }

    // Getters and Setters
    public String getSender() { return sender; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public byte[] getFileData() { return fileData; }
    public String getRoom() { return room; }

    public void setRoom(String room) {
        this.room = room;
    }
}
