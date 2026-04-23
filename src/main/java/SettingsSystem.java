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
            // We now use the actual table name ('followers') and the integer IDs!
            executeIdCleanup(conn, "DELETE FROM followers WHERE follower_id = ? OR following_id = ?", userId, true);
            
            // Fixing 'posts' since ProfileSystem shows it also uses 'user_id'
            executeIdCleanup(conn, "DELETE FROM posts WHERE user_id = ?", userId, false);

            // Safe fallbacks for other tables based on username
            executeStringCleanup(conn, "DELETE FROM likes WHERE username = ?", username, false);
            executeStringCleanup(conn, "DELETE FROM comments WHERE username = ?", username, false);
            executeStringCleanup(conn, "DELETE FROM notifications WHERE recipient = ? OR actor = ?", username, true);

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
}