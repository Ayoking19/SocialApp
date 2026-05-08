package main.java;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BlockSystem {

    // Returns "BLOCKED", "UNBLOCKED", or "ERROR"
    public static String toggleBlock(String currentUser, String targetUser) {
        String checkSQL = "SELECT 1 FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = ?) AND blocked_id = (SELECT id FROM users WHERE username = ?)";
        String blockSQL = "INSERT INTO blocked_users (blocker_id, blocked_id) VALUES ((SELECT id FROM users WHERE username = ?), (SELECT id FROM users WHERE username = ?))";
        String unblockSQL = "DELETE FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = ?) AND blocked_id = (SELECT id FROM users WHERE username = ?)";
        
        // When you block someone, it must forcefully destroy any follow relationship in both directions!
        String removeFollow1 = "DELETE FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = (SELECT id FROM users WHERE username = ?)";
        String removeFollow2 = "DELETE FROM followers WHERE follower_id = (SELECT id FROM users WHERE username = ?) AND following_id = (SELECT id FROM users WHERE username = ?)";

        try (Connection conn = DatabaseManager.connect()) {
            boolean isCurrentlyBlocked = false;
            try (PreparedStatement pstmt = conn.prepareStatement(checkSQL)) {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, targetUser);
                isCurrentlyBlocked = pstmt.executeQuery().next();
            }

            if (isCurrentlyBlocked) {
                try (PreparedStatement pstmt = conn.prepareStatement(unblockSQL)) {
                    pstmt.setString(1, currentUser);
                    pstmt.setString(2, targetUser);
                    pstmt.executeUpdate();
                    return "UNBLOCKED";
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(blockSQL)) {
                    pstmt.setString(1, currentUser);
                    pstmt.setString(2, targetUser);
                    pstmt.executeUpdate();
                }
                // Destroy follows
                try (PreparedStatement p1 = conn.prepareStatement(removeFollow1)) {
                    p1.setString(1, currentUser); p1.setString(2, targetUser); p1.executeUpdate();
                }
                try (PreparedStatement p2 = conn.prepareStatement(removeFollow2)) {
                    p2.setString(1, targetUser); p2.setString(2, currentUser); p2.executeUpdate();
                }
                return "BLOCKED";
            }
        } catch (SQLException e) {
            System.out.println("Block Error: " + e.getMessage());
            return "ERROR";
        }
    }

    // Returns "BLOCKED_BY_YOU", "BLOCKED_YOU", or "NONE"
    public static String getBlockStatus(String currentUser, String targetUser) {
        String checkYouBlockedThem = "SELECT 1 FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = ?) AND blocked_id = (SELECT id FROM users WHERE username = ?)";
        String checkTheyBlockedYou = "SELECT 1 FROM blocked_users WHERE blocker_id = (SELECT id FROM users WHERE username = ?) AND blocked_id = (SELECT id FROM users WHERE username = ?)";

        try (Connection conn = DatabaseManager.connect()) {
            try (PreparedStatement p1 = conn.prepareStatement(checkYouBlockedThem)) {
                p1.setString(1, currentUser); p1.setString(2, targetUser);
                if (p1.executeQuery().next()) return "BLOCKED_BY_YOU";
            }
            try (PreparedStatement p2 = conn.prepareStatement(checkTheyBlockedYou)) {
                p2.setString(1, targetUser); p2.setString(2, currentUser);
                if (p2.executeQuery().next()) return "BLOCKED_YOU";
            }
        } catch (SQLException e) {
            System.out.println("Status Error: " + e.getMessage());
        }
        return "NONE";
    }
}