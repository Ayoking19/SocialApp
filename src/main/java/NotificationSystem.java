package main.java;

import java.sql.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter;

public class NotificationSystem {

    // [THE CLOUD BRIDGE]: This is where your Firebase Server Key goes!
    private static final String FIREBASE_SERVER_KEY = "YOUR_FIREBASE_API_KEY_HERE"; 

    /* ========================================= */
    /* --- METHOD 1: ASYNCHRONOUS NOTIFICATION --- */
    /* ========================================= */
    public static void createNotification(String recipient, String actor, String type, int postId) {
        if (recipient.equals(actor)) return; 

        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            String sql = "INSERT INTO notifications (recipient_user, actor_user, type, post_id) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = DatabaseManager.connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, recipient);
                pstmt.setString(2, actor);
                pstmt.setString(3, type);
                pstmt.setInt(4, postId);
                pstmt.executeUpdate();

                // [THE PUSH ENGINE]: Fetch the target's device token and fire the push alert!
                String tokenSql = "SELECT fcm_token FROM users WHERE username = ?";
                try (PreparedStatement tokenStmt = conn.prepareStatement(tokenSql)) {
                    tokenStmt.setString(1, recipient);
                    ResultSet rs = tokenStmt.executeQuery();
                    
                    if (rs.next()) {
                        String deviceToken = rs.getString("fcm_token");
                        if (deviceToken != null && !deviceToken.trim().isEmpty()) {
                            
                            // Format the notification text based on the action
                            String pushTitle = "New Alert";
                            String pushBody = actor + " interacted with you.";
                            
                            if (type.equals("LIKE")) { pushTitle = "New Like"; pushBody = actor + " liked your post."; }
                            else if (type.equals("COMMENT")) { pushTitle = "New Comment"; pushBody = actor + " commented on your post."; }
                            else if (type.equals("FOLLOW")) { pushTitle = "New Follower"; pushBody = actor + " started following you."; }
                            else if (type.contains("MESSAGE")) { pushTitle = actor; pushBody = "Sent you a new message."; }
                            
                            sendFirebasePushNotification(deviceToken, pushTitle, pushBody);
                        }
                    }
                }

            } catch (SQLException e) {
                System.out.println("Notification Error: " + e.getMessage());
            }
        }).start(); 
    }

    /* ========================================= */
    /* --- THE FIREBASE RAW HTTP CLIENT ---      */
    /* ========================================= */
    private static void sendFirebasePushNotification(String deviceToken, String title, String body) {
        if (FIREBASE_SERVER_KEY.equals("YOUR_FIREBASE_API_KEY_HERE")) {
            System.out.println("[FCM Mock] Push to " + deviceToken + " | " + title + ": " + body);
            return; // Skip actual network request if key isn't set yet
        }

        try {
            URL url = new URL("https://fcm.googleapis.com/fcm/send");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "key=" + FIREBASE_SERVER_KEY);
            conn.setRequestProperty("Content-Type", "application/json");

            // Construct the raw JSON payload for Google's Firebase Servers
            String jsonPayload = "{" +
                "\"to\": \"" + deviceToken + "\"," +
                "\"notification\": {" +
                    "\"title\": \"" + title + "\"," +
                    "\"body\": \"" + body + "\"," +
                    "\"sound\": \"default\"," +
                    "\"priority\": \"high\"" +
                "}" +
            "}";

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(jsonPayload);
            wr.flush();
            
            int responseCode = conn.getResponseCode();
            System.out.println("[FCM] Push sent to " + deviceToken + " | Firebase Status: " + responseCode);
            
        } catch (Exception e) {
            System.out.println("[FCM] Error sending push: " + e.getMessage());
        }
    }

    /* ========================================= */
    /* --- DEVICE TOKEN MANAGER ---              */
    /* ========================================= */
    public static void saveDeviceToken(String username, String token) {
        String sql = "UPDATE users SET fcm_token = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, token);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
            System.out.println("[SYSTEM]: Device token registered for user: " + username);
        } catch (SQLException e) { 
            System.out.println("Error saving token: " + e.getMessage()); 
        }
    }

    /* ========================================= */
    /* --- METHOD 2: FETCH NOTIFICATIONS ---     */
    /* ========================================= */
    public static String getNotifications(String username) {
        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT id, actor_user, type, post_id, is_read, created_at AS timestamp FROM notifications WHERE recipient_user = ? ORDER BY created_at DESC LIMIT 50";
        
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"actor\":\"").append(rs.getString("actor_user")).append("\",")
                    .append("\"type\":\"").append(rs.getString("type")).append("\",")
                    .append("\"postId\":").append(rs.getInt("post_id")).append(",")
                    .append("\"isRead\":").append(rs.getBoolean("is_read")).append(",")
                    .append("\"timestamp\":\"").append(rs.getString("timestamp")).append("\"")
                    .append("}");
            }
        } catch (SQLException e) {
            System.out.println("Fetch Notifications Error: " + e.getMessage());
        }
        
        json.append("]");
        return json.toString();
    }

    /* ========================================= */
    /* --- METHOD 3: MARK AS READ ---            */
    /* ========================================= */
    public static void markNotificationsAsRead(String username) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE recipient_user = ? AND is_read = 0";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Clear Notifications Error: " + e.getMessage());
        }
    }
}