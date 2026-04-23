package main.java;

import java.sql.*;

public class NotificationSystem {

    /* [Trigger]: Saves a new alert in the database */
    public static void createNotification(String recipient, String actor, String type, int postId) {
        if (recipient.equals(actor)) return; 

        String sql = "INSERT INTO notifications (recipient_user, actor_user, type, post_id) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, recipient);
            pstmt.setString(2, actor);
            pstmt.setString(3, type);
            pstmt.setInt(4, postId);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.out.println("Notification Save Error: " + e.getMessage());
        }
    }

    /* [Fetch]: Gets the list of alerts for the UI */
    public static String getNotifications(String username) {
        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT * FROM notifications WHERE recipient_user = ? ORDER BY timestamp DESC LIMIT 20";

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

    /* [NEW] [Cleanup]: Marks all unread alerts as read for a specific user */
    public static void markNotificationsAsRead(String username) {
        // [SQL Update]: Changes the value of a column for existing rows
        String sql = "UPDATE notifications SET is_read = 1 WHERE recipient_user = ? AND is_read = 0";
        
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.executeUpdate();
            System.out.println(">>> Notifications cleared for user: " + username);
            
        } catch (SQLException e) {
            System.out.println("Clear Notifications Error: " + e.getMessage());
        }
    }
}