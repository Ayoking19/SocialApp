package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MessageSystem {

    private static String escapeJSON(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String sendMessage(String sender, String receiver, String content, String mediaBase64, boolean isForwarded, Integer replyToId) {
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

                String checkMutualSQL = "SELECT 1 FROM followers f1 JOIN followers f2 ON f1.follower_id = f2.following_id AND f1.following_id = f2.follower_id WHERE f1.follower_id = ? AND f1.following_id = ?";
                boolean isMutual = false;
                try (PreparedStatement mutualStmt = conn.prepareStatement(checkMutualSQL)) {
                    mutualStmt.setInt(1, senderId); mutualStmt.setInt(2, receiverId);
                    isMutual = mutualStmt.executeQuery().next();
                }

                if (!isMutual) {
                    String checkPrevMsgSQL = "SELECT 1 FROM messages WHERE sender_id = ? AND receiver_id = ?";
                    try (PreparedStatement prevMsgStmt = conn.prepareStatement(checkPrevMsgSQL)) {
                        prevMsgStmt.setInt(1, senderId); prevMsgStmt.setInt(2, receiverId);
                        if (prevMsgStmt.executeQuery().next()) return "SPAM_LIMIT_REACHED"; 
                    }
                }

                String insertMsgSQL = "INSERT INTO messages (sender_id, receiver_id, content, image_url, is_forwarded, reply_to_id) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertMsgSQL)) {
                    insertStmt.setInt(1, senderId);
                    insertStmt.setInt(2, receiverId);
                    insertStmt.setString(3, content);
                    insertStmt.setString(4, mediaBase64);
                    insertStmt.setBoolean(5, isForwarded);
                    if (replyToId != null) insertStmt.setInt(6, replyToId); else insertStmt.setNull(6, java.sql.Types.INTEGER);
                    insertStmt.executeUpdate();
                    return "SUCCESS";
                }
            }
        } catch (SQLException e) { System.out.println("Message Error: " + e.getMessage()); }
        return "ERROR";
    }

    public static String getChatHistory(String currentUser, String targetUser) {
        String markReadSQL = "UPDATE messages SET is_read = 1 WHERE receiver_id = (SELECT id FROM users WHERE username = ?) AND sender_id = (SELECT id FROM users WHERE username = ?) AND deleted_by_receiver = 0";
        
        // THE FIX: Advanced Subqueries to fetch the Quoted Sender and Content!
        String fetchSQL = "SELECT m.id, u.username AS sender, m.content, m.image_url, m.created_at, m.is_edited, m.is_forwarded, m.reply_to_id, " +
                          "(SELECT u2.username FROM messages m2 JOIN users u2 ON m2.sender_id = u2.id WHERE m2.id = m.reply_to_id) AS reply_sender, " +
                          "(SELECT m2.content FROM messages m2 WHERE m2.id = m.reply_to_id) AS reply_content " +
                          "FROM messages m JOIN users u ON m.sender_id = u.id " +
                          "WHERE ((m.sender_id = (SELECT id FROM users WHERE username = ?) AND m.receiver_id = (SELECT id FROM users WHERE username = ?)) " +
                          "OR (m.sender_id = (SELECT id FROM users WHERE username = ?) AND m.receiver_id = (SELECT id FROM users WHERE username = ?) AND m.deleted_by_receiver = 0)) " +
                          "ORDER BY m.created_at ASC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect()) {
            try (PreparedStatement readStmt = conn.prepareStatement(markReadSQL)) {
                readStmt.setString(1, currentUser); readStmt.setString(2, targetUser); readStmt.executeUpdate();
            }

            try (PreparedStatement fetchStmt = conn.prepareStatement(fetchSQL)) {
                fetchStmt.setString(1, currentUser); fetchStmt.setString(2, targetUser);
                fetchStmt.setString(3, targetUser); fetchStmt.setString(4, currentUser); 
                ResultSet rs = fetchStmt.executeQuery();
                
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    String text = rs.getString("content"); if (text == null) text = "";
                    String img = rs.getString("image_url"); if (img == null) img = "";

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"sender\":\"").append(escapeJSON(rs.getString("sender"))).append("\",")
                        .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                        .append("\"media\":\"").append(escapeJSON(img)).append("\",")
                        .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                        .append("\"isForwarded\":").append(rs.getBoolean("is_forwarded")).append(",")
                        .append("\"replyToId\":").append(rs.getInt("reply_to_id")).append(",")
                        .append("\"replySender\":\"").append(escapeJSON(rs.getString("reply_sender"))).append("\",")
                        .append("\"replyContent\":\"").append(escapeJSON(rs.getString("reply_content"))).append("\",")
                        .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                        .append("}");
                }
            }
        } catch (Exception e) { System.out.println("Chat History Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    public static String getInbox(String currentUser) {
        String inboxSQL = "SELECT u.username, u.profile_pic_url, " +
                          "(SELECT CASE WHEN content IS NOT NULL AND trim(content) != '' THEN content WHEN image_url IS NOT NULL AND trim(image_url) != '' THEN '[Attachment]' ELSE '' END FROM messages WHERE ((sender_id = u.id AND receiver_id = me.id AND deleted_by_receiver = 0) OR (sender_id = me.id AND receiver_id = u.id)) ORDER BY created_at DESC LIMIT 1) as last_message, " +
                          "(SELECT created_at FROM messages WHERE ((sender_id = u.id AND receiver_id = me.id AND deleted_by_receiver = 0) OR (sender_id = me.id AND receiver_id = u.id)) ORDER BY created_at DESC LIMIT 1) as last_time, " +
                          "(SELECT COUNT(*) FROM messages WHERE sender_id = u.id AND receiver_id = me.id AND is_read = 0 AND deleted_by_receiver = 0) as unread_count " +
                          "FROM users u " +
                          "JOIN users me ON me.username = ? " +
                          "WHERE u.id IN (SELECT sender_id FROM messages WHERE receiver_id = me.id AND deleted_by_receiver = 0 UNION SELECT receiver_id FROM messages WHERE sender_id = me.id) " +
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
                    .append("\"username\":\"").append(escapeJSON(rs.getString("username"))).append("\",")
                    .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                    .append("\"lastMessage\":\"").append(escapeJSON(lastMessage)).append("\",")
                    .append("\"timestamp\":\"").append(rs.getString("last_time")).append("\",")
                    .append("\"unreadCount\":").append(rs.getInt("unread_count"))
                    .append("}");
            }
        } catch (Exception e) { System.out.println("Inbox Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    public static String getGlobalUnreadCount(String currentUser) {
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = (SELECT id FROM users WHERE username = ?) AND is_read = 0 AND deleted_by_receiver = 0";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentUser);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return "{\"unread\":" + rs.getInt(1) + "}";
        } catch (Exception e) { System.out.println("Unread Count Error: " + e.getMessage()); }
        return "{\"unread\":0}";
    }

    public static boolean editMessage(String username, int messageId, String newContent) {
        String sql = "UPDATE messages SET content = ?, is_edited = 1 WHERE id = ? AND sender_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newContent);
            pstmt.setInt(2, messageId);
            pstmt.setString(3, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public static boolean deleteMessage(String username, int messageId) {
        // Attempt 1: If you are the sender, completely destroy it for everyone.
        String deleteForEveryone = "DELETE FROM messages WHERE id = ? AND sender_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(deleteForEveryone)) {
            pstmt.setInt(1, messageId);
            pstmt.setString(2, username);
            if (pstmt.executeUpdate() > 0) return true; // Success! It was your message.
        } catch (SQLException e) {}

        // Attempt 2: If the first attempt failed, it means you are the receiver. Just hide it from your screen.
        String deleteForMe = "UPDATE messages SET deleted_by_receiver = 1 WHERE id = ? AND receiver_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(deleteForMe)) {
            pstmt.setInt(1, messageId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
}