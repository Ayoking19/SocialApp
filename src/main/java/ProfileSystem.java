package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProfileSystem {

    public static String getUserProfile(String identifier) {
        // THE FIX: Added pinned_post_id to the query so the frontend knows what post to display!
        String querySQL = "SELECT username, bio, profile_pic_url, pinned_post_id, " +
                          "(SELECT COUNT(*) FROM posts WHERE posts.user_id = users.id) AS post_count " +
                          "FROM users WHERE username = ? OR email = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            pstmt.setString(1, identifier);
            pstmt.setString(2, identifier);
            
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String username = rs.getString("username");
                String bio = rs.getString("bio");
                String avatar = rs.getString("profile_pic_url"); 
                int postCount = rs.getInt("post_count");
                
                // THE FIX: Safely extract the Integer [a whole number variable type that allows for null values]
                Integer pinnedPostId = (Integer) rs.getObject("pinned_post_id");
                
                if (bio == null || bio.isEmpty()) {
                    bio = "This user hasn't written a bio yet.";
                }

                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = MessageSystem.DEFAULT_AVATAR;
                }

                String pinnedStr = (pinnedPostId != null) ? String.valueOf(pinnedPostId) : "null";

                return "{" +
                       "\"username\":\"" + username + "\"," +
                       "\"bio\":\"" + bio.replace("\"", "\\\"").replace("\n", "\\n") + "\"," +
                       "\"avatar\":\"" + avatar + "\"," +
                       "\"postCount\":" + postCount + "," +
                       "\"pinnedPostId\":" + pinnedStr +
                       "}";
            }
        } catch (SQLException e) {
            System.out.println("Error fetching profile: " + e.getMessage());
        }
        return "{\"error\": \"User not found\"}";
    }

    public static boolean updateBio(String username, String newBio) {
        String sql = "UPDATE users SET bio = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newBio);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating bio: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateProfilePicture(String username, String base64Image) {
        String sql = "UPDATE users SET profile_pic_url = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, base64Image);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating avatar: " + e.getMessage());
            return false;
        }
    }

    // THE FIX: The backend method that physically updates the MySQL database with the user's pinned post choice
    public static boolean setPinnedPost(String username, Integer postId) {
        String sql = "UPDATE users SET pinned_post_id = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            if (postId == null) {
                pstmt.setNull(1, java.sql.Types.INTEGER);
            } else {
                pstmt.setInt(1, postId);
            }
            
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error setting pinned post: " + e.getMessage());
            return false;
        }
    }

    public static String searchUsers(String searchQuery) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        String querySQL = "SELECT username, profile_pic_url, bio FROM users WHERE username LIKE ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            pstmt.setString(1, "%" + searchQuery + "%");
            ResultSet rs = pstmt.executeQuery();

            boolean isFirstItem = true;
            while (rs.next()) {
                if (!isFirstItem) { jsonBuilder.append(","); }
                
                String user = rs.getString("username");
                String avatar = rs.getString("profile_pic_url");
                String bio = rs.getString("bio");

                if (bio == null || bio.isEmpty()) { 
                    bio = "This user hasn't written a bio yet."; 
                }
                
                if (avatar == null || avatar.trim().isEmpty()) {
                    // THE FIX: Globally linking to our native SVG placeholder
                    avatar = MessageSystem.DEFAULT_AVATAR;
                }

                jsonBuilder.append("{")
                           .append("\"username\":\"").append(user).append("\",")
                           .append("\"avatar\":\"").append(avatar).append("\",")
                           .append("\"bio\":\"").append(bio.replace("\"", "\\\"").replace("\n", "\\n")).append("\"")
                           .append("}");
                isFirstItem = false;
            }
        } catch (SQLException e) {
            System.out.println("Error searching users: " + e.getMessage());
        }
        
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }
}