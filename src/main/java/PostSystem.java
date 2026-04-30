package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class PostSystem {

    // [MACRO]: Automatically redirects interactions from a pure repost to its original parent!
    private static final String TARGET_ID = "(CASE WHEN (posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = '') AND posts.parent_post_id IS NOT NULL THEN posts.parent_post_id ELSE posts.id END)";

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
                            if (!targetUsername.equals(identifier)) {
                                NotificationSystem.createNotification(targetUsername, identifier, isNewPostQuote ? "QUOTE" : "REPOST", originalPostId);
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
                                if (!targetUsername.equals(identifier)) NotificationSystem.createNotification(targetUsername, identifier, isNewPostQuote ? "QUOTE_REPOST" : "REPOST_REPOST", parentPostId);
                                String originalQuery = "SELECT users.username FROM posts JOIN users ON posts.user_id = users.id WHERE posts.id = ?";
                                try (PreparedStatement origStmt = conn.prepareStatement(originalQuery)) {
                                    origStmt.setInt(1, targetParentId);
                                    ResultSet rsOrig = origStmt.executeQuery();
                                    if (rsOrig.next() && !rsOrig.getString("username").equals(identifier)) {
                                        NotificationSystem.createNotification(rsOrig.getString("username"), identifier, isNewPostQuote ? "QUOTE" : "REPOST", targetParentId);
                                    }
                                }
                            } else {
                                if (!targetUsername.equals(identifier)) NotificationSystem.createNotification(targetUsername, identifier, isNewPostQuote ? "QUOTE" : "REPOST", parentPostId);
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

                // [API Upgrade]: We ask the database to return the ID of the post it just created!
                try (PreparedStatement insertPostStmt = conn.prepareStatement(insertPostSQL, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insertPostStmt.setInt(1, userId);
                    insertPostStmt.setString(2, content);
                    insertPostStmt.setString(3, mediaBase64); 
                    if (finalParentId != null) insertPostStmt.setInt(4, finalParentId); else insertPostStmt.setNull(4, java.sql.Types.INTEGER);
                    if (parentCommentId != null) insertPostStmt.setInt(5, parentCommentId); else insertPostStmt.setNull(5, java.sql.Types.INTEGER);
                    insertPostStmt.executeUpdate();

                    // --- BACKEND REGEX PARSER: Automatically notify mentioned users! ---
                    try (ResultSet generatedKeys = insertPostStmt.getGeneratedKeys()) {
                        if (generatedKeys.next() && content != null) {
                            int newPostId = generatedKeys.getInt(1);
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
    /* --- FEED & SEARCH UPGRADES ---            */
    /* ========================================= */

    public static String getFeed(String currentUser, int pageNumber) {
        
        // THE PAGINATION MATH
        int limit = 15; 
        int offset = (pageNumber - 1) * limit; 

        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                          "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "ORDER BY posts.created_at DESC " +
                          "LIMIT " + limit + " OFFSET " + offset; // THE STRICT DATABASE CONSTRAINT

        return executeStandardPostQuery(querySQL, currentUser, null, false);
    }

    public static String getUserPosts(String targetUsername, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                          "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE users.username = ? ORDER BY posts.created_at DESC";

        return executeStandardPostQuery(querySQL, currentUser, targetUsername, false);
    }

    public static String getUserQuotes(String targetUsername, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                          "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE users.username = ? AND (posts.parent_post_id IS NOT NULL OR posts.parent_comment_id IS NOT NULL) AND ((posts.content IS NOT NULL AND trim(posts.content) != '') OR (posts.image_url IS NOT NULL AND trim(posts.image_url) != '')) " + 
                          "ORDER BY posts.created_at DESC";

        return executeStandardPostQuery(querySQL, currentUser, targetUsername, false);
    }

    public static String getUserReposts(String targetUsername, String currentUser) {
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                          "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE users.username = ? AND (posts.parent_post_id IS NOT NULL OR posts.parent_comment_id IS NOT NULL) AND (posts.content IS NULL OR trim(posts.content) = '') AND (posts.image_url IS NULL OR trim(posts.image_url) = '') " + 
                          "ORDER BY posts.created_at DESC";

        return executeStandardPostQuery(querySQL, currentUser, targetUsername, false);
    }

    public static String getFollowingFeed(String currentUser, int pageNumber) {
        
        // THE PAGINATION MATH
        int limit = 15; 
        int offset = (pageNumber - 1) * limit; 

        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                          "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "1 AS is_following, " + 
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "JOIN followers ON posts.user_id = followers.following_id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE followers.follower_id = (SELECT id FROM users WHERE username = ?) ORDER BY posts.created_at DESC " +
                          "LIMIT " + limit + " OFFSET " + offset; // THE STRICT DATABASE CONSTRAINT

        return executeStandardPostQuery(querySQL, currentUser, null, true);
    }

    private static String executeStandardPostQuery(String querySQL, String currentUser, String param3, boolean bypassFollowCheck) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            
            if (bypassFollowCheck) {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, currentUser);
                pstmt.setString(3, currentUser);
            } else {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, currentUser);
                pstmt.setString(3, currentUser);
                if (param3 != null) pstmt.setString(4, param3);
            }
            
            ResultSet rs = pstmt.executeQuery();
            boolean isFirst = true;
            while (rs.next()) {
                if (!isFirst) jsonBuilder.append(",");
                isFirst = false;

                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                if (avatar == null || avatar.isEmpty()) avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                String content = rs.getString("content"); if (content == null) content = "";
                String media = rs.getString("image_url"); if (media == null) media = "";

                jsonBuilder.append("{")
                           .append("\"id\":").append(rs.getInt("id")).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"content\":\"").append(content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"media\":\"").append(media).append("\",")
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"commentCount\":").append(rs.getInt("comment_count")).append(",")
                           .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                           .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",") 
                           .append("\"isFollowing\":").append(bypassFollowCheck ? true : rs.getBoolean("is_following")).append(",")
                           .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                int parentPostId = rs.getInt("parent_post_id");
                int parentCommentId = rs.getInt("parent_comment_id");

                if (parentCommentId > 0) {
                    String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";

                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(parentCommentId).append(",")
                               .append("\"postId\":").append(rs.getInt("pc_post_id")).append(",")
                               .append("\"isComment\":true,")
                               .append("\"username\":\"").append(pUser).append("\",")
                               .append("\"avatar\":\"").append(pAvatar).append("\",")
                               .append("\"content\":\"").append(pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                               .append("\"media\":\"\",")
                               .append("\"isEdited\":").append(rs.getBoolean("pc_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("pc_timestamp")).append("\"}");
                } else if (parentPostId > 0) {
                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(parentPostId).append(",")
                               .append("\"isComment\":false,")
                               .append("\"username\":\"").append(pUser).append("\",")
                               .append("\"avatar\":\"").append(pAvatar).append("\",")
                               .append("\"content\":\"").append(pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                               .append("\"media\":\"").append(pMedia).append("\",")
                               .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                } else {
                    jsonBuilder.append("\"parentPost\":null");
                }
                jsonBuilder.append("}");
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String getSinglePost(String postId, String currentUser) {
        // FIX: Added 'AND p2.parent_comment_id IS NULL' to quote_count and pure_repost_count to prevent comment inflation!
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND (p2.content IS NULL OR trim(p2.content) = '') AND (p2.image_url IS NULL OR trim(p2.image_url) = '')) AS pure_repost_count, " +
                          "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL AND ((p2.content IS NOT NULL AND trim(p2.content) != '') OR (p2.image_url IS NOT NULL AND trim(p2.image_url) != ''))) AS quote_count, " +
                          "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                          "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                          "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                          "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                          "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                          "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                          "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                          "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                          "WHERE posts.id = ?";

        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
            pstmt.setString(3, currentUser);
            pstmt.setInt(4, Integer.parseInt(postId));
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                String content = rs.getString("content"); if (content == null) content = "";
                String media = rs.getString("image_url"); if (media == null) media = "";

                String parentPostJson = "null";
                int parentPostId = rs.getInt("parent_post_id");
                int parentCommentId = rs.getInt("parent_comment_id");

                if (parentCommentId > 0) {
                    String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";
                    
                    parentPostJson = "{\"id\":" + parentCommentId + ",\"postId\":" + rs.getInt("pc_post_id") + ",\"isComment\":true,\"username\":\"" + pUser + "\",\"avatar\":\"" + pAvatar + "\",\"content\":\"" + pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\",\"media\":\"\",\"isEdited\":" + rs.getBoolean("pc_is_edited") + ",\"timestamp\":\"" + rs.getString("pc_timestamp") + "\"}";
                } else if (parentPostId > 0) {
                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    parentPostJson = "{\"id\":" + parentPostId + ",\"isComment\":false,\"username\":\"" + pUser + "\",\"avatar\":\"" + pAvatar + "\",\"content\":\"" + pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\",\"media\":\"" + pMedia + "\",\"isEdited\":" + rs.getBoolean("parent_is_edited") + ",\"timestamp\":\"" + rs.getString("parent_timestamp") + "\"}";
                }

                return "{\"id\":" + rs.getInt("id") + ",\"username\":\"" + user + "\",\"avatar\":\"" + avatar + "\",\"content\":\"" + content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\",\"media\":\"" + media + "\",\"likes\":" + rs.getInt("like_count") + ",\"commentCount\":" + rs.getInt("comment_count") + ",\"pureRepostCount\":" + rs.getInt("pure_repost_count") + ",\"quoteCount\":" + rs.getInt("quote_count") + ",\"isReposted\":" + rs.getBoolean("is_reposted") + ",\"isFollowing\":" + rs.getBoolean("is_following") + ",\"isLiked\":" + rs.getBoolean("is_liked") + ",\"isEdited\":" + rs.getBoolean("is_edited") + ",\"parentPost\":" + parentPostJson + ",\"timestamp\":\"" + rs.getString("created_at") + "\"}";
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        return "ERROR";
    }

    public static String getPostQuotes(String postId, String currentUser) {
        // FIX: Now explicitly excludes comment quotes by checking parent_comment_id IS NULL
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
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                String content = rs.getString("content"); if (content == null) content = "";
                
                jsonBuilder.append("{\"id\":").append(rs.getInt("id")).append(",\"username\":\"").append(user).append("\",\"avatar\":\"").append(avatar).append("\",\"content\":\"").append(content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",\"media\":\"").append(rs.getString("image_url") == null ? "" : rs.getString("image_url")).append("\",\"isEdited\":").append(rs.getBoolean("is_edited")).append(",\"timestamp\":\"").append(rs.getString("created_at")).append("\"}");
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
                String avatar = rs.getString("profile_pic_url"); if (avatar == null || avatar.isEmpty()) avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                String content = rs.getString("content"); if (content == null) content = "";
                
                jsonBuilder.append("{\"id\":").append(rs.getInt("id")).append(",\"username\":\"").append(user).append("\",\"avatar\":\"").append(avatar).append("\",\"content\":\"").append(content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",\"media\":\"").append(rs.getString("image_url") == null ? "" : rs.getString("image_url")).append("\",\"isEdited\":").append(rs.getBoolean("is_edited")).append(",\"timestamp\":\"").append(rs.getString("created_at")).append("\"}");
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String searchPosts(String searchQuery, String currentUser) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        Pattern strictWordPattern = Pattern.compile("\\b" + Pattern.quote(searchQuery) + "\\b", Pattern.CASE_INSENSITIVE);
        
        // FIX: Excluded comments from repost_count math!
        String searchSQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                           "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                           "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                           "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                           "(SELECT GROUP_CONCAT(content, ' ') FROM comments WHERE comments.post_id = posts.id) AS all_comments, " +
                           "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                           "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                           "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                           "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                           "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                           "FROM posts " +
                           "JOIN users ON posts.user_id = users.id " +
                           "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                           "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                           "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                           "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                           "WHERE posts.content LIKE ? " +
                           "OR posts.id IN (SELECT post_id FROM comments WHERE content LIKE ?) " +
                           "ORDER BY posts.created_at DESC";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(searchSQL)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, searchPattern);
            pstmt.setString(5, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            java.util.regex.Matcher commentMatcher; 

            while (rs.next()) {
                String text = rs.getString("content");
                String allComments = rs.getString("all_comments");
                if (text == null) text = ""; 
                if (allComments == null) allComments = ""; 

                boolean matchFoundInPost = strictWordPattern.matcher(text).find();
                commentMatcher = strictWordPattern.matcher(allComments);
                boolean matchFoundInComments = commentMatcher.find();

                if (!matchFoundInPost && !matchFoundInComments) { continue; }

                String matchedSnippet = "";
                if (!matchFoundInPost && matchFoundInComments) {
                    int start = Math.max(0, commentMatcher.start() - 25);
                    int end = Math.min(allComments.length(), commentMatcher.end() + 25);
                    matchedSnippet = "..." + allComments.substring(start, end).replace("\"", "\\\"").replace("\n", " ") + "...";
                }

                if (!first) { jsonBuilder.append(","); }
                first = false;

                int id = rs.getInt("id");
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                String media = rs.getString("image_url");
                String time = rs.getString("created_at");
                int likes = rs.getInt("like_count");
                int comments = rs.getInt("comment_count");
                int reposts = rs.getInt("repost_count");
                boolean isFollowing = rs.getBoolean("is_following");
                boolean isLiked = rs.getBoolean("is_liked");
                boolean isEdited = rs.getBoolean("is_edited");
                boolean isReposted = rs.getBoolean("is_reposted");

                if (avatar == null || avatar.trim().isEmpty()) avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                if (media == null) media = "";

                jsonBuilder.append("{")
                           .append("\"id\":").append(id).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"media\":\"").append(media).append("\",")
                           .append("\"matchedSnippet\":\"").append(matchedSnippet).append("\",") 
                           .append("\"likes\":").append(likes).append(",")
                           .append("\"commentCount\":").append(comments).append(",")
                           .append("\"repostCount\":").append(reposts).append(",")
                           .append("\"isReposted\":").append(isReposted).append(",")
                           .append("\"isFollowing\":").append(isFollowing).append(",")
                           .append("\"isLiked\":").append(isLiked).append(",")
                           .append("\"isEdited\":").append(isEdited).append(",")
                           .append("\"timestamp\":\"").append(time).append("\",");

                int parentPostId = rs.getInt("parent_post_id");
                int parentCommentId = rs.getInt("parent_comment_id");

                if (parentPostId > 0) {
                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(parentPostId).append(",")
                               .append("\"isComment\":false,")
                               .append("\"username\":\"").append(pUser).append("\",")
                               .append("\"avatar\":\"").append(pAvatar).append("\",")
                               .append("\"content\":\"").append(pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                               .append("\"media\":\"").append(pMedia).append("\",")
                               .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                } else if (parentCommentId > 0) {
                    String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";

                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(parentCommentId).append(",")
                               .append("\"postId\":").append(rs.getInt("pc_post_id")).append(",")
                               .append("\"isComment\":true,")
                               .append("\"username\":\"").append(pUser).append("\",")
                               .append("\"avatar\":\"").append(pAvatar).append("\",")
                               .append("\"content\":\"").append(pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                               .append("\"media\":\"\",")
                               .append("\"isEdited\":").append(rs.getBoolean("pc_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("pc_timestamp")).append("\"}");
                } else {
                    jsonBuilder.append("\"parentPost\":null");
                }
                jsonBuilder.append("}");
            }
        } catch (Exception e) { System.out.println("Error searching posts: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    public static String searchProfilePosts(String searchQuery, String targetUsername, String currentUser) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        java.util.regex.Pattern strictWordPattern = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(searchQuery) + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

        // FIX: Excluded comments from repost_count math!
        String searchSQL = "SELECT DISTINCT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, posts.parent_post_id, posts.parent_comment_id, posts.is_edited, " +
                           "(SELECT COUNT(*) FROM likes WHERE likes.post_id = " + TARGET_ID + ") AS like_count, " +
                           "(SELECT COUNT(*) FROM comments WHERE comments.post_id = " + TARGET_ID + ") AS comment_count, " +
                           "(SELECT COUNT(*) FROM posts p2 WHERE p2.parent_post_id = " + TARGET_ID + " AND p2.parent_comment_id IS NULL) AS repost_count, " +
                           "(SELECT GROUP_CONCAT(content, ' ') FROM comments WHERE comments.post_id = posts.id) AS all_comments, " +
                           "EXISTS (SELECT 1 FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following, " +
                           "EXISTS (SELECT 1 FROM likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND post_id = " + TARGET_ID + ") AS is_liked, " +
                           "EXISTS (SELECT 1 FROM posts pr WHERE pr.parent_post_id = " + TARGET_ID + " AND pr.user_id = (SELECT id FROM users WHERE username = ?) AND (pr.content IS NULL OR trim(pr.content) = '') AND (pr.image_url IS NULL OR trim(pr.image_url) = '')) AS is_reposted, " +
                           "parent_user.username AS parent_username, parent_user.profile_pic_url AS parent_avatar, parent_post.content AS parent_content, parent_post.image_url AS parent_media, parent_post.created_at AS parent_timestamp, parent_post.is_edited AS parent_is_edited, " +
                           "parent_comment_user.username AS pc_username, parent_comment_user.profile_pic_url AS pc_avatar, parent_comment.content AS pc_content, parent_comment.created_at AS pc_timestamp, parent_comment.is_edited AS pc_is_edited, parent_comment.post_id AS pc_post_id " +
                           "FROM posts " +
                           "JOIN users ON posts.user_id = users.id " +
                           "LEFT JOIN comments c_search ON c_search.post_id = posts.id " +
                           "LEFT JOIN posts parent_post ON posts.parent_post_id = parent_post.id " +
                           "LEFT JOIN users parent_user ON parent_post.user_id = parent_user.id " +
                           "LEFT JOIN comments parent_comment ON posts.parent_comment_id = parent_comment.id " +
                           "LEFT JOIN users parent_comment_user ON parent_comment.user_id = parent_comment_user.id " +
                           "WHERE (users.username = ? OR c_search.user_id = (SELECT id FROM users WHERE username = ?)) " +
                           "AND (posts.content LIKE ? OR posts.id IN (SELECT post_id FROM comments WHERE content LIKE ?)) " +
                           "ORDER BY posts.created_at DESC";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(searchSQL)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
            pstmt.setString(3, currentUser);
            pstmt.setString(4, targetUsername);
            pstmt.setString(5, targetUsername);
            pstmt.setString(6, searchPattern);
            pstmt.setString(7, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                String text = rs.getString("content");
                String allComments = rs.getString("all_comments");
                if (text == null) text = "";
                if (allComments == null) allComments = "";

                boolean matchFoundInPost = strictWordPattern.matcher(text).find();
                java.util.regex.Matcher commentMatcher = strictWordPattern.matcher(allComments);
                boolean matchFoundInComments = commentMatcher.find();

                if (!matchFoundInPost && !matchFoundInComments) continue;

                String matchedSnippet = "";
                if (!matchFoundInPost && matchFoundInComments) {
                    int start = Math.max(0, commentMatcher.start() - 25);
                    int end = Math.min(allComments.length(), commentMatcher.end() + 25);
                    matchedSnippet = "..." + allComments.substring(start, end).replace("\"", "\\\"").replace("\n", " ") + "...";
                }

                if (!first) jsonBuilder.append(",");
                first = false;

                jsonBuilder.append("{")
                           .append("\"id\":").append(rs.getInt("id")).append(",")
                           .append("\"username\":\"").append(rs.getString("username")).append("\",")
                           .append("\"avatar\":\"").append(rs.getString("profile_pic_url")).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n")).append("\",")
                           .append("\"media\":\"").append(rs.getString("image_url")).append("\",")
                           .append("\"matchedSnippet\":\"").append(matchedSnippet).append("\",")
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"commentCount\":").append(rs.getInt("comment_count")).append(",")
                           .append("\"repostCount\":").append(rs.getInt("repost_count")).append(",")
                           .append("\"isReposted\":").append(rs.getBoolean("is_reposted")).append(",")
                           .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                           .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",");

                int parentPostId = rs.getInt("parent_post_id");
                int parentCommentId = rs.getInt("parent_comment_id");

                if (parentPostId > 0) {
                    String pUser = rs.getString("parent_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("parent_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("parent_content"); if (pContent == null) pContent = "";
                    String pMedia = rs.getString("parent_media"); if (pMedia == null) pMedia = "";
                    
                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(parentPostId).append(",")
                               .append("\"isComment\":false,")
                               .append("\"username\":\"").append(pUser).append("\",")
                               .append("\"avatar\":\"").append(pAvatar).append("\",")
                               .append("\"content\":\"").append(pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                               .append("\"media\":\"").append(pMedia).append("\",")
                               .append("\"isEdited\":").append(rs.getBoolean("parent_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("parent_timestamp")).append("\"}");
                } else if (parentCommentId > 0) {
                    String pUser = rs.getString("pc_username"); if (pUser == null) pUser = "Unknown";
                    String pAvatar = rs.getString("pc_avatar"); if (pAvatar == null || pAvatar.isEmpty()) pAvatar = "https://ui-avatars.com/api/?name=" + pUser + "&background=1e293b&color=00e676";
                    String pContent = rs.getString("pc_content"); if (pContent == null) pContent = "";

                    jsonBuilder.append("\"parentPost\":{")
                               .append("\"id\":").append(parentCommentId).append(",")
                               .append("\"postId\":").append(rs.getInt("pc_post_id")).append(",")
                               .append("\"isComment\":true,")
                               .append("\"username\":\"").append(pUser).append("\",")
                               .append("\"avatar\":\"").append(pAvatar).append("\",")
                               .append("\"content\":\"").append(pContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                               .append("\"media\":\"\",")
                               .append("\"isEdited\":").append(rs.getBoolean("pc_is_edited")).append(",")
                               .append("\"timestamp\":\"").append(rs.getString("pc_timestamp")).append("\"}");
                } else {
                    jsonBuilder.append("\"parentPost\":null");
                }
                jsonBuilder.append("}");
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    /* ========================================= */
    /* --- THE REPOST & LIKE ENGINE ---          */
    /* ========================================= */
    
    public static String toggleLike(String identifier, int postId) {
        int finalPostId = postId;
        
        String checkRepostSQL = "SELECT parent_post_id FROM posts WHERE id = ? AND parent_post_id IS NOT NULL AND (content IS NULL OR trim(content) = '') AND (image_url IS NULL OR trim(image_url) = '')";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement prStmt = conn.prepareStatement(checkRepostSQL)) {
            prStmt.setInt(1, finalPostId);
            ResultSet rsPr = prStmt.executeQuery();
            if (rsPr.next()) {
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
        String sql = "INSERT INTO comments(post_id, user_id, content) VALUES(?, (SELECT id FROM users WHERE username = ? OR email = ?), ?)";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, postId);
            pstmt.setString(2, identifier);
            pstmt.setString(3, identifier);
            pstmt.setString(4, content);
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0 && content != null) {
                // --- BACKEND REGEX PARSER: Notify mentioned users in comments! ---
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@(\\w+)").matcher(content);
                while (matcher.find()) {
                    String mentionedUser = matcher.group(1);
                    if (!mentionedUser.equals(identifier)) {
                        NotificationSystem.createNotification(mentionedUser, identifier, "MENTION", postId);
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
                            return "LIKED";
                        }
                    }
                }
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        return "ERROR";
    }

    public static String getComments(int postId, String currentUser) {
        String querySQL = "SELECT comments.id, users.username, comments.content, comments.created_at, comments.is_edited, " +
                          "(SELECT COUNT(*) FROM comment_likes WHERE comment_likes.comment_id = comments.id) AS like_count, " +
                          "EXISTS (SELECT 1 FROM comment_likes WHERE user_id = (SELECT id FROM users WHERE username = ?) AND comment_id = comments.id) AS is_liked, " +
                          "(SELECT COUNT(*) FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = comments.user_id) AS is_following " +
                          "FROM comments JOIN users ON comments.user_id = users.id " +
                          "WHERE comments.post_id = ? ORDER BY comments.created_at ASC";

        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
            pstmt.setInt(3, postId);
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                isFirstItem = false;
                
                String user = rs.getString("username");
                String text = rs.getString("content");
                if (text == null) text = "";

                jsonBuilder.append("{")
                           .append("\"id\":").append(rs.getInt("id")).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"isLiked\":").append(rs.getBoolean("is_liked")).append(",")
                           .append("\"isFollowing\":").append(rs.getBoolean("is_following")).append(",")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\"")
                           .append("}");
            }
        } catch (SQLException e) { System.out.println("Error: " + e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }

    public static String getUserActivity(String targetUsername) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        String sql = "SELECT comments.id AS comment_id, comments.content, comments.created_at, comments.is_edited, posts.id AS post_id, users.username AS post_owner, " +
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
                jsonBuilder.append("{\"id\":").append(rs.getInt("comment_id")).append(",")
                           .append("\"content\":\"").append(rs.getString("content").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"likes\":").append(rs.getInt("like_count")).append(",")
                           .append("\"isEdited\":").append(rs.getBoolean("is_edited")).append(",")
                           .append("\"timestamp\":\"").append(rs.getString("created_at")).append("\",")
                           .append("\"postId\":").append(rs.getInt("post_id")).append(",")
                           .append("\"postOwner\":\"").append(rs.getString("post_owner")).append("\"}");
            }
        } catch (Exception e) { System.out.println(e.getMessage()); }
        jsonBuilder.append("]"); return jsonBuilder.toString();
    }
}