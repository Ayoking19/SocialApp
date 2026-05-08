package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SettingsSystem {

    /* ========================================= */
    /* --- METHOD 1: CHANGE PASSWORD --- */
    /* ========================================= */
    public static boolean updatePassword(String username, String currentPassword, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPassword);
            pstmt.setString(2, username);
            pstmt.setString(3, currentPassword);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Password Update Error: " + e.getMessage());
            return false;
        }
    }

    /* ========================================= */
    /* --- METHOD 2: DELETE ACCOUNT (CORRECTED) --- */
    /* ========================================= */
    public static boolean deleteAccount(String username) {
        Connection conn = null;
        System.out.println("--- Starting Precise Deletion for: " + username + " ---");
        
        try {
            conn = DatabaseManager.connect();
            // [Auto-Commit OFF]: This locks the database so we can perform multiple steps safely.
            conn.setAutoCommit(false); 

            // STEP 1: Get the user's INTEGER ID (The Missing Key!)
            int userId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("id");
                }
            }

            if (userId == -1) {
                System.out.println("Error: User not found.");
                return false;
            }

            // STEP 2: The Corrected Relational Cleanup
            // We use the integer IDs for tables that rely on the user_id foreign key
            executeIdCleanup(conn, "DELETE FROM followers WHERE follower_id = ? OR following_id = ?", userId, true);
            executeIdCleanup(conn, "DELETE FROM posts WHERE user_id = ?", userId, false);
            
            // [Type Correction: Swapped from String to Integer ID for likes and comments]
            executeIdCleanup(conn, "DELETE FROM likes WHERE user_id = ?", userId, false);
            executeIdCleanup(conn, "DELETE FROM comments WHERE user_id = ?", userId, false);
            
            // Safe fallback for the notifications table which still uses the username string
            executeStringCleanup(conn, "DELETE FROM notifications WHERE recipient_user = ? OR actor_user = ?", username, true);
            // STEP 3: Final removal of the user
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
                pstmt.setString(1, username);
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    conn.commit(); 
                    System.out.println("SUCCESS: User " + username + " fully erased.");
                    return true;
                }
            }
            
            return false;

        } catch (SQLException e) {
            System.out.println("DELETION FAILED: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* Ignored */ }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { /* Ignored */ }
            }
        }
    }

    /**
     * Helper to run deletions using Integer IDs
     */
    private static void executeIdCleanup(Connection conn, String sql, int id, boolean twoParams) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            if (twoParams) pstmt.setInt(2, id);
            pstmt.executeUpdate();
            System.out.println("Cleaned up table via ID: " + sql.split(" ")[2]);
        } catch (SQLException e) {
            System.out.println("Skipped table cleanup: " + e.getMessage());
        }
    }

    /**
     * Helper to run deletions using Usernames
     */
    private static void executeStringCleanup(Connection conn, String sql, String str, boolean twoParams) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, str);
            if (twoParams) pstmt.setString(2, str);
            pstmt.executeUpdate();
            System.out.println("Cleaned up table via Username: " + sql.split(" ")[2]);
        } catch (SQLException e) {
            System.out.println("Skipped table cleanup: " + e.getMessage());
        }
    }

    /* ========================================= */
    /* --- METHOD 3: GHOST RECORD CLEANUP --- */
    /* ========================================= */
    /**
     * Scans the database for Orphan Records and deletes them.
     * [Referential Integrity Sweep: A maintenance task that forces a database to clean up 
     * relationship tables so they perfectly match the main data tables]
     */
    public static void removeGhostFollowers() {
        // [NOT IN Clause]: An SQL operator that isolates data that does not have a match in another list
        String sql = "DELETE FROM followers WHERE " +
                     "follower_id NOT IN (SELECT id FROM users) OR " +
                     "following_id NOT IN (SELECT id FROM users)";
        
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            int rowsDeleted = pstmt.executeUpdate();
            System.out.println("GHOSTS BUSTED: Successfully erased " + rowsDeleted + " orphan records from the database!");
            
        } catch (SQLException e) {
            System.out.println("Ghost Cleanup Error: " + e.getMessage());
        }
    }
    /* ========================================= */
    /* --- METHOD 4: GHOST ACTIVITY CLEANUP --- */
    /* ========================================= */
    /**
     * Scans the database for Orphan Records in likes, comments, and posts, and deletes them.
     * [Referential Integrity Sweep: A maintenance task that forces a database to clean up 
     * secondary tables so they perfectly match the main data tables]
     */
    public static void removeGhostActivity() {
        // [NOT IN Clause]: An SQL operator that isolates data that does not have a match in another list
        String deleteLikes = "DELETE FROM likes WHERE user_id NOT IN (SELECT id FROM users)";
        String deleteComments = "DELETE FROM comments WHERE user_id NOT IN (SELECT id FROM users)";
        String deletePosts = "DELETE FROM posts WHERE user_id NOT IN (SELECT id FROM users)";
        
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmtLikes = conn.prepareStatement(deleteLikes);
             PreparedStatement stmtComments = conn.prepareStatement(deleteComments);
             PreparedStatement stmtPosts = conn.prepareStatement(deletePosts)) {
            
            int likesDeleted = stmtLikes.executeUpdate();
            int commentsDeleted = stmtComments.executeUpdate();
            int postsDeleted = stmtPosts.executeUpdate();
            
            System.out.println("GHOST ACTIVITY BUSTED: Erased " + likesDeleted + " likes, " + 
                               commentsDeleted + " comments, and " + postsDeleted + " posts.");
            
        } catch (SQLException e) {
            System.out.println("Ghost Activity Cleanup Error: " + e.getMessage());
        }
    }
    /* ========================================= */
    /* --- METHOD 5: CHANGE USERNAME ---         */
    /* ========================================= */
    public static String updateUsername(String currentUsername, String newUsername) {
        if (currentUsername.equals(newUsername)) return "SAME_USERNAME";
        if (newUsername == null || newUsername.trim().isEmpty() || newUsername.contains(" ")) return "INVALID_FORMAT";

        // Step 1: Ensure the new username isn't already taken
        String checkSql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, newUsername);
            if (checkStmt.executeQuery().next()) {
                return "USERNAME_TAKEN";
            }
        } catch (SQLException e) { return "ERROR"; }

        // Step 2: Update the Users table and the Notifications table seamlessly
        String updateUsers = "UPDATE users SET username = ? WHERE username = ?";
        String updateNotifActor = "UPDATE notifications SET actor_user = ? WHERE actor_user = ?";
        String updateNotifRecipient = "UPDATE notifications SET recipient_user = ? WHERE recipient_user = ?";

        try (Connection conn = DatabaseManager.connect()) {
            try (PreparedStatement stmt1 = conn.prepareStatement(updateUsers)) {
                stmt1.setString(1, newUsername); stmt1.setString(2, currentUsername); stmt1.executeUpdate();
            }
            try (PreparedStatement stmt2 = conn.prepareStatement(updateNotifActor)) {
                stmt2.setString(1, newUsername); stmt2.setString(2, currentUsername); stmt2.executeUpdate();
            }
            try (PreparedStatement stmt3 = conn.prepareStatement(updateNotifRecipient)) {
                stmt3.setString(1, newUsername); stmt3.setString(2, currentUsername); stmt3.executeUpdate();
            }
            return "SUCCESS";
        } catch (SQLException e) {
            System.out.println("Update Username Error: " + e.getMessage());
            return "ERROR";
        }
    }
}