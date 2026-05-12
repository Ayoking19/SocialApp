package main.java;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    
    private static final String DATABASE_URL = "jdbc:sqlite:social_network.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DATABASE_URL);
            System.out.println("Connection to SQLite has been established.");
        } catch (SQLException e) {
            System.out.println("Connection Error: " + e.getMessage());
        }
        return conn;
    }

    public static void initializeDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "email TEXT NOT NULL UNIQUE,"
                + "password TEXT NOT NULL,"
                + "bio TEXT,"
                + "profile_pic_url TEXT,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
         
        String createPostsTable = "CREATE TABLE IF NOT EXISTS posts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "content TEXT NOT NULL,"
                + "image_url TEXT,"
                + "video_url TEXT,"
                + "parent_post_id INTEGER DEFAULT NULL," 
                + "parent_comment_id INTEGER DEFAULT NULL," 
                + "is_edited BOOLEAN DEFAULT 0," 
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (user_id) REFERENCES users(id),"
                + "FOREIGN KEY (parent_post_id) REFERENCES posts(id),"
                + "FOREIGN KEY (parent_comment_id) REFERENCES comments(id)" 
                + ");";

        String createCommentsTable = "CREATE TABLE IF NOT EXISTS comments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "post_id INTEGER NOT NULL,"
                + "user_id INTEGER NOT NULL,"
                + "content TEXT NOT NULL,"
                + "parent_comment_id INTEGER DEFAULT NULL," // [NEW]: Threaded Replies
                + "is_edited BOOLEAN DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (post_id) REFERENCES posts(id),"
                + "FOREIGN KEY (user_id) REFERENCES users(id),"
                + "FOREIGN KEY (parent_comment_id) REFERENCES comments(id)" // [NEW]: Self-Referential Foreign Key
                + ");";
        
        String createLikesTable = "CREATE TABLE IF NOT EXISTS likes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "post_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (user_id) REFERENCES users(id),"
                + "FOREIGN KEY (post_id) REFERENCES posts(id),"
                + "UNIQUE(user_id, post_id)" 
                + ");";            

        String createCommentLikesTable = "CREATE TABLE IF NOT EXISTS comment_likes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "comment_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (user_id) REFERENCES users(id),"
                + "FOREIGN KEY (comment_id) REFERENCES comments(id),"
                + "UNIQUE(user_id, comment_id)" 
                + ");";
                      
        String createFollowersTable = "CREATE TABLE IF NOT EXISTS followers ("
                + "follower_id INTEGER NOT NULL,"
                + "following_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (follower_id) REFERENCES users(id),"
                + "FOREIGN KEY (following_id) REFERENCES users(id),"
                + "PRIMARY KEY (follower_id, following_id)"
                + ");";

        String createNotificationsTable = "CREATE TABLE IF NOT EXISTS notifications ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "recipient_user TEXT NOT NULL,"
                + "actor_user TEXT NOT NULL,"
                + "type TEXT NOT NULL," 
                + "post_id INTEGER,"    
                + "is_read BOOLEAN DEFAULT 0,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sender_id INTEGER NOT NULL,"
                + "receiver_id INTEGER,"
                + "content TEXT,"
                + "image_url TEXT," 
                + "is_edited BOOLEAN DEFAULT 0,"
                + "is_forwarded BOOLEAN DEFAULT 0,"
                + "deleted_by_receiver BOOLEAN DEFAULT 0,"
                + "reply_to_id INTEGER DEFAULT NULL,"
                + "is_read BOOLEAN DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (sender_id) REFERENCES users(id),"
                + "FOREIGN KEY (receiver_id) REFERENCES users(id),"
                + "FOREIGN KEY (reply_to_id) REFERENCES messages(id)"
                + ");";

        // --- NEW: THE BLOCKING ENGINE JUNCTION TABLE ---
        String createBlockedUsersTable = "CREATE TABLE IF NOT EXISTS blocked_users ("
                + "blocker_id INTEGER NOT NULL,"
                + "blocked_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (blocker_id) REFERENCES users(id),"
                + "FOREIGN KEY (blocked_id) REFERENCES users(id),"
                + "PRIMARY KEY (blocker_id, blocked_id)"
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
            
            // Execute the new table creation
            stmt.execute(createBlockedUsersTable);
            
            /* ========================================= */
            /* --- THE SCHEMA MIGRATION ENGINE ---       */
            /* ========================================= */
            try { stmt.execute("ALTER TABLE posts ADD COLUMN parent_post_id INTEGER DEFAULT NULL"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE posts ADD COLUMN is_edited BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE comments ADD COLUMN is_edited BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE posts ADD COLUMN parent_comment_id INTEGER DEFAULT NULL"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN image_url TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN is_edited BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN is_forwarded BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN deleted_by_receiver BOOLEAN DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE messages ADD COLUMN reply_to_id INTEGER DEFAULT NULL"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE comments ADD COLUMN parent_comment_id INTEGER DEFAULT NULL"); } catch (SQLException e) {} // [NEW]: Threaded Replies Migration
            
            System.out.println("All database tables have been successfully initialized!");
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
    }
}