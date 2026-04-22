package main.java;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginSystem {

    /* Method 1: Registering with an Email */
    public static boolean registerUser(String username, String email, String password) {
        String insertSQL = "INSERT INTO users(username, email, password) VALUES(?, ?, ?)";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, email); // Saving the email
            pstmt.setString(3, password);
            
            pstmt.executeUpdate();
            System.out.println("User " + username + " registered successfully!");
            return true;

        } catch (SQLException e) {
            System.out.println("Registration failed. Username or Email might already exist.");
            return false;
        }
    }

    /* Method 2: Authenticating with Username OR Email */
    public static boolean authenticateUser(String identifier, String password) {
        /* 
           [Logical OR Operator]: The 'OR' keyword tells the database to find the user 
           if the text they typed matches the 'username' column OR the 'email' column. 
        */
        String querySQL = "SELECT * FROM users WHERE (username = ? OR email = ?) AND password = ?";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(querySQL)) {
            
            pstmt.setString(1, identifier); // Checks against the username column
            pstmt.setString(2, identifier); // Checks against the email column
            pstmt.setString(3, password);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                System.out.println("Login successful!");
                return true;
            } else {
                return false;
            }

        } catch (SQLException e) {
            System.out.println("Authentication error: " + e.getMessage());
            return false;
        }
    }
}