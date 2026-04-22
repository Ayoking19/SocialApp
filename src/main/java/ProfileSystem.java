package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProfileSystem {

    /* ========================================= */
    /* --- METHOD 1: FETCH USER PROFILE --- */
    /* ========================================= */
    public static String getUserProfile(String identifier) {
        
        /* [SQL Upgrade]: We added 'profile_pic_url' to the SELECT statement! */
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
                String avatar = rs.getString("profile_pic_url"); // <-- GRABBING THE IMAGE
                int postCount = rs.getInt("post_count");
                
                if (bio == null || bio.isEmpty()) {
                    bio = "This user hasn't written a bio yet.";
                }

                /* [Fallback Logic: If the user hasn't uploaded a picture yet, we safely default back to your custom colored initials!] */
                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = "https://ui-avatars.com/api/?name=" + username + "&background=1e293b&color=00e676";
                }

                return "{" +
                       "\"username\":\"" + username + "\"," +
                       "\"bio\":\"" + bio + "\"," +
                       "\"avatar\":\"" + avatar + "\"," + // <-- PACKAGING THE IMAGE INTO THE JSON
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
    /* ========================================= */
    /* --- METHOD 2: UPDATE USER BIO --- */
    /* ========================================= */
    public static boolean updateBio(String username, String newBio) {
        
        /* [SQL UPDATE]: We target the 'users' table, SET the bio column to new text, but ONLY WHERE the username matches! */
        String updateSQL = "UPDATE users SET bio = ? WHERE username = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            pstmt.setString(1, newBio);
            pstmt.setString(2, username);
            
            // executeUpdate() is used whenever we alter the database (Insert, Update, Delete)
            int rowsAffected = pstmt.executeUpdate();
            
            // If it successfully changed at least 1 row, it worked!
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error updating bio: " + e.getMessage());
            return false;
        }
    }
    /* ========================================= */
    /* --- METHOD 3: UPDATE PROFILE PICTURE --- */
    /* ========================================= */
    public static boolean updateProfilePicture(String username, String base64Image) {
        
        /* [SQL UPDATE]: We target the 'users' table and SET your specific 'profile_pic_url' column to the new image text! */
        String updateSQL = "UPDATE users SET profile_pic_url = ? WHERE username = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            pstmt.setString(1, base64Image);
            pstmt.setString(2, username);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println("Error updating avatar: " + e.getMessage());
            return false;
        }
    }
    /* ========================================= */
    /* --- METHOD 4: SEARCH USERS (FUZZY MATCH) --- */
    /* ========================================= */
    public static String searchUsers(String searchQuery) {
        
        /* [Fuzzy Matching]: We use the LIKE operator with % wildcards to find any username that contains the search text! */
        String querySQL = "SELECT username, profile_pic_url, bio FROM users WHERE username LIKE ? LIMIT 15";

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[");

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {

            // We wrap the user's text in wildcards. If they type "ay", it searches for "%ay%"
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
                
                // Fallback for missing profile pictures
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