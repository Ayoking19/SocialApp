package main.java;

import java.sql.*;

public class NotificationSystem {

    /* ========================================= */
    /* --- METHOD 1: ASYNCHRONOUS NOTIFICATION --- */
    /* ========================================= */
    public static void createNotification(String recipient, String actor, String type, int postId) {
        // Prevent sending a notification to yourself
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
                            String pushTitle = "SocialApp";
                            String pushBody = actor + " interacted with you.";
                            
                            if (type.equals("LIKE")) { pushTitle = "New Like"; pushBody = actor + " liked your post."; }
                            else if (type.equals("COMMENT")) { pushTitle = "New Comment"; pushBody = actor + " commented on your post."; }
                            else if (type.equals("FOLLOW")) { pushTitle = "New Follower"; pushBody = actor + " started following you."; }
                            else if (type.contains("MESSAGE")) { pushTitle = "New Message"; pushBody = actor + " sent you a private message."; }
                            
                            // [THE NEW TRIGGER]: Tap the Node.js Postman on the shoulder!
                            triggerPushNotification(deviceToken, pushTitle, pushBody);
                        }
                    }
                }

            } catch (SQLException e) {
                System.out.println("Notification Error: " + e.getMessage());
            }
        }).start(); 
    }

    /* ========================================= */
    /* --- NODE.JS POSTMAN TRIGGER ---           */
    /* ========================================= */
    public static void triggerPushNotification(String deviceToken, String title, String messageText) {
        try {
            // 1. Build the command exactly as you would type it in the Ubuntu terminal
            String[] command = {
                "node",
                "postman.js",
                deviceToken,
                title,
                messageText 
            };
            
            // 2. Command the Ubuntu operating system to launch the Subprocess
            Runtime.getRuntime().exec(command);
            System.out.println("Postman dispatched to Google for token: " + deviceToken);
            
        } catch (Exception e) {
            System.out.println("Postman failed to launch: " + e.getMessage());
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
        String sql = "SELECT id, actor_user, type, post_id, is_read, timestamp FROM notifications WHERE recipient_user = ? ORDER BY timestamp DESC LIMIT 50";
        
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