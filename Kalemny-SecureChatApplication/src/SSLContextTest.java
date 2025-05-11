/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */


import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SSLContextTest {
    public static void main(String[] args) {
        try {
            // Load the keystore manually
            String keystorePath = "E:/uni files/Year 3/Semester 2/Network Programming/Project/SecureChatApp/resources/keystore.jks";
            char[] password = "123456".toCharArray();

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(keystorePath), password);

            // Init KeyManager with the keystore
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);

            // Create SSL context manually and initialize it
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            System.out.println("SSLContext created manually: " + sslContext.getProtocol());

        } catch (Exception e) {
            System.out.println("Manual SSLContext creation failed: " + e.getMessage());
        }
    }
}