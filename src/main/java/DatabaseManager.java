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
            /* [DriverManager: A basic service for managing a set of JDBC (Java Database Connectivity) drivers] */
            conn = DriverManager.getConnection(DATABASE_URL);
            System.out.println("Connection to SQLite has been established.");
        } catch (SQLException e) {
            /* [SQLException: An exception that provides information on a database access error or other errors] */
            System.out.println("Connection Error: " + e.getMessage());
        }
        return conn;
    }

    /* 
       This method creates the tables if they do not already exist. 
       You only need to run this once when the application starts! 
    */
    public static void initializeDatabase() {
        
        // 1. Creating the Users Table
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "email TEXT NOT NULL UNIQUE,"
                + "password TEXT NOT NULL,"
                + "bio TEXT,"
                + "profile_pic_url TEXT,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
         
        // 2. Creating the Posts Table
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
        String createCommentsTable = "CREATE TABLE IF NOT EXISTS comments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "post_id INTEGER NOT NULL,"
                + "user_id INTEGER NOT NULL,"
                + "content TEXT NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (post_id) REFERENCES posts(id),"
                + "FOREIGN KEY (user_id) REFERENCES users(id)"
                + ");";
        
        // 4. Creating the Likes Table
        String createLikesTable = "CREATE TABLE IF NOT EXISTS likes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "post_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (user_id) REFERENCES users(id),"
                + "FOREIGN KEY (post_id) REFERENCES posts(id),"
                + "UNIQUE(user_id, post_id)" 
                + ");";            
                      
        // 5. Creating the Followers Table
        String createFollowersTable = "CREATE TABLE IF NOT EXISTS followers ("
                + "follower_id INTEGER NOT NULL,"
                + "following_id INTEGER NOT NULL,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (follower_id) REFERENCES users(id),"
                + "FOREIGN KEY (following_id) REFERENCES users(id),"
                + "PRIMARY KEY (follower_id, following_id)"
                + ");";

        // 6. Creating the Notifications Table (NEWLY ADDED!)
        /* 
           [Schema: The formal structure or "blueprint" of a database that defines 
           how data is organized into tables and columns] 
        */
        String createNotificationsTable = "CREATE TABLE IF NOT EXISTS notifications ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "recipient_user TEXT NOT NULL,"
                + "actor_user TEXT NOT NULL,"
                + "type TEXT NOT NULL," // This stores 'LIKE', 'FOLLOW', or 'COMMENT'
                + "post_id INTEGER,"    // This is optional (only used for likes/comments)
                + "is_read BOOLEAN DEFAULT 0,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        /* [Try-with-resources: A structure that ensures that each resource is closed 
           at the end of the statement, preventing memory leaks] */
        try (Connection conn = connect();
             /* [Statement: An interface used to execute a static SQL statement and return the results] */
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createUsersTable);
            stmt.execute(createPostsTable);
            stmt.execute(createCommentsTable);
            stmt.execute(createLikesTable);
            stmt.execute(createFollowersTable);
            stmt.execute(createNotificationsTable); // RUN THE NOTIFICATIONS TABLE!
            
            System.out.println("All database tables have been successfully initialized!");
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
    }
}