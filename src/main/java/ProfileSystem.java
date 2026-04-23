package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProfileSystem {

    public static String getUserProfile(String identifier) {
        String querySQL = "SELECT username, bio, profile_pic_url, " +
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
                
                if (bio == null || bio.isEmpty()) {
                    bio = "This user hasn't written a bio yet.";
                }

                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = "https://ui-avatars.com/api/?name=" + username + "&background=1e293b&color=00e676";
                }

                return "{" +
                       "\"username\":\"" + username + "\"," +
                       "\"bio\":\"" + bio + "\"," +
                       "\"avatar\":\"" + avatar + "\"," + 
                       "\"followers\":0," +   
                       "\"following\":0," +   
                       "\"postCount\":" + postCount +
                       "}";
            }
        } catch (SQLException e) {
            System.out.println("Error fetching profile: " + e.getMessage());
        }
        
        return "ERROR";
    }

    public static boolean updateBio(String username, String newBio) {
        String updateSQL = "UPDATE users SET bio = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
            pstmt.setString(1, newBio);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating bio: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateProfilePicture(String username, String base64Image) {
        String updateSQL = "UPDATE users SET profile_pic_url = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
            pstmt.setString(1, base64Image);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating avatar: " + e.getMessage());
            return false;
        }
    }

    public static String searchUsers(String searchQuery) {
        String querySQL = "SELECT username, profile_pic_url, bio FROM users WHERE username LIKE ? LIMIT 15";
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

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
                    avatar = "https://ui-avatars.com/api/?name=" + user + "&background=1e293b&color=00e676";
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