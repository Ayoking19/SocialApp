package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MessageSystem {

    /* --- 1. SEND A MESSAGE (WITH MEDIA & SPAM FILTER) --- */
    public static String sendMessage(String sender, String receiver, String content, String mediaBase64) {
        if (sender.equals(receiver)) return "CANNOT_MESSAGE_SELF";

        String getIdsSQL = "SELECT (SELECT id FROM users WHERE username = ?) AS sender_id, " +
                           "(SELECT id FROM users WHERE username = ?) AS receiver_id";
        
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement idStmt = conn.prepareStatement(getIdsSQL)) {
            
            idStmt.setString(1, sender);
            idStmt.setString(2, receiver);
            ResultSet rsIds = idStmt.executeQuery();
            
            if (rsIds.next()) {
                int senderId = rsIds.getInt("sender_id");
                int receiverId = rsIds.getInt("receiver_id");
                if (senderId == 0 || receiverId == 0) return "USER_NOT_FOUND";

                String checkMutualSQL = "SELECT 1 FROM followers f1 JOIN followers f2 " +
                                        "ON f1.follower_id = f2.following_id AND f1.following_id = f2.follower_id " +
                                        "WHERE f1.follower_id = ? AND f1.following_id = ?";
                boolean isMutual = false;
                try (PreparedStatement mutualStmt = conn.prepareStatement(checkMutualSQL)) {
                    mutualStmt.setInt(1, senderId);
                    mutualStmt.setInt(2, receiverId);
                    isMutual = mutualStmt.executeQuery().next();
                }

                if (!isMutual) {
                    String checkPrevMsgSQL = "SELECT 1 FROM messages WHERE sender_id = ? AND receiver_id = ?";
                    try (PreparedStatement prevMsgStmt = conn.prepareStatement(checkPrevMsgSQL)) {
                        prevMsgStmt.setInt(1, senderId);
                        prevMsgStmt.setInt(2, receiverId);
                        if (prevMsgStmt.executeQuery().next()) {
                            return "SPAM_LIMIT_REACHED"; 
                        }
                    }
                }

                String insertMsgSQL = "INSERT INTO messages (sender_id, receiver_id, content, image_url) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertMsgSQL)) {
                    insertStmt.setInt(1, senderId);
                    insertStmt.setInt(2, receiverId);
                    insertStmt.setString(3, content);
                    insertStmt.setString(4, mediaBase64);
                    insertStmt.executeUpdate();
                    return "SUCCESS";
                }
            }
        } catch (SQLException e) { System.out.println("Message Error: " + e.getMessage()); }
        return "ERROR";
    }

    /* --- 2. GET CONVERSATION HISTORY --- */
    public static String getChatHistory(String currentUser, String targetUser) {
        String markReadSQL = "UPDATE messages SET is_read = 1 WHERE receiver_id = (SELECT id FROM users WHERE username = ?) " +
                             "AND sender_id = (SELECT id FROM users WHERE username = ?)";
        
        String fetchSQL = "SELECT m.id, u.username AS sender, m.content, m.image_url, m.created_at " +
                          "FROM messages m JOIN users u ON m.sender_id = u.id " +
                          "WHERE (m.sender_id = (SELECT id FROM users WHERE username = ?) AND m.receiver_id = (SELECT id FROM users WHERE username = ?)) " +
                          "OR (m.sender_id = (SELECT id FROM users WHERE username = ?) AND m.receiver_id = (SELECT id FROM users WHERE username = ?)) " +
                          "ORDER BY m.created_at ASC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect()) {
            
            try (PreparedStatement readStmt = conn.prepareStatement(markReadSQL)) {
                readStmt.setString(1, currentUser);
                readStmt.setString(2, targetUser);
                readStmt.executeUpdate();
            }

            try (PreparedStatement fetchStmt = conn.prepareStatement(fetchSQL)) {
                fetchStmt.setString(1, currentUser);
                fetchStmt.setString(2, targetUser);
                fetchStmt.setString(3, targetUser);
                fetchStmt.setString(4, currentUser);
                ResultSet rs = fetchStmt.executeQuery();
                
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    String text = rs.getString("content"); if (text == null) text = "";
                    String img = rs.getString("image_url"); if (img == null) img = "";

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"sender\":\"").append(rs.getString("sender")).append("\",")
                        .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                        .append("\"media\":\"").append(img).append("\",")
                        .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                        .append("}");
                }
            }
        } catch (Exception e) { System.out.println("Chat History Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    /* --- 3. GET INBOX (ACTIVE CHATS) --- */
    public static String getInbox(String currentUser) {
        // [UI UPGRADE]: Changing the preview text from [Image] to [Attachment]
        String inboxSQL = "SELECT u.username, u.profile_pic_url, " +
                          "(SELECT CASE WHEN content IS NOT NULL AND trim(content) != '' THEN content WHEN image_url IS NOT NULL AND trim(image_url) != '' THEN '[Attachment]' ELSE '' END FROM messages WHERE (sender_id = u.id AND receiver_id = me.id) OR (sender_id = me.id AND receiver_id = u.id) ORDER BY created_at DESC LIMIT 1) as last_message, " +
                          "(SELECT created_at FROM messages WHERE (sender_id = u.id AND receiver_id = me.id) OR (sender_id = me.id AND receiver_id = u.id) ORDER BY created_at DESC LIMIT 1) as last_time, " +
                          "(SELECT COUNT(*) FROM messages WHERE sender_id = u.id AND receiver_id = me.id AND is_read = 0) as unread_count " +
                          "FROM users u " +
                          "JOIN users me ON me.username = ? " +
                          "WHERE u.id IN (SELECT sender_id FROM messages WHERE receiver_id = me.id UNION SELECT receiver_id FROM messages WHERE sender_id = me.id) " +
                          "ORDER BY last_time DESC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(inboxSQL)) {
            stmt.setString(1, currentUser);
            ResultSet rs = stmt.executeQuery();
            
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String avatar = rs.getString("profile_pic_url");
                if (avatar == null || avatar.trim().isEmpty()) avatar = "https://ui-avatars.com/api/?name=" + rs.getString("username") + "&background=1e293b&color=00e676";
                String lastMessage = rs.getString("last_message");
                if (lastMessage == null) lastMessage = "";
                
                json.append("{")
                    .append("\"username\":\"").append(rs.getString("username")).append("\",")
                    .append("\"avatar\":\"").append(avatar).append("\",")
                    .append("\"lastMessage\":\"").append(lastMessage.replace("\"", "\\\"").replace("\n", " ").replace("\r", "")).append("\",")
                    .append("\"timestamp\":\"").append(rs.getString("last_time")).append("\",")
                    .append("\"unreadCount\":").append(rs.getInt("unread_count"))
                    .append("}");
            }
        } catch (Exception e) { System.out.println("Inbox Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    /* --- 4. GLOBAL UNREAD MESSAGES COUNT --- */
    public static String getGlobalUnreadCount(String currentUser) {
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = (SELECT id FROM users WHERE username = ?) AND is_read = 0";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentUser);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return "{\"unread\":" + rs.getInt(1) + "}";
        } catch (Exception e) { System.out.println("Unread Count Error: " + e.getMessage()); }
        return "{\"unread\":0}";
    }
}