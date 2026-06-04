package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PostSystem {

    private static final String TARGET_ID = "(CASE WHEN (posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = '') AND posts.parent_post_id IS NOT NULL THEN posts.parent_post_id ELSE posts.id END)";

    /* --- THE FIX: The Bulletproof Universal JSON Sanitizer --- */
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /* ========================================= */
    /* --- CRUD: CREATE POSTS & QUOTES ---       */
    /* ========================================= */
    
    public static boolean createPost(String identifier, String content, String mediaBase64) {
        return createPost(identifier, content, mediaBase64, null, null);
    }

    public static boolean createPost(String identifier, String content, String mediaBase64, Integer parentPostId) {
        return createPost(identifier, content, mediaBase64, parentPostId, null);
    }

    public static boolean createPost(String identifier, String content, String mediaBase64, Integer parentPostId, Integer parentCommentId) {
        String findUserSQL = "SELECT id FROM users WHERE username = ? OR email = ?";
        String insertPostSQL = "INSERT INTO posts(user_id, content, image_url, parent_post_id, parent_comment_id) VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {

            findUserStmt.setString(1, identifier);
            findUserStmt.setString(2, identifier);
            ResultSet rs = findUserStmt.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("id");
                Integer finalParentId = parentPostId;
                boolean isNewPostQuote = ((content != null && !content.trim().isEmpty()) || (mediaBase64 != null && !mediaBase64.trim().isEmpty()));

                if (parentCommentId != null) {
                    String commentQuery = "SELECT users.username, comments.post_id FROM comments JOIN users ON comments.user_id = users.id WHERE comments.id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(commentQuery)) {
                        stmt.setInt(1, parentCommentId);
                        ResultSet rsComm = stmt.executeQuery();
                        if (rsComm.next()) {
                            String targetUsername = rsComm.getString("username");
                            int originalPostId = rsComm.getInt("post_id");
                            if (!targetUsername.equals(identifier) && !isNewPostQuote) {
                                NotificationSystem.createNotification(targetUsername, identifier, "REPOST", originalPostId);
                            }
                        }
                    }
                } 
                else if (parentPostId != null) {
                    String targetUserQuery = "SELECT users.username, posts.content, posts.parent_post_id FROM posts JOIN users ON posts.user_id = users.id WHERE posts.id = ?";
                    try (PreparedStatement targetStmt = conn.prepareStatement(targetUserQuery)) {
                        targetStmt.setInt(1, parentPostId);
                        ResultSet rsTarget = targetStmt.executeQuery();
                        if (rsTarget.next()) {
                            String targetUsername = rsTarget.getString("username");
                            String targetContent = rsTarget.getString("content");
                            int targetParentId = rsTarget.getInt("parent_post_id");
                            boolean hasTargetParent = !rsTarget.wasNull();

                            if ((targetContent == null || targetContent.trim().isEmpty()) && hasTargetParent) {
                                finalParentId = targetParentId;
                                if (!targetUsername.equals(identifier) && !isNewPostQuote) NotificationSystem.createNotification(targetUsername, identifier, "REPOST_REPOST", parentPostId);
                                String originalQuery = "SELECT users.username FROM posts JOIN users ON posts.user_id = users.id WHERE posts.id = ?";
                                try (PreparedStatement origStmt = conn.prepareStatement(originalQuery)) {
                                    origStmt.setInt(1, targetParentId);
                                    ResultSet rsOrig = origStmt.executeQuery();
                                    if (rsOrig.next() && !rsOrig.getString("username").equals(identifier) && !isNewPostQuote) {
                                        NotificationSystem.createNotification(rsOrig.getString("username"), identifier, "REPOST", targetParentId);
                                    }
                                }
                            } else {
                                if (!targetUsername.equals(identifier) && !isNewPostQuote) NotificationSystem.createNotification(targetUsername, identifier, "REPOST", parentPostId);
                            }
                        }
                    }
                }

                if (!isNewPostQuote && (finalParentId != null || parentCommentId != null)) {
                    String checkExistSQL = "SELECT id FROM posts WHERE user_id = ? AND " + (finalParentId != null ? "parent_post_id = ?" : "parent_post_id IS NULL") + " AND " + (parentCommentId != null ? "parent_comment_id = ?" : "parent_comment_id IS NULL") + " AND (content IS NULL OR trim(content) = '') AND (image_url IS NULL OR trim(image_url) = '')";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkExistSQL)) {
                        checkStmt.setInt(1, userId);
                        int paramIndex = 2;
                        if (finalParentId != null) checkStmt.setInt(paramIndex++, finalParentId);
                        if (parentCommentId != null) checkStmt.setInt(paramIndex, parentCommentId);
                        ResultSet rsExist = checkStmt.executeQuery();
                        if (rsExist.next()) {
                            try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM posts WHERE id = ?")) { delStmt.setInt(1, rsExist.getInt("id")); delStmt.executeUpdate(); }
                            return true; 
                        }
                    }
                }

                try (PreparedStatement insertPostStmt = conn.prepareStatement(insertPostSQL, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insertPostStmt.setInt(1, userId);
                    insertPostStmt.setString(2, content);
                    insertPostStmt.setString(3, mediaBase64); 
                    if (finalParentId != null) insertPostStmt.setInt(4, finalParentId); else insertPostStmt.setNull(4, java.sql.Types.INTEGER);
                    if (parentCommentId != null) insertPostStmt.setInt(5, parentCommentId); else insertPostStmt.setNull(5, java.sql.Types.INTEGER);
                    insertPostStmt.executeUpdate();

                    try (ResultSet generatedKeys = insertPostStmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int newPostId = generatedKeys.getInt(1);
                            
                            if (isNewPostQuote && parentCommentId != null) {
                                String qUserSql = "SELECT users.username FROM comments JOIN users ON comments.user_id = users.id WHERE comments.id = ?";
                                try (PreparedStatement qStmt = conn.prepareStatement(qUserSql)) {
                                    qStmt.setInt(1, parentCommentId);
                                    ResultSet qRs = qStmt.executeQuery();
                                    if (qRs.next() && !qRs.getString("username").equals(identifier)) NotificationSystem.createNotification(qRs.getString("username"), identifier, "QUOTE_COMMENT", newPostId);
                                }
                            } else if (isNewPostQuote && finalParentId != null) {
                                String qUserSql = "SELECT users.username FROM posts JOIN users ON posts.user_id = users.id WHERE posts.id = ?";
                                try (PreparedStatement qStmt = conn.prepareStatement(qUserSql)) {
                                    qStmt.setInt(1, finalParentId);
                                    ResultSet qRs = qStmt.executeQuery();
                                    if (qRs.next() && !qRs.getString("username").equals(identifier)) NotificationSystem.createNotification(qRs.getString("username"), identifier, "QUOTE_POST", newPostId);
                                }
                            }

                            if (content != null) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@(\\w+)").matcher(content);
                                while (matcher.find()) {
                                    String mentionedUser = matcher.group(1);
                                    if (!mentionedUser.equals(identifier)) {
                                        NotificationSystem.createNotification(mentionedUser, identifier, "MENTION", newPostId);
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }
            return false; 
        } catch (SQLException e) { System.out.println("Error creating post: " + e.getMessage()); return false; }
    }

    /* ========================================= */
    /* --- CRUD: UPDATE & DELETE POSTS ---       */
    /* ========================================= */
    
    public static boolean editPost(String username, int postId, String newContent) {
        String sql = "UPDATE posts SET content = ?, is_edited = 1 WHERE id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newContent);
            pstmt.setInt(2, postId);
            pstmt.setString(3, username);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { System.out.println("Error editing post: " + e.getMessage()); return false; }
    }

    public static boolean deletePost(String username, int postId) {
        String sql = "DELETE FROM posts WHERE id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, postId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { System.out.println("Error deleting post: " + e.getMessage()); return false; }
    }

    /* ========================================= */
    /* --- FEED & SEARCH UPGRADES (OPTIMIZED)--- */
    /* ========================================= */

    private static String buildStatColumns(String currentUser, boolean forceIsFollowing) {
        String safeUser = currentUser.replace("'", "''");
        String IS_PURE_REPOST = "((posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = ''))";
        String followingStr = forceIsFollowing ? "1 AS is_following, " : "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND following_id = posts.user_id) AS is_following, ";

        // THE FIX: Creating a unique string explicitly for pr guarantees the child evaluates its own purity, perfectly restoring the blue glow to the parent!
        String IS_PURE_PR = "((pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = ''))";

        return "(CASE WHEN " + IS_PURE_REPOST + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = posts.parent_comment_id) ELSE (SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") END) AS like_count, " +
               "(CASE WHEN " + IS_PURE_REPOST + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM comments c2 WHERE c2.parent_comment_id = posts.parent_comment_id) ELSE (SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") END) AS comment_count, " +
               "(CASE WHEN " + IS_PURE_REPOST + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = posts.parent_comment_id) ELSE (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) END) AS repost_count, " +
               "(CASE WHEN " + IS_PURE_REPOST + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = posts.parent_comment_id AND NOT " + IS_PURE_REPOST + ") ELSE (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND NOT " + IS_PURE_REPOST + ") END) AS quote_count, " +
               followingStr +
               "(CASE WHEN " + IS_PURE_REPOST + " AND posts.parent_comment_id IS NOT NULL THEN EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND comment_id = posts.parent_comment_id) ELSE EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND post_id = " + TARGET_ID + ") END) AS is_liked, " +
               "(CASE WHEN " + IS_PURE_REPOST + " AND posts.parent_comment_id IS NOT NULL THEN EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_comment_id = posts.parent_comment_id AND pr.user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND " + IS_PURE_PR + ") ELSE EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND " + IS_PURE_PR + ") END) AS is_reposted, " +
               "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
               "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.image_url AS pc_media, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id ";
    }

    public static String getFeed(String currentUser, int pageNumber) {
        int limit = 15; int offset = (pageNumber - 1) * limit; 
        String safeUser = currentUser.replace("'", "''");
        String blockFilter = "WHERE user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";

        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, false) +
                          "FROM (SELECT * FROM posts " + blockFilter + "ORDER BY created_at DESC LIMIT " + limit + " OFFSET " + offset + ") AS posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id ORDER BY posts.created_at DESC";
        return executeStandardPostQuery(querySQL, currentUser, null, false);
    }

    public static String getFollowingFeed(String currentUser, int pageNumber) {
        int limit = 15; int offset = (pageNumber - 1) * limit; 
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, true) +
                          "FROM (SELECT posts.* FROM posts JOIN followers ON posts.user_id = followers.following_id WHERE followers.follower_id = (SELECT id FROM users WHERE username = ?) ORDER BY posts.created_at DESC LIMIT " + limit + " OFFSET " + offset + ") AS posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id ORDER BY posts.created_at DESC";
        return executeStandardPostQuery(querySQL, currentUser, currentUser, false);
    }

    public static String getTopPosts(String currentUser, String timeFilter, String customDate) {
        String safeUser = currentUser.replace("'", "''");
        String blockFilter = " AND posts.user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND posts.user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";
        String timeCondition = "1=1"; String queryParam = null;
        if ("today".equals(timeFilter)) timeCondition = "date(posts.created_at) = date('now')";
        else if ("week".equals(timeFilter)) timeCondition = "posts.created_at >= datetime('now', '-7 days')";
        else if ("month".equals(timeFilter)) timeCondition = "strftime('%Y-%m', posts.created_at) = strftime('%Y-%m', 'now')";
        else if ("custom_day".equals(timeFilter)) { timeCondition = "date(posts.created_at) = ?"; queryParam = customDate; }
        else if ("custom_month".equals(timeFilter)) { timeCondition = "strftime('%Y-%m', posts.created_at) = ?"; queryParam = customDate; }
        else if ("custom_year".equals(timeFilter)) { timeCondition = "strftime('%Y', posts.created_at) = ?"; queryParam = customDate; }

        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, false) +
                          "FROM posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE " + timeCondition + blockFilter + " AND NOT (posts.parent_post_id IS NOT NULL AND (posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = '')) ORDER BY (like_count + comment_count + repost_count) DESC, posts.created_at DESC LIMIT 50";
        return executeStandardPostQuery(querySQL, currentUser, queryParam, false);
    }

    public static String getUserPosts(String targetUsername, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, false) +
                          "FROM posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id WHERE users.username = ? ORDER BY posts.created_at DESC";
        return executeStandardPostQuery(querySQL, currentUser, targetUsername, false);
    }

    public static String getUserQuotes(String targetUsername, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, false) +
                          "FROM posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE users.username = ? AND (posts.parent_post_id IS NOT NULL OR posts.parent_comment_id IS NOT NULL) AND ((posts.content IS NOT NULL AND trim(posts.content) != '') OR (posts.image_url IS NOT NULL AND trim(posts.image_url) != '')) ORDER BY posts.created_at DESC";
        return executeStandardPostQuery(querySQL, currentUser, targetUsername, false);
    }

    public static String getUserReposts(String targetUsername, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, false) +
                          "FROM posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE users.username = ? AND (posts.parent_post_id IS NOT NULL OR posts.parent_comment_id IS NOT NULL) AND (posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = '') ORDER BY posts.created_at DESC";
        return executeStandardPostQuery(querySQL, currentUser, targetUsername, false);
    }

    private static String executeStandardPostQuery(String querySQL, String currentUser, String param1, boolean forceIsSaved) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            if (param1 != null) pstmt.setString(1, param1);
            
            ResultSet rs = pstmt.executeQuery();
            boolean isFirst = true;
            while (rs.next()) {
                if (!isFirst) jsonBuilder.append(",");
                isFirst = false;

                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                String content = rs.getString("content"); if (content == null) content = "";
                String media = rs.getString("image_url"); if (media == null) media = "";

                boolean isSaved = forceIsSaved;
                if (!forceIsSaved) {
                    try (PreparedStatement saveStmt = conn.prepareStatement("SELECT 1 FROM saved_posts WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = ?")) {
                        saveStmt.setString(1, currentUser); saveStmt.setInt(2, rs.getInt("id"));
                        isSaved = saveStmt.executeQuery().next();
                    }
                }

                // THE FIX: Wrapping all variables in escapeJSON guarantees the JSON NEVER breaks!
                jsonBuilder.append("{").append("\"id\":").append(rs.getInt("id"))
                           .append(",\"username\":\"").append(escapeJSON(user)).append("\"")
                           .append(",\"avatar\":\"").append(escapeJSON(avatar)).append("\"")
                           .append(",\"content\":\"").append(escapeJSON(content)).append("\"")
                           .append(",\"media\":\"").append(escapeJSON(media)).append("\"")
                           .append(",\"likes\":").append(rs.getInt("like_count"))
                           .append(",\"commentCount\":").append(rs.getInt("comment_count"))
                           .append(",\"repostCount\":").append(rs.getInt("repost_count"))
                           .append(",\"quoteCount\":").append(rs.getInt("quote_count"))
                           .append(",\"isReposted\":").append(rs.getBoolean("is_reposted"))
                           .append(",\"isFollowing\":").append(rs.getBoolean("is_following"))
                           .append(",\"isLiked\":").append(rs.getBoolean("is_liked"))
                           .append(",\"isSaved\":").append(isSaved)
                           .append(",\"isEdited\":").append(rs.getBoolean("is_edited"))
                           .append(",\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                int parentPostId = rs.getInt("parent_post_id");
                int parentCommentId = rs.getInt("parent_comment_id");

                if (parentCommentId > 0) {
                    String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                    String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";
                    String pMedia = ""; try { pMedia = rs.getString("pc_media"); if (pMedia == null) pMedia = ""; } catch (Exception e) {}

                    jsonBuilder.append("\"parentPost\":{\"id\":").append(parentCommentId).append(",\"postId\":").append(rs.getInt("pc_post_id")).append(",\"isComment\":true,\"username\":\"").append(escapeJSON(pUser)).append("\",\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",\"content\":\"").append(escapeJSON(pContent)).append("\",\"media\":\"").append(escapeJSON(pMedia)).append("\",\"isEdited\":").append(rs.getBoolean("pc_is_edited")).append(",\"timestamp\":\"").append(rs.getString("pc_timestamp")).append("\"}");
                } else if (parentPostId > 0) {
                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    jsonBuilder.append("\"parentPost\":{\"id\":").append(parentPostId).append(",\"isComment\":false,\"username\":\"").append(escapeJSON(pUser)).append("\",\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",\"content\":\"").append(escapeJSON(pContent)).append("\",\"media\":\"").append(escapeJSON(pMedia)).append("\",\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                } else {
                    jsonBuilder.append("\"parentPost\":null");
                }
                jsonBuilder.append("}");
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String getSinglePost(String postId, String currentUser) {
        String safeUser = currentUser.replace("'", "''");
        String IS_PURE = "((posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = ''))";
        String IS_PURE_P2 = "((p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = ''))";
        String IS_PURE_PR = "((pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = ''))";

        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(CASE WHEN " + IS_PURE + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = posts.parent_comment_id) ELSE (SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") END) AS like_count, " +
                          "(CASE WHEN " + IS_PURE + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM comments c2 WHERE c2.parent_comment_id = posts.parent_comment_id) ELSE (SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") END) AS comment_count, " +
                          "(CASE WHEN " + IS_PURE + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = posts.parent_comment_id AND " + IS_PURE_P2 + ") ELSE (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND " + IS_PURE_P2 + ") END) AS pure_repost_count, " +
                          "(CASE WHEN " + IS_PURE + " AND posts.parent_comment_id IS NOT NULL THEN (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = posts.parent_comment_id AND NOT " + IS_PURE_P2 + ") ELSE (SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND NOT " + IS_PURE_P2 + ") END) AS quote_count, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND following_id = posts.user_id) AS is_following, " +
                          "(CASE WHEN " + IS_PURE + " AND posts.parent_comment_id IS NOT NULL THEN EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND comment_id = posts.parent_comment_id) ELSE EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND post_id = " + TARGET_ID + ") END) AS is_liked, " +
                          "(CASE WHEN " + IS_PURE + " AND posts.parent_comment_id IS NOT NULL THEN EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_comment_id = posts.parent_comment_id AND pr.user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND " + IS_PURE_PR + ") ELSE EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND " + IS_PURE_PR + ") END) AS is_reposted, " +
                          "EXISTS (SELECT 1 FROM saved_posts WHERE user_id = (SELECT id FROM users WHERE username = '" + safeUser + "') AND post_id = posts.id) AS is_saved, " +
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.image_url AS pc_media, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id WHERE posts.id = ?";

        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setInt(1, Integer.parseInt(postId));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                String content = rs.getString("content"); if (content == null) content = "";
                String media = rs.getString("image_url"); if (media == null) media = "";

                String parentPostJson = "null";
                int parentPostId = rs.getInt("parent_post_id");
                int parentCommentId = rs.getInt("parent_comment_id");

                if (parentCommentId > 0) {
                    String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                    String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";
                    String pMedia = ""; try { pMedia = rs.getString("pc_media"); if (pMedia == null) pMedia = ""; } catch (Exception e) {}
                    parentPostJson = "{\"id\":" + parentCommentId + ",\"postId\":" + rs.getInt("pc_post_id") + ",\"isComment\":true,\"username\":\"" + escapeJSON(pUser) + "\",\"avatar\":\"" + escapeJSON(pAvatar) + "\",\"content\":\"" + escapeJSON(pContent) + "\",\"media\":\"" + escapeJSON(pMedia) + "\",\"isEdited\":" + rs.getBoolean("pc_is_edited") + ",\"timestamp\":\"" + rs.getString("pc_timestamp") + "\"}";
                } else if (parentPostId > 0) {
                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    parentPostJson = "{\"id\":" + parentPostId + ",\"isComment\":false,\"username\":\"" + escapeJSON(pUser) + "\",\"avatar\":\"" + escapeJSON(pAvatar) + "\",\"content\":\"" + escapeJSON(pContent) + "\",\"media\":\"" + escapeJSON(pMedia) + "\",\"isEdited\":" + rs.getBoolean("parent_is_edited") + ",\"timestamp\":\"" + rs.getString("parent_timestamp") + "\"}";
                }
                return "{\"id\":" + rs.getInt("id") + ",\"username\":\"" + escapeJSON(user) + "\",\"avatar\":\"" + escapeJSON(avatar) + "\",\"content\":\"" + escapeJSON(content) + "\",\"media\":\"" + escapeJSON(media) + "\",\"likes\":" + rs.getInt("like_count") + ",\"commentCount\":" + rs.getInt("comment_count") + ",\"repostCount\":" + rs.getInt("pure_repost_count") + ",\"quoteCount\":" + rs.getInt("quote_count") + ",\"isReposted\":" + rs.getBoolean("is_reposted") + ",\"isFollowing\":" + rs.getBoolean("is_following") + ",\"isLiked\":" + rs.getBoolean("is_liked") + ",\"isEdited\":" + rs.getBoolean("is_edited") + ",\"isSaved\":" + rs.getBoolean("is_saved") + ",\"parentPost\":" + parentPostJson + ",\"timestamp\":\"" + rs.getString("created_at") + "\"}";
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        return "ERROR";
    }

    public static String getPostQuotes(String postId, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.is_edited " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "WHERE posts.parent_post_id = ? AND posts.parent_comment_id IS NULL AND ((posts.content IS NOT NULL AND trim(posts.content) != '') OR (posts.image_url IS NOT NULL AND trim(posts.image_url) != '')) " +
                          "ORDER BY posts.created_at DESC";

        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setInt(1, Integer.parseInt(postId));
            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonBuilder.append(",");
                first = false;
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                String content = rs.getString("content"); if (content == null) content = "";
                
                jsonBuilder.append("{\"id\":").append(rs.getInt("id")).append(",\"username\":\"").append(escapeJSON(user)).append("\",\"avatar\":\"").append(escapeJSON(avatar)).append("\",\"content\":\"").append(escapeJSON(content)).append("\",\"media\":\"").append(escapeJSON(rs.getString("image_url") == null ? "" : rs.getString("image_url"))).append("\",\"isEdited\":").append(rs.getBoolean("is_edited")).append(",\"timestamp\":\"").append(rs.getString("created_at")).append("\"}");
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String getCommentQuotes(String commentId, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.is_edited " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "WHERE posts.parent_comment_id = ? AND ((posts.content IS NOT NULL AND trim(posts.content) != '') OR (posts.image_url IS NOT NULL AND trim(posts.image_url) != '')) " +
                          "ORDER BY posts.created_at DESC";

        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setInt(1, Integer.parseInt(commentId));
            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonBuilder.append(",");
                first = false;
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                String content = rs.getString("content"); if (content == null) content = "";
                
                jsonBuilder.append("{\"id\":").append(rs.getInt("id")).append(",\"username\":\"").append(escapeJSON(user)).append("\",\"avatar\":\"").append(escapeJSON(avatar)).append("\",\"content\":\"").append(escapeJSON(content)).append("\",\"media\":\"").append(escapeJSON(rs.getString("image_url") == null ? "" : rs.getString("image_url"))).append("\",\"isEdited\":").append(rs.getBoolean("is_edited")).append(",\"timestamp\":\"").append(rs.getString("created_at")).append("\"}");
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String searchPosts(String searchQuery, String currentUser) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        java.util.regex.Pattern strictWordPattern = java.util.regex.Pattern.compile("(?i)\\b" + java.util.regex.Pattern.quote(searchQuery) + "\\b");
        
        String safeUser = currentUser.replace("'", "''");
        String blockFilterPosts = " AND posts.user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND posts.user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";
        String blockFilterComments = " AND comments.user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND comments.user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";
        
        boolean first = true;
        String searchPattern = "%" + searchQuery + "%";

        try (Connection conn = DatabaseManager.connect()) {
            String postSQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                       "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                       "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                       "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS repost_count, " +
                       "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                       "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                       "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                       "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                       "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.image_url AS pc_media, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                       "FROM posts " +
                       "JOIN users ON posts.user_id = users.id " +
                       "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                       "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                       "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                       "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                       "WHERE posts.content LIKE ? " + blockFilterPosts + 
                       "ORDER BY posts.created_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(postSQL)) {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, currentUser);
                pstmt.setString(3, currentUser);
                pstmt.setString(4, searchPattern);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String text = rs.getString("content"); if (text == null) text = "";
                    java.util.regex.Matcher m = strictWordPattern.matcher(text);
                    String matchedSnippet = "";
                    if (m.find()) {
                        int start = Math.max(0, m.start() - 25);
                        int end = Math.min(text.length(), m.end() + 25);
                        matchedSnippet = "..." + escapeJSON(text.substring(start, end).replace("\n", " ")) + "...";
                    }

                    if (!first) jsonBuilder.append(",");
                    first = false;

                    String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.trim().isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                    String media = rs.getString("image_url"); if (media == null) media = "";

                    jsonBuilder.append("{")
                               .append("\"id\":").append(rs.getInt("id")).append(",")
                               .append("\"username\":\"").append(escapeJSON(rs.getString("username"))).append("\",")
                               .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                               .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                               .append("\"media\":\"").append(escapeJSON(media)).append("\",")
                               .append("\"matchedSnippet\":\"").append(matchedSnippet).append("\",")
                               .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                               .append("\"commentCount\":").append(rs.getInt("comment_count")).append(",")
                               .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                               .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                               .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                               .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                               .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                               .append("\"isComment\":false,")
                               .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                    int parentPostId = rs.getInt("parent_post_id");
                    int parentCommentId = rs.getInt("parent_comment_id");

                    if (parentPostId > 0) {
                        String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                        String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                        String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                        String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                        
                        jsonBuilder.append("\"parentPost\":{")
                                   .append("\"id\":").append(parentPostId).append(",")
                                   .append("\"isComment\":false,")
                                   .append("\"username\":\"").append(escapeJSON(pUser)).append("\",")
                                   .append("\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",")
                                   .append("\"content\":\"").append(escapeJSON(pContent)).append("\",")
                                   .append("\"media\":\"").append(escapeJSON(pMedia)).append("\",")
                                   .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                                   .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                    } else if (parentCommentId > 0) {
                        String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                        String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                        String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";
                        String pMedia = "";
                        try { pMedia = rs.getString("pc_media"); if (pMedia == null) pMedia = ""; } catch(Exception e){}

                        jsonBuilder.append("\"parentPost\":{")
                                   .append("\"id\":").append(parentCommentId).append(",")
                                   .append("\"postId\":").append(rs.getInt("pc_post_id")).append(",")
                                   .append("\"isComment\":true,")
                                   .append("\"username\":\"").append(escapeJSON(pUser)).append("\",")
                                   .append("\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",")
                                   .append("\"content\":\"").append(escapeJSON(pContent)).append("\",")
                                   .append("\"media\":\"").append(escapeJSON(pMedia)).append("\",")
                                   .append("\"isEdited\":").append(rs.getBoolean("pc_is_edited")).append(",")
                                   .append("\"timestamp\":\"").append(rs.getString("pc_timestamp")).append("\"}");
                    } else {
                        jsonBuilder.append("\"parentPost\":null");
                    }
                    jsonBuilder.append("}");
                }
            }

            String commentSQL = "SELECT comments.id, comments.post_id, comments.parent_comment_id, users.username, users.profile_pic_url, comments.content, comments.image_url, comments.created_at, comments.is_edited, " +
                       "(SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = comments.id) AS like_count, " +
                       "(SELECT COUNT(*) FROM comments c2 WHERE c2.parent_comment_id = comments.id) AS comment_count, " +
                       "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = comments.id AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS repost_count, " +
                       "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = comments.user_id) AS is_following, " +
                       "EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND comment_id = comments.id) AS is_liked, " +
                       "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_comment_id = comments.id AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                       "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited " +
                       "FROM comments " +
                       "JOIN users ON comments.user_id = users.id " +
                       "JOIN posts parent_post ON comments.post_id = parent_post.id " +
                       "JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                       "WHERE comments.content LIKE ? " + blockFilterComments + 
                       "ORDER BY comments.created_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(commentSQL)) {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, currentUser);
                pstmt.setString(3, currentUser);
                pstmt.setString(4, searchPattern);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String text = rs.getString("content"); if (text == null) text = "";
                    
                    java.util.regex.Matcher m = strictWordPattern.matcher(text);
                    String matchedSnippet = "";
                    if (m.find()) {
                        int start = Math.max(0, m.start() - 25);
                        int end = Math.min(text.length(), m.end() + 25);
                        matchedSnippet = "..." + escapeJSON(text.substring(start, end).replace("\n", " ")) + "...";
                    } else {
                        matchedSnippet = escapeJSON(text); 
                    }

                    if (!first) jsonBuilder.append(",");
                    first = false;

                    String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.trim().isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                    String media = rs.getString("image_url"); if (media == null) media = "";

                    jsonBuilder.append("{")
                               .append("\"id\":").append(rs.getInt("id")).append(",") 
                               .append("\"postId\":").append(rs.getInt("post_id")).append(",")
                               .append("\"username\":\"").append(escapeJSON(rs.getString("username"))).append("\",")
                               .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                               .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                               .append("\"media\":\"").append(escapeJSON(media)).append("\",")
                               .append("\"matchedSnippet\":\"").append(matchedSnippet).append("\",")
                               .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                               .append("\"commentCount\":").append(rs.getInt("comment_count")).append(",")
                               .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                               .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                               .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                               .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                               .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                               .append("\"isComment\":true,") 
                               .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(rs.getInt("post_id")).append(",")
                               .append("\"isComment\":false,")
                               .append("\"username\":\"").append(escapeJSON(pUser)).append("\",")
                               .append("\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",")
                               .append("\"content\":\"").append(escapeJSON(pContent)).append("\",")
                               .append("\"media\":\"").append(escapeJSON(pMedia)).append("\",")
                               .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                    
                    jsonBuilder.append("}");
                }
            }

        } catch (Exception e) { System.out.println("Error searching posts: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    public static String searchProfilePosts(String searchQuery, String targetUsername, String currentUser) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        java.util.regex.Pattern strictWordPattern = java.util.regex.Pattern.compile("(?i)\\b" + java.util.regex.Pattern.quote(searchQuery) + "\\b");

        String safeUser = currentUser.replace("'", "''");
        String blockFilterPosts = " AND posts.user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND posts.user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";
        String blockFilterComments = " AND comments.user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND comments.user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";

        boolean first = true;
        String searchPattern = "%" + searchQuery + "%";

        try (Connection conn = DatabaseManager.connect()) {
            String postSQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                       "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                       "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                       "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS repost_count, " +
                       "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                       "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                       "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                       "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                       "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.image_url AS pc_media, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                       "FROM posts " +
                       "JOIN users ON posts.user_id = users.id " +
                       "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                       "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                       "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                       "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                       "WHERE users.username = ? AND posts.content LIKE ? " + blockFilterPosts + 
                       "ORDER BY posts.created_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(postSQL)) {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, currentUser);
                pstmt.setString(3, currentUser);
                pstmt.setString(4, targetUsername);
                pstmt.setString(5, searchPattern);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String text = rs.getString("content"); if (text == null) text = "";
                    java.util.regex.Matcher m = strictWordPattern.matcher(text);
                    String matchedSnippet = "";
                    if (m.find()) {
                        int start = Math.max(0, m.start() - 25);
                        int end = Math.min(text.length(), m.end() + 25);
                        matchedSnippet = "..." + escapeJSON(text.substring(start, end).replace("\n", " ")) + "...";
                    }

                    if (!first) jsonBuilder.append(",");
                    first = false;

                    String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.trim().isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                    String media = rs.getString("image_url"); if (media == null) media = "";

                    jsonBuilder.append("{")
                               .append("\"id\":").append(rs.getInt("id")).append(",")
                               .append("\"username\":\"").append(escapeJSON(rs.getString("username"))).append("\",")
                               .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                               .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                               .append("\"media\":\"").append(escapeJSON(media)).append("\",")
                               .append("\"matchedSnippet\":\"").append(matchedSnippet).append("\",")
                               .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                               .append("\"commentCount\":").append(rs.getInt("comment_count")).append(",")
                               .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                               .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                               .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                               .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                               .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                               .append("\"isComment\":false,")
                               .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                    int parentPostId = rs.getInt("parent_post_id");
                    int parentCommentId = rs.getInt("parent_comment_id");

                    if (parentPostId > 0) {
                        String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                        String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                        String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                        String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                        
                        jsonBuilder.append("\"parentPost\":{")
                                   .append("\"id\":").append(parentPostId).append(",")
                                   .append("\"isComment\":false,")
                                   .append("\"username\":\"").append(escapeJSON(pUser)).append("\",")
                                   .append("\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",")
                                   .append("\"content\":\"").append(escapeJSON(pContent)).append("\",")
                                   .append("\"media\":\"").append(escapeJSON(pMedia)).append("\",")
                                   .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                                   .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                    } else if (parentCommentId > 0) {
                        String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                        String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                        String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";
                        String pMedia = "";
                        try { pMedia = rs.getString("pc_media"); if (pMedia == null) pMedia = ""; } catch(Exception e){}

                        jsonBuilder.append("\"parentPost\":{")
                                   .append("\"id\":").append(parentCommentId).append(",")
                                   .append("\"postId\":").append(rs.getInt("pc_post_id")).append(",")
                                   .append("\"isComment\":true,")
                                   .append("\"username\":\"").append(escapeJSON(pUser)).append("\",")
                                   .append("\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",")
                                   .append("\"content\":\"").append(escapeJSON(pContent)).append("\",")
                                   .append("\"media\":\"").append(escapeJSON(pMedia)).append("\",")
                                   .append("\"isEdited\":").append(rs.getBoolean("pc_is_edited")).append(",")
                                   .append("\"timestamp\":\"").append(rs.getString("pc_timestamp")).append("\"}");
                    } else {
                        jsonBuilder.append("\"parentPost\":null");
                    }
                    jsonBuilder.append("}");
                }
            }

            String commentSQL = "SELECT comments.id, comments.post_id, comments.parent_comment_id, users.username, users.profile_pic_url, comments.content, comments.image_url, comments.created_at, comments.is_edited, " +
                       "(SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = comments.id) AS like_count, " +
                       "(SELECT COUNT(*) FROM comments c2 WHERE c2.parent_comment_id = comments.id) AS comment_count, " +
                       "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = comments.id AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS repost_count, " +
                       "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = comments.user_id) AS is_following, " +
                       "EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND comment_id = comments.id) AS is_liked, " +
                       "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_comment_id = comments.id AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                       "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited " +
                       "FROM comments " +
                       "JOIN users ON comments.user_id = users.id " +
                       "JOIN posts parent_post ON comments.post_id = parent_post.id " +
                       "JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                       "WHERE users.username = ? AND comments.content LIKE ? " + blockFilterComments + 
                       "ORDER BY comments.created_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(commentSQL)) {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, currentUser);
                pstmt.setString(3, currentUser);
                pstmt.setString(4, targetUsername);
                pstmt.setString(5, searchPattern);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String text = rs.getString("content"); if (text == null) text = "";
                    
                    java.util.regex.Matcher m = strictWordPattern.matcher(text);
                    String matchedSnippet = "";
                    if (m.find()) {
                        int start = Math.max(0, m.start() - 25);
                        int end = Math.min(text.length(), m.end() + 25);
                        matchedSnippet = "..." + escapeJSON(text.substring(start, end).replace("\n", " ")) + "...";
                    } else {
                        matchedSnippet = escapeJSON(text);
                    }

                    if (!first) jsonBuilder.append(",");
                    first = false;

                    String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.trim().isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                    String media = rs.getString("image_url"); if (media == null) media = "";

                    jsonBuilder.append("{")
                               .append("\"id\":").append(rs.getInt("id")).append(",")
                               .append("\"postId\":").append(rs.getInt("post_id")).append(",")
                               .append("\"username\":\"").append(escapeJSON(rs.getString("username"))).append("\",")
                               .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                               .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                               .append("\"media\":\"").append(escapeJSON(media)).append("\",")
                               .append("\"matchedSnippet\":\"").append(matchedSnippet).append("\",")
                               .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                               .append("\"commentCount\":").append(rs.getInt("comment_count")).append(",")
                               .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                               .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                               .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                               .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                               .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                               .append("\"isComment\":true,")
                               .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = MessageSystem.DEFAULT_AVATAR;
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(rs.getInt("post_id")).append(",")
                               .append("\"isComment\":false,")
                               .append("\"username\":\"").append(escapeJSON(pUser)).append("\",")
                               .append("\"avatar\":\"").append(escapeJSON(pAvatar)).append("\",")
                               .append("\"content\":\"").append(escapeJSON(pContent)).append("\",")
                               .append("\"media\":\"").append(escapeJSON(pMedia)).append("\",")
                               .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                    
                    jsonBuilder.append("}");
                }
            }

        } catch (Exception e) { System.out.println("Error searching posts: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    /* ========================================= */
    /* --- THE REPOST & LIKE ENGINE ---          */
    /* ========================================= */
    
    public static String toggleLike(String identifier, int postId) {
        int finalPostId = postId;
        
        String checkRepostSQL = "SELECT parent_post_id, parent_comment_id FROM posts WHERE id = ? AND (content IS NULL OR trim(content) = '') AND (image_url IS NULL OR trim(image_url) = '') AND (parent_post_id IS NOT NULL OR parent_comment_id IS NOT NULL)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement prStmt = conn.prepareStatement(checkRepostSQL)) {
            prStmt.setInt(1, finalPostId);
            ResultSet rsPr = prStmt.executeQuery();
            if (rsPr.next()) {
                int pCId = rsPr.getInt("parent_comment_id");
                if (pCId > 0) return toggleCommentLike(identifier, pCId);
                finalPostId = rsPr.getInt("parent_post_id");
            }
        } catch (SQLException e) { System.out.println("Error checking repost link: " + e.getMessage()); }

        String findUserSQL = "SELECT id FROM users WHERE username = ? OR email = ?";
        String checkLikeSQL = "SELECT id FROM likes WHERE user_id = ? AND post_id = ?";
        String addLikeSQL = "INSERT INTO likes(user_id, post_id) VALUES(?, ?)";
        String removeLikeSQL = "DELETE FROM likes WHERE user_id = ? AND post_id = ?";
        String getOwnerSQL = "SELECT username FROM users WHERE id = (SELECT user_id FROM posts WHERE id = ?)";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {

            findUserStmt.setString(1, identifier);
            findUserStmt.setString(2, identifier);
            ResultSet rsUser = findUserStmt.executeQuery();

            if (rsUser.next()) {
                int userId = rsUser.getInt("id");

                try (PreparedStatement checkLikeStmt = conn.prepareStatement(checkLikeSQL)) {
                    checkLikeStmt.setInt(1, userId);
                    checkLikeStmt.setInt(2, finalPostId);
                    ResultSet rsLike = checkLikeStmt.executeQuery();

                    if (rsLike.next()) {
                        try (PreparedStatement removeStmt = conn.prepareStatement(removeLikeSQL)) {
                            removeStmt.setInt(1, userId);
                            removeStmt.setInt(2, finalPostId);
                            removeStmt.executeUpdate();
                            return "UNLIKED";
                        }
                    } else {
                        try (PreparedStatement addStmt = conn.prepareStatement(addLikeSQL)) {
                            addStmt.setInt(1, userId);
                            addStmt.setInt(2, finalPostId);
                            addStmt.executeUpdate();

                            try (PreparedStatement ownerStmt = conn.prepareStatement(getOwnerSQL)) {
                                ownerStmt.setInt(1, finalPostId);
                                ResultSet rsOwner = ownerStmt.executeQuery();
                                if (rsOwner.next()) {
                                    String postOwner = rsOwner.getString("username");
                                    if (!postOwner.equals(identifier)) {
                                        NotificationSystem.createNotification(postOwner, identifier, "LIKE", finalPostId);
                                    }
                                }
                            }
                            return "LIKED";
                        }
                    }
                }
            }
        } catch (SQLException e) { System.out.println("Error toggling like: " + e.getMessage()); }
        return "ERROR";
    }

    public static boolean addComment(String identifier, int postId, String content) {
        return addComment(identifier, postId, content, "", null);
    }
    
    public static boolean addComment(String identifier, int postId, String content, Integer parentCommentId) {
        return addComment(identifier, postId, content, "", parentCommentId);
    }

    public static boolean addComment(String identifier, int postId, String content, String mediaUrl, Integer parentCommentId) {
        String sql = "INSERT INTO comments(post_id, user_id, content, image_url, parent_comment_id) VALUES(?, (SELECT id FROM users WHERE username = ? OR email = ?), ?, ?, ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, postId);
            pstmt.setString(2, identifier);
            pstmt.setString(3, identifier);
            pstmt.setString(4, content);
            pstmt.setString(5, mediaUrl);
            
            if (parentCommentId != null) {
                pstmt.setInt(6, parentCommentId);
            } else {
                pstmt.setNull(6, java.sql.Types.INTEGER);
            }
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0 && content != null) {
                if (parentCommentId == null) {
                    String getOwnerSql = "SELECT users.username FROM posts JOIN users ON posts.user_id = users.id WHERE posts.id = ?";
                    try (PreparedStatement ownerStmt = conn.prepareStatement(getOwnerSql)) {
                        ownerStmt.setInt(1, postId);
                        ResultSet rs = ownerStmt.executeQuery();
                        if (rs.next()) {
                            String postOwner = rs.getString("username");
                            if (!postOwner.equals(identifier)) {
                                NotificationSystem.createNotification(postOwner, identifier, "COMMENT", postId);
                            }
                        }
                    }
                } else {
                    String getParentOwnerSql = "SELECT users.username FROM comments JOIN users ON comments.user_id = users.id WHERE comments.id = ?";
                    try (PreparedStatement parentOwnerStmt = conn.prepareStatement(getParentOwnerSql)) {
                        parentOwnerStmt.setInt(1, parentCommentId);
                        ResultSet rs = parentOwnerStmt.executeQuery();
                        if (rs.next()) {
                            String parentOwner = rs.getString("username");
                            if (!parentOwner.equals(identifier)) {
                                NotificationSystem.createNotification(parentOwner, identifier, "COMMENT_REPLY", postId);
                            }
                        }
                    }
                }

                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@(\\w+)").matcher(content);
                while (matcher.find()) {
                    String mentionedUser = matcher.group(1);
                    if (!mentionedUser.equals(identifier)) {
                        NotificationSystem.createNotification(mentionedUser, identifier, "COMMENT_MENTION", postId);
                    }
                }
                return true;
            }
            return false;
        } catch (SQLException e) { System.out.println("Error adding comment: " + e.getMessage()); return false; }
    }

    public static boolean editComment(String username, int commentId, String newContent) {
        String sql = "UPDATE comments SET content = ?, is_edited = 1 WHERE id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newContent);
            pstmt.setInt(2, commentId);
            pstmt.setString(3, username);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { System.out.println("Error editing comment: " + e.getMessage()); return false; }
    }

    public static boolean deleteComment(String username, int commentId) {
        String sql = "DELETE FROM comments WHERE id = ? AND user_id = (SELECT id FROM users WHERE username = ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, commentId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { System.out.println("Error deleting comment: " + e.getMessage()); return false; }
    }

    public static String toggleCommentLike(String identifier, int commentId) {
        String findUserSQL = "SELECT id FROM users WHERE username = ?";
        String checkLikeSQL = "SELECT id FROM comment_likes WHERE user_id = ? AND comment_id = ?";
        String addLikeSQL = "INSERT INTO comment_likes(user_id, comment_id) VALUES(?, ?)";
        String removeLikeSQL = "DELETE FROM comment_likes WHERE user_id = ? AND comment_id = ?";

        try (Connection conn = DatabaseManager.connect(); PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {
            findUserStmt.setString(1, identifier);
            ResultSet rsUser = findUserStmt.executeQuery();
            if (rsUser.next()) {
                int userId = rsUser.getInt("id");
                try (PreparedStatement checkLikeStmt = conn.prepareStatement(checkLikeSQL)) {
                    checkLikeStmt.setInt(1, userId);
                    checkLikeStmt.setInt(2, commentId);
                    if (checkLikeStmt.executeQuery().next()) {
                        try (PreparedStatement removeStmt = conn.prepareStatement(removeLikeSQL)) {
                            removeStmt.setInt(1, userId);
                            removeStmt.setInt(2, commentId);
                            removeStmt.executeUpdate(); 
                            return "UNLIKED";
                        }
                    } else {
                        try (PreparedStatement addStmt = conn.prepareStatement(addLikeSQL)) {
                            addStmt.setInt(1, userId); 
                            addStmt.setInt(2, commentId); 
                            addStmt.executeUpdate(); 
                            
                            String getCommentOwnerSql = "SELECT users.username, comments.post_id FROM comments JOIN users ON comments.user_id = users.id WHERE comments.id = ?";
                            try (PreparedStatement cOwnerStmt = conn.prepareStatement(getCommentOwnerSql)) {
                                cOwnerStmt.setInt(1, commentId);
                                ResultSet rs = cOwnerStmt.executeQuery();
                                
                                if (rs.next()) {
                                    String commentOwner = rs.getString("username");
                                    int pId = rs.getInt("post_id"); 
                                    
                                    if (!commentOwner.equals(identifier)) {
                                        NotificationSystem.createNotification(commentOwner, identifier, "COMMENT_LIKE", pId);
                                    }
                                }
                            }
                            return "LIKED";
                        }
                    }
                }
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        return "ERROR";
    }

    public static String getComments(int postId, String currentUser) {
        String safeUser = currentUser.replace("'", "''");
        String blockFilter = " AND comments.user_id NOT IN (SELECT blocked_id FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) AND comments.user_id NOT IN (SELECT blocker_id FROM blocked_users WHERE blocked_id = (SELECT id FROM users WHERE username = '" + safeUser + "')) ";

        String querySQL = "SELECT comments.id, comments.parent_comment_id, users.username, users.profile_pic_url, comments.content, comments.image_url, comments.created_at, comments.is_edited, " +
                          "(SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = comments.id) AS like_count, " +
                          "(SELECT COUNT(*) FROM comments c2 WHERE c2.parent_comment_id = comments.id) AS reply_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = comments.id AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS repost_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = comments.id AND NOT ((p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = ''))) AS quote_count, " +
                          "EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND comment_id = comments.id) AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_comment_id = comments.id AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "(SELECT COUNT(*) FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = comments.user_id) AS is_following, " +
                          "EXISTS (SELECT 1 FROM saved_comments WHERE user_id = (SELECT id FROM users WHERE username = ?) AND comment_id = comments.id) AS is_saved " +
                          "FROM comments JOIN users ON comments.user_id = users.id " +
                          "WHERE comments.post_id = ? " + blockFilter + " ORDER BY comments.created_at ASC";

        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, currentUser);
            pstmt.setInt(5, postId);
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                isFirstItem = false;
                
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                String text = rs.getString("content"); if (text == null) text = "";
                String media = rs.getString("image_url"); if (media == null) media = ""; 
                int parentCommentId = rs.getInt("parent_comment_id");
                boolean hasParent = !rs.wasNull();
                
                jsonBuilder.append("{")
                           .append("\"id\":").append(rs.getInt("id")).append(",")
                           .append("\"parentCommentId\":").append(hasParent ? parentCommentId : "null").append(",") 
                           .append("\"replyCount\":").append(rs.getInt("reply_count")).append(",") 
                           .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",") 
                           .append("\"quoteCount\":").append(rs.getInt("quote_count")).append(",") 
                           .append("\"username\":\"").append(escapeJSON(user)).append("\",")
                           .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                           .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                           .append("\"media\":\"").append(escapeJSON(media)).append("\",") 
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                           .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                           .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                           .append("\"isSaved\":").append(rs.getBoolean("is_saved")).append(",")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                           .append("}");
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String getUserActivity(String targetUsername) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        String sql = "SELECT comments.id AS comment_id, comments.content, comments.image_url, comments.created_at, comments.is_edited, posts.id AS post_id, users.username AS post_owner, " +
                     "(SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = comments.id) AS like_count " +
                     "FROM comments JOIN posts ON comments.post_id = posts.id JOIN users ON posts.user_id = users.id " +
                     "WHERE comments.user_id = (SELECT id FROM users WHERE username = ?) ORDER BY comments.created_at DESC";
        
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, targetUsername);
            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonBuilder.append(",");
                first = false;
                String text = rs.getString("content"); if (text == null) text = "";
                String media = rs.getString("image_url"); if (media == null) media = "";
                
                jsonBuilder.append("{\"id\":").append(rs.getInt("comment_id")).append(",")
                           .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                           .append("\"media\":\"").append(escapeJSON(media)).append("\",") 
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",")
                           .append("\"postId\":").append(rs.getInt("post_id")).append(",")
                           .append("\"postOwner\":\"").append(rs.getString("post_owner")).append("\"}");
            }
        } catch (Exception e) { System.out.println(e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    /* ========================================= */
    /* --- THE SAVED POSTS ENGINE ---            */
    /* ========================================= */
    
    public static String toggleSave(String identifier, int postId) {
        String findUserSQL = "SELECT id FROM users WHERE username = ?";
        String checkSaveSQL = "SELECT id FROM saved_posts WHERE user_id = ? AND post_id = ?";
        String addSaveSQL = "INSERT INTO saved_posts(user_id, post_id) VALUES(?, ?)";
        String removeSaveSQL = "DELETE FROM saved_posts WHERE user_id = ? AND post_id = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {

            findUserStmt.setString(1, identifier);
            ResultSet rsUser = findUserStmt.executeQuery();

            if (rsUser.next()) {
                int userId = rsUser.getInt("id");

                try (PreparedStatement checkStmt = conn.prepareStatement(checkSaveSQL)) {
                    checkStmt.setInt(1, userId);
                    checkStmt.setInt(2, postId);
                    
                    if (checkStmt.executeQuery().next()) {
                        try (PreparedStatement removeStmt = conn.prepareStatement(removeSaveSQL)) {
                            removeStmt.setInt(1, userId);
                            removeStmt.setInt(2, postId);
                            removeStmt.executeUpdate();
                            return "UNSAVED";
                        }
                    } else {
                        try (PreparedStatement addStmt = conn.prepareStatement(addSaveSQL)) {
                            addStmt.setInt(1, userId);
                            addStmt.setInt(2, postId);
                            addStmt.executeUpdate();
                            return "SAVED";
                        }
                    }
                }
            }
        } catch (SQLException e) { 
            System.out.println("Error toggling save: " + e.getMessage()); 
        }
        return "ERROR";
    }

    public static String getSavedPosts(String currentUser, int pageNumber) {
        int limit = 15; 
        int offset = (pageNumber - 1) * limit; 

        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " + buildStatColumns(currentUser, false) +
                          "FROM saved_posts " +
                          "JOIN posts ON saved_posts.post_id = posts.id " +
                          "JOIN users ON posts.user_id = users.id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE saved_posts.user_id = (SELECT id FROM users WHERE username = ?) " +
                          "ORDER BY saved_posts.created_at DESC LIMIT " + limit + " OFFSET " + offset;

        return executeStandardPostQuery(querySQL, currentUser, currentUser, false);
    }

    public static String toggleSaveComment(String identifier, int commentId) {
        String findUserSQL = "SELECT id FROM users WHERE username = ?";
        String checkSaveSQL = "SELECT id FROM saved_comments WHERE user_id = ? AND comment_id = ?";
        String addSaveSQL = "INSERT INTO saved_comments(user_id, comment_id) VALUES(?, ?)";
        String removeSaveSQL = "DELETE FROM saved_comments WHERE user_id = ? AND comment_id = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {

            findUserStmt.setString(1, identifier);
            ResultSet rsUser = findUserStmt.executeQuery();

            if (rsUser.next()) {
                int userId = rsUser.getInt("id");

                try (PreparedStatement checkStmt = conn.prepareStatement(checkSaveSQL)) {
                    checkStmt.setInt(1, userId);
                    checkStmt.setInt(2, commentId);
                    
                    if (checkStmt.executeQuery().next()) {
                        try (PreparedStatement removeStmt = conn.prepareStatement(removeSaveSQL)) {
                            removeStmt.setInt(1, userId);
                            removeStmt.setInt(2, commentId);
                            removeStmt.executeUpdate();
                            return "UNSAVED";
                        }
                    } else {
                        try (PreparedStatement addStmt = conn.prepareStatement(addSaveSQL)) {
                            addStmt.setInt(1, userId);
                            addStmt.setInt(2, commentId);
                            addStmt.executeUpdate();
                            return "SAVED";
                        }
                    }
                }
            }
        } catch (SQLException e) { 
            System.out.println("Error toggling comment save: " + e.getMessage()); 
        }
        return "ERROR";
    }

    public static String getSavedComments(String currentUser, int pageNumber) {
        int limit = 15;
        int offset = (pageNumber - 1) * limit;
        String querySQL = "SELECT comments.id, comments.parent_comment_id, users.username, users.profile_pic_url, comments.content, comments.image_url, comments.created_at, comments.is_edited, comments.post_id, " +
                          "(SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = comments.id) AS like_count, " +
                          "(SELECT COUNT(*) FROM comments c2 WHERE c2.parent_comment_id = comments.id) AS reply_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_comment_id = comments.id AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS repost_count, " +
                          "EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND comment_id = comments.id) AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_comment_id = comments.id AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = comments.user_id) AS is_following " +
                          "FROM saved_comments " +
                          "JOIN comments ON saved_comments.comment_id = comments.id " +
                          "JOIN users ON comments.user_id = users.id " +
                          "WHERE saved_comments.user_id = (SELECT id FROM users WHERE username = ?) " +
                          "ORDER BY saved_comments.created_at DESC LIMIT " + limit + " OFFSET " + offset;

        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, currentUser);
            ResultSet rs = pstmt.executeQuery();
            
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonBuilder.append(",");
                first = false;
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = MessageSystem.DEFAULT_AVATAR;
                String text = rs.getString("content"); if (text == null) text = "";
                String media = rs.getString("image_url"); if (media == null) media = "";
                int parentCommentId = rs.getInt("parent_comment_id");
                boolean hasParent = !rs.wasNull();

                jsonBuilder.append("{")
                           .append("\"id\":").append(rs.getInt("id")).append(",")
                           .append("\"postId\":").append(rs.getInt("post_id")).append(",")
                           .append("\"parentCommentId\":").append(hasParent ? parentCommentId : "null").append(",")
                           .append("\"replyCount\":").append(rs.getInt("reply_count")).append(",")
                           .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                           .append("\"username\":\"").append(escapeJSON(user)).append("\",")
                           .append("\"avatar\":\"").append(escapeJSON(avatar)).append("\",")
                           .append("\"content\":\"").append(escapeJSON(text)).append("\",")
                           .append("\"media\":\"").append(escapeJSON(media)).append("\",")
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                           .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                           .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                           .append("\"isSaved\":true,")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                           .append("}");
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }
    /* ========================================= */
    /* --- THE POST INTERACTIONS ENGINE ---      */
    /* ========================================= */
    public static String getPostLikes(int postId) {
        String sql = "SELECT users.username, users.profile_pic_url, users.bio FROM likes JOIN users ON likes.user_id = users.id WHERE likes.post_id = ? ORDER BY likes.created_at DESC";
        return buildUserListJson(sql, postId);
    }

    public static String getPostReposts(int postId) {
        String sql = "SELECT users.username, users.profile_pic_url, users.bio FROM posts p JOIN users ON p.user_id = users.id WHERE p.parent_post_id = ? AND (p.content IS NULL OR trim(p.content) = '') AND (p.image_url IS NULL OR trim(p.image_url) = '') ORDER BY p.created_at DESC";
        return buildUserListJson(sql, postId);
    }

    private static String buildUserListJson(String sql, int parentId) {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, parentId);
            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String u = rs.getString("username");
                String a = rs.getString("profile_pic_url"); if (a == null || a.isEmpty()) a = MessageSystem.DEFAULT_AVATAR;
                String b = rs.getString("bio"); if (b == null) b = "";
                json.append("{\"username\":\"").append(escapeJSON(u)).append("\",\"avatar\":\"").append(escapeJSON(a)).append("\",\"bio\":\"").append(escapeJSON(b)).append("\"}");
            }
        } catch(Exception e) { System.out.println("Interaction Query Error: " + e.getMessage()); }
        return json.append("]").toString();
    }
}