package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FollowSystem {

    /* ========================================= */
    /* --- METHOD 1: GET INTERNAL USER ID --- */
    /* ========================================= */
    private static int getUserId(String identifier) {
        String sql = "SELECT id FROM users WHERE username = ? OR email = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, identifier);
            pstmt.setString(2, identifier);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { System.out.println(e.getMessage()); }
        return -1;
    }

    /* ========================================= */
    /* --- METHOD 2: TOGGLE FOLLOW STATUS --- */
    /* ========================================= */
    public static String toggleFollow(String currentUsername, String targetUsername) {
        int followerId = getUserId(currentUsername);
        int targetId = getUserId(targetUsername);

        if (followerId == -1 || targetId == -1 || followerId == targetId) return "ERROR"; // Cannot follow yourself!

        String checkSQL = "SELECT * FROM followers WHERE follower_id = ? AND following_id = ?";
        String followSQL = "INSERT INTO followers(follower_id, following_id) VALUES(?, ?)";
        String unfollowSQL = "DELETE FROM followers WHERE follower_id = ? AND following_id = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
            
            checkStmt.setInt(1, followerId);
            checkStmt.setInt(2, targetId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // If the record exists, Unfollow them!
                try (PreparedStatement unfollowStmt = conn.prepareStatement(unfollowSQL)) {
                    unfollowStmt.setInt(1, followerId);
                    unfollowStmt.setInt(2, targetId);
                    unfollowStmt.executeUpdate();
                    return "UNFOLLOWED";
                }
            } else {
                // If it doesn't exist, Follow them!
                try (PreparedStatement followStmt = conn.prepareStatement(followSQL)) {
                    followStmt.setInt(1, followerId);
                    followStmt.setInt(2, targetId);
                    followStmt.executeUpdate();
                    return "FOLLOWED";
                }
            }
        } catch (SQLException e) { System.out.println("Follow Error: " + e.getMessage()); }
        return "ERROR";
    }

    /* ========================================= */
    /* --- METHOD 3: THE RELATIONSHIP SCANNER --- */
    /* ========================================= */
    public static String getProfileRelations(String currentUsername, String targetUsername) {
        int currentId = getUserId(currentUsername);
        int targetId = getUserId(targetUsername);

        int followersCount = 0;
        int followingCount = 0;
        boolean isFollowing = false;
        boolean followsYou = false;

        /* [Complex Queries: We are asking the database for 4 distinct pieces of data to support your UI requests] */
        String countFollowersSQL = "SELECT COUNT(*) AS count FROM followers WHERE following_id = ?";
        String countFollowingSQL = "SELECT COUNT(*) AS count FROM followers WHERE follower_id = ?";
        String checkIsFollowingSQL = "SELECT 1 FROM followers WHERE follower_id = ? AND following_id = ?";
        String checkFollowsYouSQL = "SELECT 1 FROM followers WHERE follower_id = ? AND following_id = ?";

        try (Connection conn = DatabaseManager.connect()) {
            // 1. How many followers does the target have?
            try (PreparedStatement stmt = conn.prepareStatement(countFollowersSQL)) {
                stmt.setInt(1, targetId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) followersCount = rs.getInt("count");
            }
            // 2. How many people is the target following?
            try (PreparedStatement stmt = conn.prepareStatement(countFollowingSQL)) {
                stmt.setInt(1, targetId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) followingCount = rs.getInt("count");
            }
            // 3. Do I follow them?
            try (PreparedStatement stmt = conn.prepareStatement(checkIsFollowingSQL)) {
                stmt.setInt(1, currentId);
                stmt.setInt(2, targetId);
                isFollowing = stmt.executeQuery().next();
            }
            // 4. Do they follow me? (Your brilliant UI suggestion!)
            try (PreparedStatement stmt = conn.prepareStatement(checkFollowsYouSQL)) {
                stmt.setInt(1, targetId);
                stmt.setInt(2, currentId);
                followsYou = stmt.executeQuery().next();
            }
            
            // Package all 4 facts into a JSON string
            return "{" +
                   "\"followers\":" + followersCount + "," +
                   "\"following\":" + followingCount + "," +
                   "\"isFollowing\":" + isFollowing + "," +
                   "\"followsYou\":" + followsYou +
                   "}";

        } catch (SQLException e) { System.out.println("Relation Error: " + e.getMessage()); }
        return "ERROR";
    }
}