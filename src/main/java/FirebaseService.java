package main.java;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.io.FileInputStream;
import java.io.IOException;

public class FirebaseService {

    /* ========================================= */
    /* --- METHOD 1: SECURE IGNITION ---         */
    /* ========================================= */
    /**
     * Reads your master JSON key and securely logs your Java server into Google's backend.
     * [Authentication Handshake: The process where your server proves its identity to 
     * Google using a cryptographic file before being allowed to send any network traffic]
     */
    public static void initialize() {
        try {
            // This looks for the key exactly where it sits in your main SocialApp folder
            FileInputStream serviceAccount = new FileInputStream("firebase-key.json");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("SUCCESS: Firebase Admin SDK securely connected!");
            }
        } catch (IOException e) {
            System.out.println("CRITICAL FIREBASE ERROR: Could not read the firebase-key.json file.");
            e.printStackTrace();
        }
    }

    /* ========================================= */
    /* --- METHOD 2: THE NOTIFICATION CANNON --- */
    /* ========================================= */
    /**
     * Constructs a digital payload and fires it to a specific device token.
     * [Payload Construction: The act of packaging a title, body text, and target address 
     * into a standardized JSON format that Apple and Android devices know how to read]
     */
    public static boolean sendPushNotification(String targetToken, String title, String body) {
        try {
            // 1. Build the visual alert that will appear on the phone screen
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // 2. Package the visual alert with the exact routing address (the token)
            Message message = Message.builder()
                    .setNotification(notification)
                    .setToken(targetToken)
                    .build();

            // 3. Fire the payload through Google's servers
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("Notification successfully fired! Firebase Response: " + response);
            return true;

        } catch (Exception e) {
            System.out.println("Failed to send notification: " + e.getMessage());
            return false;
        }
    }
}