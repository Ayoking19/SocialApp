package main.java;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    
    /* The exact location and name of our new database file */
    private static final String DATABASE_URL = "jdbc:sqlite:social_network.db";

    /* 
       [Method: A block of code that performs a specific task. 
       This method opens the door to the database.] 
    */
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

    /* 
       This method creates the tables if they do not already exist. 
       You only need to run this once when the application starts! 
    */
    public static void initializeDatabase() {
        /* 
           [SQL String: A text variable containing a command written in 
           Structured Query Language that the database can understand.] 
        */
        
        // 1. Creating the Users Table (You already have this)
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "email TEXT NOT NULL UNIQUE,"
                + "password TEXT NOT NULL,"
                + "bio TEXT,"
                + "profile_pic_url TEXT,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        // 2. Creating the Posts Table (ADD THIS!)
        /* 
           [Foreign Key: A rule that links the 'user_id' in this table directly 
           to the 'id' in the users table, ensuring no ghost posts exist] 
        */
        String createPostsTable = "CREATE TABLE IF NOT EXISTS posts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "content TEXT NOT NULL,"
                + "image_url TEXT,"
                + "video_url TEXT,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (user_id) REFERENCES users(id)"
                + ");";

        // 3. Creating the Comments Table
        /* 
           [Dual Foreign Keys: This table acts as a bridge. It links to the ID of the 
           person typing the comment, AND the ID of the post they are looking at.] 
        */
        String createCommentsTable = "CREATE TABLE IF NOT EXISTS comments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "post_id INTEGER NOT NULL,"
                + "user_id INTEGER NOT NULL,"
                + "content TEXT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (post_id) REFERENCES posts(id),"
                + "FOREIGN KEY (user_id) REFERENCES users(id)"
                + ");";
        
        // 4. Creating the Likes Table (NEW!)
        /* 
           [UNIQUE Constraint: A strict database rule that makes it physically 
           impossible for the exact same user_id and post_id combination to be saved twice. 
           This prevents double-liking at the deepest level of the application!] 
        */
        String createLikesTable = "CREATE TABLE IF NOT EXISTS likes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "post_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (user_id) REFERENCES users(id),"
                + "FOREIGN KEY (post_id) REFERENCES posts(id),"
                + "UNIQUE(user_id, post_id)" 
                + ");";

        // 5. Creating the Followers Table (NEW!)
        /* 
           [Bidirectional Tracking: This table records two IDs. The person clicking the button (follower_id), 
           and the person they are choosing to follow (following_id).] 
        */
        String createFollowersTable = "CREATE TABLE IF NOT EXISTS followers ("
                + "follower_id INTEGER NOT NULL,"
                + "following_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (follower_id) REFERENCES users(id),"
                + "FOREIGN KEY (following_id) REFERENCES users(id),"
                + "PRIMARY KEY (follower_id, following_id)" // Prevents following the same person twice!
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createUsersTable);
            stmt.execute(createPostsTable);
            stmt.execute(createCommentsTable);
            stmt.execute(createLikesTable);
            stmt.execute(createFollowersTable); // RUN THE NEW TABLE!
            
            System.out.println("Database tables have been successfully initialized!");
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
    }
}