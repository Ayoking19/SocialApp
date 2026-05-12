package main.java;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginSystem {

    public static String decodeGoogleJWT(String jwtToken) {
        try {
            // Split the token to get the payload
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return "ERROR";
            
            // Decode the Base64 payload
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            
            String email = extractJWTValue(payload, "email");
            String name = extractJWTValue(payload, "name");
            
            return processGoogleLogin(email, name);
        } catch (Exception e) { return "ERROR"; }
    }

    private static String extractJWTValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return "";
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        return json.substring(startIndex, endIndex);
    }

    private static String processGoogleLogin(String email, String name) {
        // 1. If user already exists, log them in instantly
        String checkSQL = "SELECT username FROM users WHERE email = ?";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(checkSQL)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("username");
        } catch (SQLException e) { System.out.println(e.getMessage()); }

        // 2. If new user, create a username from their email prefix (e.g., "john.doe" from "john.doe@gmail.com")
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_.]", "").toLowerCase();
        String finalUsername = baseUsername;

        // 3. Ensure the username is completely unique
        String verifyUniqueSQL = "SELECT 1 FROM users WHERE username = ?";
        int counter = 1;
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(verifyUniqueSQL)) {
            while (true) {
                pstmt.setString(1, finalUsername);
                if (!pstmt.executeQuery().next()) break; // Username is free!
                finalUsername = baseUsername + counter;  // If taken, append a number
                counter++;
            }
        } catch (SQLException e) {}

        // 4. Register the new user! (We save 'GOOGLE_OAUTH' as the password since they don't need one)
        String insertSQL = "INSERT INTO users(username, email, password) VALUES(?, ?, 'GOOGLE_OAUTH')";
        try (Connection conn = DatabaseManager.connect(); PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, finalUsername);
            pstmt.setString(2, email);
            pstmt.executeUpdate();
            
            // [THE FIX]: Send a flag to the frontend letting it know this is a brand new account!
            return "NEW_USER:" + finalUsername; 
        } catch (SQLException e) { return "ERROR"; }
    }
}