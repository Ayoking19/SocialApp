package main.java;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    
    // THE FIX: Injected your precise Railway internal network routing credentials
    // THE FIX: Your permanent Aiven MySQL database routing
    private static final String DATABASE_URL = "jdbc:mysql://mysql-dbd948d-oluyemiayomikun689-4378.j.aivencloud.com:14711/defaultdb?sslMode=REQUIRED";
    private static final String DB_USER = "avnadmin";
    private static final String DB_PASSWORD = "AVNS_JPZBPVEMHsknCarmRnH";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DATABASE_URL, DB_USER, DB_PASSWORD);
            System.out.println("Connection to Railway MySQL has been established successfully.");
        } catch (SQLException e) {
            System.out.println("Connection Error: " + e.getMessage());
        }
        return conn;
    }

    public static void initializeDatabase() {
        // THE FIX: Translated all SQLite 'AUTOINCREMENT' commands into MySQL 'AUTO_INCREMENT' syntax
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "username TEXT NOT NULL,"
                + "email TEXT NOT NULL,"
                + "password TEXT NOT NULL,"
                + "bio TEXT,"
                + "profile_pic_url TEXT,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE(username(255)),"
                + "UNIQUE(email(255))"
                + ");";
         
        String createPostsTable = "CREATE TABLE IF NOT EXISTS posts ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "image_url TEXT,"
                + "video_url TEXT,"
                + "parent_post_id INT DEFAULT NULL," 
                + "parent_comment_id INT DEFAULT NULL," 
                + "is_edited BOOLEAN DEFAULT 0," 
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createCommentsTable = "CREATE TABLE IF NOT EXISTS comments ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "post_id INT NOT NULL,"
                + "user_id INT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "parent_comment_id INT DEFAULT NULL," 
                + "is_edited BOOLEAN DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
        
        String createLikesTable = "CREATE TABLE IF NOT EXISTS likes ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "post_id INT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE(user_id, post_id)" 
                + ");";            

        String createCommentLikesTable = "CREATE TABLE IF NOT EXISTS comment_likes ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "comment_id INT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE(user_id, comment_id)" 
                + ");";
                      
        String createFollowersTable = "CREATE TABLE IF NOT EXISTS followers ("
                + "follower_id INT NOT NULL,"
                + "following_id INT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (follower_id, following_id)"
                + ");";

        String createNotificationsTable = "CREATE TABLE IF NOT EXISTS notifications ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "recipient_user TEXT NOT NULL,"
                + "actor_user TEXT NOT NULL,"
                + "type TEXT NOT NULL," 
                + "post_id INT,"    
                + "is_read BOOLEAN DEFAULT 0,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "sender_id INT NOT NULL,"
                + "receiver_id INT,"
                + "content TEXT,"
                + "image_url TEXT," 
                + "is_edited BOOLEAN DEFAULT 0,"
                + "is_forwarded BOOLEAN DEFAULT 0,"
                + "deleted_by_receiver BOOLEAN DEFAULT 0,"
                + "reply_to_id INT DEFAULT NULL,"
                + "is_read BOOLEAN DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createBlockedUsersTable = "CREATE TABLE IF NOT EXISTS blocked_users ("
                + "blocker_id INT NOT NULL,"
                + "blocked_id INT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (blocker_id, blocked_id)"
                + ");";

        String createSavedPostsTable = "CREATE TABLE IF NOT EXISTS saved_posts ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "post_id INT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE(user_id, post_id)"
                + ");";

        String createSavedCommentsTable = "CREATE TABLE IF NOT EXISTS saved_comments ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "comment_id INT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE(user_id, comment_id)"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createUsersTable);
            stmt.execute(createPostsTable);
            stmt.execute(createCommentsTable);
            stmt.execute(createLikesTable);
            stmt.execute(createCommentLikesTable); 
            stmt.execute(createFollowersTable);
            stmt.execute(createNotificationsTable); 
            stmt.execute(createMessagesTable);
            stmt.execute(createBlockedUsersTable);
            stmt.execute(createSavedPostsTable);
            stmt.execute(createSavedCommentsTable);
            
            /* ========================================= */
            /* --- THE SCHEMA MIGRATION ENGINE ---       */
            /* ========================================= */
            try { stmt.execute("ALTER TABLE posts ADD COLUMN parent_post_id INT DEFAULT NULL"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE posts ADD COLUMN is_edited BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE comments ADD COLUMN is_edited BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE posts ADD COLUMN parent_comment_id INT DEFAULT NULL"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN image_url TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN is_edited BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN is_forwarded BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN deleted_by_receiver BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN reply_to_id INT DEFAULT NULL"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE comments ADD COLUMN parent_comment_id INT DEFAULT NULL"); } catch (SQLException e) {} 
            
            System.out.println("All MySQL database tables have been successfully initialized!");
        } catch (SQLException e) {
            System.out.println("Error creating MySQL tables: " + e.getMessage());
        }
    }
}