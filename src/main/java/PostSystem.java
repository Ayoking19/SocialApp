package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class PostSystem {

    /* ========================================= */
    /* --- METHOD 1: CREATE A NEW POST --- */
    /* ========================================= */
    public static boolean createPost(String identifier, String content, String mediaBase64) {
        String findUserSQL = "SELECT id FROM users WHERE username = ? OR email = ?";
        String insertPostSQL = "INSERT INTO posts(user_id, content, image_url) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL);
             PreparedStatement insertPostStmt = conn.prepareStatement(insertPostSQL)) {

            findUserStmt.setString(1, identifier);
            findUserStmt.setString(2, identifier);
            ResultSet rs = findUserStmt.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("id");
                insertPostStmt.setInt(1, userId);
                insertPostStmt.setString(2, content);
                insertPostStmt.setString(3, mediaBase64); 
                insertPostStmt.executeUpdate();
                return true;
            }
            return false; 
        } catch (SQLException e) {
            System.out.println("Error creating post: " + e.getMessage());
            return false;
        }
    }

    /* ========================================= */
    /* --- METHOD 2: READ THE FEED (STATE AWARE) --- */
    /* ========================================= */
    public static String getFeed(String currentUser) {
        /* [State Subquery: We ask the database to count if a row exists in the followers table between the current user and the post author!] */
        // Add this specific SQL string to BOTH getFeed() and getUserPosts()
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = posts.id) AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = posts.id) AS comment_count, " +
                          "(SELECT COUNT(*) FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following " +
                          "FROM posts JOIN users ON posts.user_id = users.id ORDER BY posts.created_at DESC";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("["); 

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            // We inject the current user into the subquery!
            pstmt.setString(1, currentUser);
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                
                int postId = rs.getInt("id");
                int likes = rs.getInt("like_count");
                int comments = rs.getInt("comment_count"); 
                boolean isFollowing = rs.getInt("is_following") > 0; // Converts the database count into a True/False statement
                
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                String text = rs.getString("content");
                String media = rs.getString("image_url"); 
                String time = rs.getString("created_at");

                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = "https://ui-avatars.com/api/?name=" + user + "&background=random&color=fff&rounded=true";
                }

                if (text == null) { text = ""; }
                if (media == null) { media = ""; }
                text = text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

                jsonBuilder.append("{")
                           .append("\"id\":").append(postId).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"content\":\"").append(text).append("\",")
                           .append("\"media\":\"").append(media).append("\",")
                           .append("\"likes\":").append(likes).append(",")
                           .append("\"commentCount\":").append(comments).append(",")
                           .append("\"isFollowing\":").append(isFollowing).append(",") // Packages the truth into the JSON!
                           .append("\"timestamp\":\"").append(time).append("\"")
                           .append("}");
                isFirstItem = false;
            }
        } catch (SQLException e) { System.out.println("Error fetching feed: " + e.getMessage()); }
        
        jsonBuilder.append("]"); 
        return jsonBuilder.toString();
    }

    /* ========================================= */
    /* --- METHOD 8: GET SPECIFIC USER POSTS --- */
    /* ========================================= */
    public static String getUserPosts(String targetUsername, String currentUser) {
        
        /* [Targeted Query]: We added 'WHERE users.username = ?' so it ONLY grabs posts from the specific profile you are looking at! */
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = posts.id) AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = posts.id) AS comment_count, " +
                          "(SELECT COUNT(*) FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "WHERE users.username = ? " + // <-- THE CRITICAL FIX IS HERE
                          "ORDER BY posts.created_at DESC";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            // 1. Inject the logged-in user to calculate the Follow buttons accurately
            pstmt.setString(1, currentUser);
            
            // 2. Inject the target profile's username to lock the filter!
            pstmt.setString(2, targetUsername);
            
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                
                int postId = rs.getInt("id");
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                String text = rs.getString("content");
                String media = rs.getString("image_url");
                String time = rs.getString("created_at");
                int likes = rs.getInt("like_count");
                int comments = rs.getInt("comment_count");
                boolean isFollowing = rs.getInt("is_following") > 0;

                /* [Fallback Logic] */
                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                }
                
                if (text == null) { text = ""; }
                if (media == null) { media = ""; }

                jsonBuilder.append("{")
                           .append("\"id\":").append(postId).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"media\":\"").append(media).append("\",")
                           .append("\"likes\":").append(likes).append(",")
                           .append("\"commentCount\":").append(comments).append(",")
                           .append("\"isFollowing\":").append(isFollowing).append(",")
                           .append("\"timestamp\":\"").append(time).append("\"")
                           .append("}");
                isFirstItem = false;
            }
        } catch (SQLException e) { System.out.println("Error fetching user posts: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    /* ========================================= */
    /* --- METHOD 4: TOGGLE LIKE (THE NEW ENGINE) --- */
    /* ========================================= */
    public static String toggleLike(String identifier, int postId) {
        String findUserSQL = "SELECT id FROM users WHERE username = ? OR email = ?";
        String checkLikeSQL = "SELECT id FROM likes WHERE user_id = ? AND post_id = ?";
        String addLikeSQL = "INSERT INTO likes(user_id, post_id) VALUES(?, ?)";
        String removeLikeSQL = "DELETE FROM likes WHERE user_id = ? AND post_id = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {

            // 1. Find the User's ID
            findUserStmt.setString(1, identifier);
            findUserStmt.setString(2, identifier);
            ResultSet rsUser = findUserStmt.executeQuery();

            if (rsUser.next()) {
                int userId = rsUser.getInt("id");

                // 2. Check if they already liked this post
                try (PreparedStatement checkLikeStmt = conn.prepareStatement(checkLikeSQL)) {
                    checkLikeStmt.setInt(1, userId);
                    checkLikeStmt.setInt(2, postId);
                    ResultSet rsLike = checkLikeStmt.executeQuery();

                    if (rsLike.next()) {
                        // 3a. If they DID like it, remove the like! (Unlike)
                        try (PreparedStatement removeStmt = conn.prepareStatement(removeLikeSQL)) {
                            removeStmt.setInt(1, userId);
                            removeStmt.setInt(2, postId);
                            removeStmt.executeUpdate();
                            return "UNLIKED";
                        }
                    } else {
                        // 3b. If they DID NOT like it, save the like!
                        try (PreparedStatement addStmt = conn.prepareStatement(addLikeSQL)) {
                            addStmt.setInt(1, userId);
                            addStmt.setInt(2, postId);
                            addStmt.executeUpdate();
                            return "LIKED";
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error toggling like: " + e.getMessage());
        }
        return "ERROR";
    }
    /* ========================================= */
    /* --- METHOD 5: ADD A COMMENT --- */
    /* ========================================= */
    public static boolean addComment(String identifier, int postId, String content) {
        /* [Foreign Key Mapping: We must find the user's ID first, so we know EXACTLY who is commenting] */
        String findUserSQL = "SELECT id FROM users WHERE username = ? OR email = ?";
        String insertCommentSQL = "INSERT INTO comments(post_id, user_id, content) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement findUserStmt = conn.prepareStatement(findUserSQL)) {

            findUserStmt.setString(1, identifier);
            findUserStmt.setString(2, identifier);
            ResultSet rsUser = findUserStmt.executeQuery();

            if (rsUser.next()) {
                int userId = rsUser.getInt("id");

                try (PreparedStatement insertStmt = conn.prepareStatement(insertCommentSQL)) {
                    insertStmt.setInt(1, postId);
                    insertStmt.setInt(2, userId);
                    insertStmt.setString(3, content);
                    insertStmt.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error adding comment: " + e.getMessage());
        }
        return false;
    }

    /* ========================================= */
    /* --- METHOD 6: FETCH COMMENTS (STATE AWARE) --- */
    /* ========================================= */
    public static String getComments(int postId, String currentUser) {
        String querySQL = "SELECT users.username, comments.content, comments.created_at, " +
                          "(SELECT COUNT(*) FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = comments.user_id) AS is_following " +
                          "FROM comments JOIN users ON comments.user_id = users.id " +
                          "WHERE comments.post_id = ? ORDER BY comments.created_at ASC";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            pstmt.setString(1, currentUser);
            pstmt.setInt(2, postId);
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                
                String user = rs.getString("username");
                String text = rs.getString("content");
                String time = rs.getString("created_at");
                boolean isFollowing = rs.getInt("is_following") > 0;

                jsonBuilder.append("{")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"")).append("\",")
                           .append("\"isFollowing\":").append(isFollowing).append(",")
                           .append("\"timestamp\":\"").append(time).append("\"")
                           .append("}");
                isFirstItem = false;
            }
        } catch (SQLException e) { System.out.println("Error fetching comments: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }
    /* ========================================= */
    /* --- METHOD 7: SEARCH POSTS (WORD MATCH) --- */
    /* ========================================= */
    public static String searchPosts(String searchQuery, String currentUser) {
        
        /* [Exact Word Match Engine]: We use a 4-part OR statement to check if the word has spaces around it, is at the beginning, the end, or is the only word in the post! */
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = posts.id) AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = posts.id) AS comment_count, " +
                          "(SELECT COUNT(*) FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = posts.user_id) AS is_following " +
                          "FROM posts JOIN users ON posts.user_id = users.id " +
                          "WHERE (posts.content LIKE ? OR posts.content LIKE ? OR posts.content LIKE ? OR posts.content = ?) " +
                          "ORDER BY posts.created_at DESC LIMIT 15";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            // 1. Inject the currentUser for the Follow button math
            pstmt.setString(1, currentUser);
            
            // 2. The 4 variations for an exact word boundary!
            pstmt.setString(2, "% " + searchQuery + " %"); // Word is in the middle of a sentence
            pstmt.setString(3, searchQuery + " %");        // Word is at the very beginning of the post
            pstmt.setString(4, "% " + searchQuery);        // Word is at the very end of the post
            pstmt.setString(5, searchQuery);               // The post is literally just this exact word
            
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                
                int postId = rs.getInt("id");
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                String text = rs.getString("content");
                String media = rs.getString("image_url");
                String time = rs.getString("created_at");
                int likes = rs.getInt("like_count");
                int comments = rs.getInt("comment_count");
                boolean isFollowing = rs.getInt("is_following") > 0;

                /* [Fallback Logic] */
                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                }
                
                if (text == null) { text = ""; }
                if (media == null) { media = ""; }

                jsonBuilder.append("{")
                           .append("\"id\":").append(postId).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"media\":\"").append(media).append("\",")
                           .append("\"likes\":").append(likes).append(",")
                           .append("\"commentCount\":").append(comments).append(",")
                           .append("\"isFollowing\":").append(isFollowing).append(",")
                           .append("\"timestamp\":\"").append(time).append("\"")
                           .append("}");
                isFirstItem = false;
            }
        } catch (SQLException e) { System.out.println("Error searching posts: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }
    /* ========================================= */
    /* --- METHOD 9: GET FOLLOWING FEED --- */
    /* ========================================= */
    public static String getFollowingFeed(String currentUser) {
        
        /* [Relational Join]: We link the 'posts' table directly to the 'followers' table. We tell the database to ONLY return a post if the author's ID exists in the current user's 'following' list! */
        String querySQL = "SELECT posts.id, users.username, users.profile_pic_url, posts.content, posts.image_url, posts.created_at, " +
                          "(SELECT COUNT(*) FROM likes WHERE likes.post_id = posts.id) AS like_count, " +
                          "(SELECT COUNT(*) FROM comments WHERE comments.post_id = posts.id) AS comment_count " +
                          "FROM posts " +
                          "JOIN users ON posts.user_id = users.id " +
                          "JOIN followers ON posts.user_id = followers.following_id " +
                          "WHERE followers.follower_id = (SELECT id FROM users WHERE username = ?) " +
                          "ORDER BY posts.created_at DESC";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            // We inject the logged-in user so the database knows whose follower list to check!
            pstmt.setString(1, currentUser);
            
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                
                int postId = rs.getInt("id");
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                String text = rs.getString("content");
                String media = rs.getString("image_url");
                String time = rs.getString("created_at");
                int likes = rs.getInt("like_count");
                int comments = rs.getInt("comment_count");
                
                /* [Logical Bypass]: Because this engine ONLY fetches posts from people you follow, we don't need to ask the database if you are following them. We already know the answer is true! */
                boolean isFollowing = true; 

                /* [Fallback Logic] */
                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
                }
                
                if (text == null) { text = ""; }
                if (media == null) { media = ""; }

                // Package the exact same JSON format as your Home Feed so the frontend doesn't break!
                jsonBuilder.append("{")
                           .append("\"id\":").append(postId).append(",")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"content\":\"").append(text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\",")
                           .append("\"media\":\"").append(media).append("\",")
                           .append("\"likes\":").append(likes).append(",")
                           .append("\"commentCount\":").append(comments).append(",")
                           .append("\"isFollowing\":").append(isFollowing).append(",")
                           .append("\"timestamp\":\"").append(time).append("\"")
                           .append("}");
                isFirstItem = false;
            }
        } catch (SQLException e) { System.out.println("Error fetching following feed: " + e.getMessage()); }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }
}