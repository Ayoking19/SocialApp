package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MessageSystem {

    public static final String DEFAULT_AVATAR = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9Ii00IC00IDMyIDMyIj48cGF0aCBmaWxsPSIjZmZmZmZmIiBkPSJNMTIgMTJjMi4yMSAwIDQtMS43OSA0LTRzLTEuNzktNC00LTQtNCAxLjc5LTQgNCAxLjc5IDQgNCA0em0wIDJjLTIuNjcgMC04IDEuMzQtOCA0djJoMTZ2LTJjMC0yLjY2LTUuMzMtNC04LTR6Ii8+PC9zdmc+";

    public static void ensureSchema() {
        try (Connection conn = DatabaseManager.connect(); java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS groups (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, avatar TEXT, creator_id INTEGER, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS group_members (group_id INTEGER, user_id INTEGER, is_admin INTEGER DEFAULT 0, joined_at DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(group_id, user_id))");
            try { stmt.execute("ALTER TABLE messages ADD COLUMN group_id INTEGER DEFAULT NULL"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE groups ADD COLUMN avatar TEXT DEFAULT NULL"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE group_members ADD COLUMN is_admin INTEGER DEFAULT 0"); } catch (Exception e) {}
        } catch (Exception e) { System.out.println("Schema Initialization Error: " + e.getMessage()); }
    }

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

    private static void sendSystemMessage(Connection conn, String triggerUser, int groupId, String content) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, group_id, content, is_forwarded) VALUES ((SELECT id FROM users WHERE username = ?), (SELECT id FROM users WHERE username = ?), ?, ?, 0)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, triggerUser);
            stmt.setString(2, triggerUser); 
            stmt.setInt(3, groupId);
            stmt.setString(4, "[SYS]" + content); 
            stmt.executeUpdate();
        } catch (Exception e) { System.out.println("System Message Error: " + e.getMessage()); }
    }

    public static String sendMessage(String sender, String receiver, String content, String mediaBase64, boolean isForwarded, Integer replyToId) {
        ensureSchema();
        if (sender.equals(receiver)) return "CANNOT_MESSAGE_SELF";

        if (content != null && content.startsWith("[SYS]")) {
            content = " " + content;
        }

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

                if (isForwarded) {
                    String checkFollowSQL = "SELECT 1 FROM followers WHERE follower_id = ? AND following_id = ?";
                    try (PreparedStatement followStmt = conn.prepareStatement(checkFollowSQL)) {
                        followStmt.setInt(1, senderId);
                        followStmt.setInt(2, receiverId);
                        if (!followStmt.executeQuery().next()) return "NOT_FOLLOWING";
                    }
                }

                String checkMutualSQL = "SELECT 1 FROM followers f1 JOIN followers f2 ON f1.follower_id = f2.following_id AND f1.following_id = f2.follower_id WHERE f1.follower_id = ? AND f1.following_id = ?";
                boolean isMutual = false;
                try (PreparedStatement mutualStmt = conn.prepareStatement(checkMutualSQL)) {
                    mutualStmt.setInt(1, senderId); mutualStmt.setInt(2, receiverId);
                    isMutual = mutualStmt.executeQuery().next();
                }

                if (!isMutual && !isForwarded) {
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
        ensureSchema();
        String markReadSQL = "UPDATE messages SET is_read = 1 WHERE receiver_id = (SELECT id FROM users WHERE username = ?) AND sender_id = (SELECT id FROM users WHERE username = ?) AND deleted_by_receiver = 0";
        
        String fetchSQL = "SELECT m.id, u.username AS sender, m.content, m.image_url, m.created_at, m.is_edited, m.is_forwarded, m.reply_to_id, " +
                          "(SELECT u2.username FROM messages m2 JOIN users u2 ON m2.sender_id = u2.id WHERE m2.id = m.reply_to_id) AS reply_sender, " +
                          "(SELECT m2.content FROM messages m2 WHERE m2.id = m.reply_to_id) AS reply_content, " +
                          "(SELECT g.name FROM messages m2 JOIN groups g ON m2.group_id = g.id WHERE m2.id = m.reply_to_id) AS reply_group_name, " +
                          "(SELECT g.id FROM messages m2 JOIN groups g ON m2.group_id = g.id WHERE m2.id = m.reply_to_id) AS reply_group_id " +
                          "FROM messages m JOIN users u ON m.sender_id = u.id " +
                          "WHERE ((m.sender_id = (SELECT id FROM users WHERE username = ?) AND m.receiver_id = (SELECT id FROM users WHERE username = ?)) " +
                          "OR (m.sender_id = (SELECT id FROM users WHERE username = ?) AND m.receiver_id = (SELECT id FROM users WHERE username = ?) AND m.deleted_by_receiver = 0)) " +
                          "AND m.group_id IS NULL " +
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

                    int replyGrpId = rs.getInt("reply_group_id");
                    String replyGrpIdStr = rs.wasNull() ? "null" : String.valueOf(replyGrpId);

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
                        .append("\"replyGroupName\":\"").append(escapeJSON(rs.getString("reply_group_name"))).append("\",")
                        .append("\"replyGroupId\":").append(replyGrpIdStr).append(",")
                        .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                        .append("}");
                }
            }
        } catch (Exception e) { System.out.println("Chat History Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    public static String getInbox(String currentUser) {
        ensureSchema();
        String inboxSQL = "SELECT u.username, u.profile_pic_url, " +
                          "(SELECT CASE WHEN content IS NOT NULL AND trim(content) != '' THEN content WHEN image_url IS NOT NULL AND trim(image_url) != '' THEN '[Attachment]' ELSE '' END FROM messages WHERE ((sender_id = u.id AND receiver_id = me.id AND deleted_by_receiver = 0) OR (sender_id = me.id AND receiver_id = u.id)) AND group_id IS NULL ORDER BY created_at DESC LIMIT 1) as last_message, " +
                          "(SELECT created_at FROM messages WHERE ((sender_id = u.id AND receiver_id = me.id AND deleted_by_receiver = 0) OR (sender_id = me.id AND receiver_id = u.id)) AND group_id IS NULL ORDER BY created_at DESC LIMIT 1) as last_time, " +
                          "(SELECT COUNT(*) FROM messages WHERE sender_id = u.id AND receiver_id = me.id AND is_read = 0 AND deleted_by_receiver = 0 AND group_id IS NULL) as unread_count " +
                          "FROM users u " +
                          "JOIN users me ON me.username = ? " +
                          "WHERE u.id IN (SELECT sender_id FROM messages WHERE receiver_id = me.id AND deleted_by_receiver = 0 AND group_id IS NULL UNION SELECT receiver_id FROM messages WHERE sender_id = me.id AND group_id IS NULL) " +
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
                if (avatar == null || avatar.trim().isEmpty()) avatar = DEFAULT_AVATAR;
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
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = (SELECT id FROM users WHERE username = ?) AND is_read = 0 AND deleted_by_receiver = 0 AND group_id IS NULL";
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
        String deleteForEveryone = "DELETE FROM messages WHERE id = ? AND sender_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(deleteForEveryone)) {
            pstmt.setInt(1, messageId);
            pstmt.setString(2, username);
            if (pstmt.executeUpdate() > 0) return true; 
        } catch (SQLException e) {}

        String deleteForMe = "UPDATE messages SET deleted_by_receiver = 1 WHERE id = ? AND receiver_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(deleteForMe)) {
            pstmt.setInt(1, messageId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public static String createGroup(String creator, String groupName, String membersStr) {
        ensureSchema();
        try (Connection conn = DatabaseManager.connect()) {
            String insertGroup = "INSERT INTO groups (name, creator_id) VALUES (?, (SELECT id FROM users WHERE username = ?))";
            int groupId = -1;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertGroup, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, groupName);
                stmt.setString(2, creator);
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) groupId = rs.getInt(1);
            }

            if (groupId == -1) return "FAILURE";

            String insertMember = "INSERT INTO group_members (group_id, user_id, is_admin) VALUES (?, (SELECT id FROM users WHERE username = ?), ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertMember)) {
                stmt.setInt(1, groupId);
                stmt.setString(2, creator);
                stmt.setInt(3, 1); 
                stmt.executeUpdate();

                if (membersStr != null && !membersStr.trim().isEmpty()) {
                    String[] members = membersStr.split(",");
                    for (String member : members) {
                        if (!member.trim().isEmpty() && !member.trim().equals(creator)) {
                            stmt.setInt(1, groupId);
                            stmt.setString(2, member.trim());
                            stmt.setInt(3, 0);
                            stmt.executeUpdate();
                            
                            sendSystemMessage(conn, creator, groupId, creator + " added " + member.trim() + " to the group.");
                        }
                    }
                }
            }
            return "SUCCESS";

        } catch (SQLException e) {
            System.out.println("Create Group Error: " + e.getMessage());
            return "ERROR";
        }
    }

    public static String getUserGroups(String currentUser) {
        ensureSchema();
        String sql = "SELECT g.id, g.name, g.avatar, " +
                     "COALESCE((SELECT created_at FROM messages WHERE group_id = g.id ORDER BY created_at DESC LIMIT 1), g.created_at) as last_time, " +
                     "COALESCE((SELECT CASE WHEN content IS NOT NULL AND trim(content) != '' THEN content WHEN image_url IS NOT NULL AND trim(image_url) != '' THEN '[Attachment]' ELSE '' END FROM messages WHERE group_id = g.id ORDER BY created_at DESC LIMIT 1), 'Group created') as last_message " +
                     "FROM groups g " +
                     "JOIN group_members gm ON g.id = gm.group_id " +
                     "WHERE gm.user_id = (SELECT id FROM users WHERE username = ?) " +
                     "ORDER BY last_time DESC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, currentUser);
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String name = rs.getString("name");
                String avatar = rs.getString("avatar");
                if (avatar == null || avatar.trim().isEmpty()) avatar = DEFAULT_AVATAR;
                
                json.append("{")
                    .append("\"id\":\"").append(rs.getInt("id")).append("\",")
                    .append("\"username\":\"").append(escapeJSON(name)).append("\",") 
                    .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                    .append("\"lastMessage\":\"").append(escapeJSON(rs.getString("last_message"))).append("\",")
                    .append("\"timestamp\":\"").append(rs.getString("last_time")).append("\",")
                    .append("\"unreadCount\":0,")
                    .append("\"isGroup\":true")
                    .append("}");
            }
        } catch (Exception e) { System.out.println("Group Inbox Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    public static String sendGroupMessage(String sender, int groupId, String content, String mediaBase64, Integer replyToId) {
        ensureSchema();
        if (content != null && content.startsWith("[SYS]")) {
            content = " " + content;
        }
        String insertMsgSQL = "INSERT INTO messages (sender_id, receiver_id, group_id, content, image_url, is_forwarded, reply_to_id) VALUES ((SELECT id FROM users WHERE username = ?), (SELECT id FROM users WHERE username = ?), ?, ?, ?, 0, ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement insertStmt = conn.prepareStatement(insertMsgSQL)) {
            insertStmt.setString(1, sender);
            insertStmt.setString(2, sender);
            insertStmt.setInt(3, groupId);
            insertStmt.setString(4, content);
            insertStmt.setString(5, mediaBase64);
            if (replyToId != null) insertStmt.setInt(6, replyToId); else insertStmt.setNull(6, java.sql.Types.INTEGER);
            insertStmt.executeUpdate();
            return "SUCCESS";
        } catch (SQLException e) { System.out.println("Group Message Error: " + e.getMessage()); }
        return "ERROR";
    }

    public static String getGroupChatHistory(String currentUser, int groupId) {
        ensureSchema();
        String fetchSQL = "SELECT m.id, u.username AS sender, m.content, m.image_url, m.created_at, m.is_edited, m.is_forwarded, m.reply_to_id, " +
                          "(SELECT u2.username FROM messages m2 JOIN users u2 ON m2.sender_id = u2.id WHERE m2.id = m.reply_to_id) AS reply_sender, " +
                          "(SELECT m2.content FROM messages m2 WHERE m2.id = m.reply_to_id) AS reply_content, " +
                          "(SELECT g.name FROM messages m2 JOIN groups g ON m2.group_id = g.id WHERE m2.id = m.reply_to_id) AS reply_group_name, " +
                          "(SELECT g.id FROM messages m2 JOIN groups g ON m2.group_id = g.id WHERE m2.id = m.reply_to_id) AS reply_group_id " +
                          "FROM messages m JOIN users u ON m.sender_id = u.id " +
                          "WHERE m.group_id = ? " +
                          "ORDER BY m.created_at ASC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement fetchStmt = conn.prepareStatement(fetchSQL)) {
            fetchStmt.setInt(1, groupId);
            ResultSet rs = fetchStmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String text = rs.getString("content"); if (text == null) text = "";
                String img = rs.getString("image_url"); if (img == null) img = "";

                int replyGrpId = rs.getInt("reply_group_id");
                String replyGrpIdStr = rs.wasNull() ? "null" : String.valueOf(replyGrpId);

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
                    .append("\"replyGroupName\":\"").append(escapeJSON(rs.getString("reply_group_name"))).append("\",")
                    .append("\"replyGroupId\":").append(replyGrpIdStr).append(",")
                    .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                    .append("}");
            }
        } catch (Exception e) { System.out.println("Group History Error: " + e.getMessage()); }
        json.append("]"); return json.toString();
    }

    public static String getChatMedia(String currentUser, String targetUser, Integer groupId) {
        ensureSchema();
        String sql = groupId != null 
            ? "SELECT id, image_url, (SELECT username FROM users WHERE id = sender_id) as sender FROM messages WHERE group_id = ? AND image_url IS NOT NULL AND image_url != '' ORDER BY created_at DESC"
            : "SELECT id, image_url, (SELECT username FROM users WHERE id = sender_id) as sender FROM messages WHERE ((sender_id = (SELECT id FROM users WHERE username = ?) AND receiver_id = (SELECT id FROM users WHERE username = ?)) OR (sender_id = (SELECT id FROM users WHERE username = ?) AND receiver_id = (SELECT id FROM users WHERE username = ?))) AND group_id IS NULL AND image_url IS NOT NULL AND image_url != '' ORDER BY created_at DESC";

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (groupId != null) {
                stmt.setInt(1, groupId);
            } else {
                stmt.setString(1, currentUser); stmt.setString(2, targetUser);
                stmt.setString(3, targetUser); stmt.setString(4, currentUser);
            }
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"sender\":\"").append(escapeJSON(rs.getString("sender"))).append("\",")
                    .append("\"media\":\"").append(escapeJSON(rs.getString("image_url"))).append("\"")
                    .append("}");
            }
        } catch (Exception e) {}
        json.append("]"); return json.toString();
    }

    public static String getGroupInfo(String currentUser, int groupId) {
        ensureSchema();
        String groupSQL = "SELECT name, avatar FROM groups WHERE id = ?";
        String membersSQL = "SELECT u.username, u.profile_pic_url, gm.is_admin FROM group_members gm JOIN users u ON gm.user_id = u.id WHERE gm.group_id = ? ORDER BY gm.is_admin DESC, u.username ASC";

        StringBuilder json = new StringBuilder("{");
        try (Connection conn = DatabaseManager.connect()) {
            boolean isCurrentUserAdmin = false;
            int adminCount = 0;
            
            try (PreparedStatement stmt = conn.prepareStatement(groupSQL)) {
                stmt.setInt(1, groupId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String av = rs.getString("avatar"); if (av == null || av.isEmpty()) av = DEFAULT_AVATAR;
                    json.append("\"name\":\"").append(escapeJSON(rs.getString("name"))).append("\",")
                        .append("\"avatar\":\"").append(escapeJSON(av)).append("\",");
                }
            }

            StringBuilder membersJson = new StringBuilder("[");
            try (PreparedStatement stmt = conn.prepareStatement(membersSQL)) {
                stmt.setInt(1, groupId);
                ResultSet rs = stmt.executeQuery();
                boolean first = true;
                while (rs.next()) {
                    String uName = rs.getString("username");
                    int isAd = rs.getInt("is_admin");
                    if (uName.equals(currentUser) && isAd == 1) isCurrentUserAdmin = true;
                    if (isAd == 1) adminCount++;
                    
                    if (!first) membersJson.append(",");
                    first = false;
                    String uAv = rs.getString("profile_pic_url"); if (uAv == null || uAv.isEmpty()) uAv = DEFAULT_AVATAR;
                    
                    membersJson.append("{")
                        .append("\"username\":\"").append(escapeJSON(uName)).append("\",")
                        .append("\"avatar\":\"").append(escapeJSON(uAv)).append("\",")
                        .append("\"isAdmin\":").append(isAd == 1)
                        .append("}");
                }
            }
            membersJson.append("]");
            
            json.append("\"isAdmin\":").append(isCurrentUserAdmin).append(",")
                .append("\"adminsCount\":").append(adminCount).append(",")
                .append("\"members\":").append(membersJson.toString());

        } catch (Exception e) {}
        json.append("}"); return json.toString();
    }

    public static String updateGroupInfo(String currentUser, int groupId, String newName, String newAvatar) {
        if (newName != null && newName.trim().isEmpty()) newName = null;
        if (newAvatar != null && newAvatar.trim().isEmpty()) newAvatar = null;

        String sql = "UPDATE groups SET name = COALESCE(?, name), avatar = COALESCE(?, avatar) WHERE id = ? AND EXISTS (SELECT 1 FROM group_members WHERE group_id = ? AND user_id = (SELECT id FROM users WHERE username = ?) AND is_admin = 1)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, newAvatar);
            stmt.setInt(3, groupId);
            stmt.setInt(4, groupId);
            stmt.setString(5, currentUser);
            if (stmt.executeUpdate() > 0) return "SUCCESS";
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String manageGroupMember(String currentUser, int groupId, String action, String targetUser) {
        String checkAdmin = "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = (SELECT id FROM users WHERE username = ?) AND is_admin = 1";
        try (Connection conn = DatabaseManager.connect()) {
            boolean isAdmin = false;
            try (PreparedStatement stmt = conn.prepareStatement(checkAdmin)) {
                stmt.setInt(1, groupId); stmt.setString(2, currentUser);
                if (stmt.executeQuery().next()) isAdmin = true;
            }
            if (!isAdmin) return "UNAUTHORIZED";

            String sql = "";
            if (action.equals("ADD")) {
                sql = "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, (SELECT id FROM users WHERE username = ?))";
                sendSystemMessage(conn, currentUser, groupId, currentUser + " added " + targetUser + " to the group.");
            } else if (action.equals("REMOVE")) {
                sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
                sendSystemMessage(conn, currentUser, groupId, currentUser + " removed " + targetUser + " from the group.");
            } else if (action.equals("PROMOTE")) {
                sql = "UPDATE group_members SET is_admin = 1 WHERE group_id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
                sendSystemMessage(conn, currentUser, groupId, currentUser + " made " + targetUser + " an admin.");
            } else if (action.equals("DISMISS")) {
                sql = "UPDATE group_members SET is_admin = 0 WHERE group_id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
                sendSystemMessage(conn, currentUser, groupId, currentUser + " dismissed " + targetUser + " as admin.");
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, groupId); stmt.setString(2, targetUser);
                stmt.executeUpdate();
                return "SUCCESS";
            }
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String leaveGroup(String currentUser, int groupId, boolean deleteEntireGroup) {
        try (Connection conn = DatabaseManager.connect()) {
            if (deleteEntireGroup) {
                conn.createStatement().execute("DELETE FROM messages WHERE group_id = " + groupId);
                conn.createStatement().execute("DELETE FROM group_members WHERE group_id = " + groupId);
                conn.createStatement().execute("DELETE FROM groups WHERE id = " + groupId);
            } else {
                String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, groupId); stmt.setString(2, currentUser); stmt.executeUpdate();
                }
                sendSystemMessage(conn, currentUser, groupId, currentUser + " left the group.");
            }
            return "SUCCESS";
        } catch(Exception e) { return "ERROR"; }
    }
}