# Kalemny - Secure Chat Application

Kalemny is a secure, multi-feature Java-based chat application designed for encrypted real-time communication between multiple users using SSL and WebRTC.

## 📌 Features

- 🔐 **End-to-End Encrypted Chat** using SSL sockets
- 💬 **Private and Group Messaging**
- 📁 **File Transfer Support**
- 📞 **Audio/Video Calls** via WebRTC
- 😊 **Emoji Support**
- 💾 **Chat History Logging**
- 👥 **User Authentication**

## 🗂️ Project Structure

```
Kalemny-SecureChatApplication/
├── src/
│   ├── ClientHandler.java
│   ├── SecureChatServer.java
│   ├── SecureChatClient.java
│   ├── SecureChatGUI.java
│   ├── WebRTCHandler.java
│   ├── SSLContextTest.java
│   ├── Message.java
│   ├── MessageType.java
│   └── EmojiMapper.java
├── resources/
│   └── keystore.jks
├── downloads/
│   └── [Documentation Files]
├── build.xml
└── manifest.mf
```

## 🛠️ Setup & Running

### Prerequisites

- Java 11 or higher
- NetBeans (recommended) or any Java IDE
- SSL certificate (`keystore.jks` provided)

### Running the Application

1. **Compile the project** via `build.xml` using NetBeans or Ant.
2. **Run the `SecureChatServer.java`** on the host machine:
   ```bash
   java SecureChatServer
   ```
3. **Launch multiple instances of `SecureChatClient.java`** for testing:
   ```bash
   java SecureChatClient
   ```

## 🔐 SSL Configuration

- The `SSLContextTest.java` sets up TLS encryption using the provided keystore.
- Make sure the keystore path and password are configured correctly in the code.

## 📞 WebRTC Integration

- WebRTC support is implemented in `WebRTCHandler.java`
- Uses external browser-based client to initiate calls.

## 🧪 Testing

- Use `SecureChatApplicationTest.java` under `securechatapplicationtest` for unit tests.

## 📄 Documentation

All related documentation is available in the `downloads/` folder, including:
- Project Proposal
- Final Report
- Related Research Papers

## 👨‍💻 Contributors

- Yassin Essam  
- [Add others if needed]
