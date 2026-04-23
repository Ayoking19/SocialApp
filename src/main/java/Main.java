package main.java;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Main {
    
    public static void main(String[] args) {
        // [Initialization Trigger: Code that executes automatically when the application starts]
SettingsSystem.removeGhostFollowers();
        
        System.out.println("=== Starting Social Media Backend ===");
        DatabaseManager.initializeDatabase();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            /* ========================================= */
            /* --- 1. THE REGISTRATION ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/register", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        String email = extractJsonValue(body, "email");
                        String password = extractJsonValue(body, "password");
                        
                        boolean success = LoginSystem.registerUser(username, email, password);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 2. THE LOGIN ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/login", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String identifier = extractJsonValue(body, "identifier");
                        String password = extractJsonValue(body, "password");
                        
                        boolean success = LoginSystem.authenticateUser(identifier, password);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 3. THE CREATE POST ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/createPost", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        String content = extractJsonValue(body, "content");
                        String media = extractJsonValue(body, "media");
                        
                        boolean success = PostSystem.createPost(username, content, media);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 4. THE FEED ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getFeed", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String jsonResponse = PostSystem.getFeed(currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 5. THE PROFILE ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/profile", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String targetUser = extractJsonValue(body, "username");
                        String jsonResponse = ProfileSystem.getUserProfile(targetUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 6. THE USER POSTS ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/userPosts", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String targetUser = extractJsonValue(body, "username");
                        String currentUser = extractJsonValue(body, "currentUser"); 
                        
                        String jsonResponse = PostSystem.getUserPosts(targetUser, currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 7. THE EDIT BIO ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/updateBio", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        String newBio = extractJsonValue(body, "bio");
                        
                        boolean success = ProfileSystem.updateBio(username, newBio);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 8. THE TOGGLE LIKE ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/toggleLike", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int postId = Integer.parseInt(extractJsonValue(body, "postId"));
                        
                        String response = PostSystem.toggleLike(username, postId);
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 9. THE ADD COMMENT ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/addComment", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int postId = Integer.parseInt(extractJsonValue(body, "postId"));
                        String content = extractJsonValue(body, "content");
                        
                        boolean success = PostSystem.addComment(username, postId, content);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 10. THE GET COMMENTS ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getComments", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        int postId = Integer.parseInt(extractJsonValue(body, "postId"));
                        
                        String jsonResponse = PostSystem.getComments(postId, currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 11. THE TOGGLE FOLLOW ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/toggleFollow", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String targetUser = extractJsonValue(body, "targetUser");
                        
                        String response = FollowSystem.toggleFollow(currentUser, targetUser);
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 12. THE PROFILE RELATIONS ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getRelations", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String targetUser = extractJsonValue(body, "targetUser");
                        
                        String jsonResponse = FollowSystem.getProfileRelations(currentUser, targetUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 13. THE UPLOAD AVATAR ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/uploadAvatar", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        String base64Image = extractJsonValue(body, "image");
                        
                        boolean success = ProfileSystem.updateProfilePicture(username, base64Image);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 14. THE UNIVERSAL SEARCH ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/search", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String searchQuery = extractJsonValue(body, "query");
                        String currentUser = extractJsonValue(body, "currentUser"); 
                        
                        if (searchQuery == null || searchQuery.trim().isEmpty()) {
                            String emptyResponse = "{\"users\":[],\"posts\":[]}";
                            exchange.sendResponseHeaders(200, emptyResponse.length());
                            OutputStream os = exchange.getResponseBody();
                            os.write(emptyResponse.getBytes());
                            os.close();
                            return;
                        }

                        String usersJson = ProfileSystem.searchUsers(searchQuery);
                        String postsJson = PostSystem.searchPosts(searchQuery, currentUser);
                        
                        String combinedJsonResponse = "{" +
                                "\"users\":" + usersJson + "," +
                                "\"posts\":" + postsJson +
                                "}";
                        
                        exchange.sendResponseHeaders(200, combinedJsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(combinedJsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 15. THE FOLLOWING FEED ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/followingFeed", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String jsonResponse = PostSystem.getFollowingFeed(currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 16. THE SINGLE POST ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getSinglePost", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String postId = extractJsonValue(body, "postId");
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = PostSystem.getSinglePost(postId, currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 17. THE PROFILE SEARCH ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/searchProfile", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    
                    if ("OPTIONS".equals(exchange.getRequestMethod())) {
                        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                        exchange.sendResponseHeaders(204, -1);
                        return;
                    }

                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String query = extractJsonValue(body, "query");
                        String targetUser = extractJsonValue(body, "targetUser");
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = PostSystem.searchProfilePosts(query, targetUser, currentUser);
                        
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });
            
            /* ========================================= */
            /* --- 18. THE USER REPLIES ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getUserActivity", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String targetUser = extractJsonValue(body, "targetUser");
                        String jsonResponse = PostSystem.getUserActivity(targetUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 19. THE FOLLOWER DISCOVERY ROUTES --- */
            /* ========================================= */
            server.createContext("/api/getFollowers", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    handleUserListRequest(exchange, true);
                }
            });

            server.createContext("/api/getFollowing", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    handleUserListRequest(exchange, false);
                }
            });

            /* ========================================= */
            /* --- 20. THE NOTIFICATION ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getNotifications", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        String jsonResponse = NotificationSystem.getNotifications(username);
                        
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });
            /* --- 21. THE CLEAR NOTIFICATIONS ENDPOINT --- */
            server.createContext("/api/markNotificationsRead", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        
                        // Execute the clearing logic
                        NotificationSystem.markNotificationsAsRead(username);
                        
                        String response = "SUCCESS";
                        exchange.sendResponseHeaders(200, response.length());
                        exchange.getResponseBody().write(response.getBytes());
                        exchange.getResponseBody().close();
                    }
                }
            });
            /* ========================================= */
            /* --- 22. THE PASSWORD UPDATE ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/updatePassword", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        String oldPass = extractJsonValue(body, "currentPassword");
                        String newPass = extractJsonValue(body, "newPassword");
                        
                        boolean success = SettingsSystem.updatePassword(username, oldPass, newPass);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        exchange.getResponseBody().write(response.getBytes());
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- 23. THE DELETE ACCOUNT ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/deleteAccount", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        
                        boolean success = SettingsSystem.deleteAccount(username);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        exchange.getResponseBody().write(response.getBytes());
                        exchange.getResponseBody().close();
                    }
                }
            });
            // Inside your Main.java, add this context alongside your other API routes
server.createContext("/", new HttpHandler() {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // [URI Path: The specific address requested after the domain name, such as /index.html]
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html"; // Default to index

        // This points to your 'static' folder in your project resources
        File file = new File("src/main/resources/static" + path);

        if (file.exists()) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            
            // Set the correct [MIME Type: A label used to tell the browser what kind of file it is receiving, such as 'text/html' or 'text/css']
            String contentType = "text/html";
            if (path.endsWith(".css")) contentType = "text/css";
            if (path.endsWith(".js")) contentType = "application/javascript";
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            // [404 Error: The standard HTTP response code used to indicate that the requested file could not be found on the server]
            String error = "404 Not Found";
            exchange.sendResponseHeaders(404, error.length());
            exchange.getResponseBody().write(error.getBytes());
            exchange.getResponseBody().close();
        }
    }
});
server.createContext("/", new HttpHandler() {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        // [File Pathing: The process of telling the computer exactly where to find a file on your hard drive]
        File file = new File("src/main/resources/static" + path);

        if (file.exists()) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String contentType = "text/html";
            if (path.endsWith(".css")) contentType = "text/css";
            if (path.endsWith(".js")) contentType = "application/javascript";
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }
});
// [Context Creation]: Telling the server to listen for any request starting with "/"
server.createContext("/", new HttpHandler() {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 1. Get the path from the URL (e.g., "/settings.html")
        String path = exchange.getRequestURI().getPath();
        
        // 2. Default to index.html if the path is just "/"
        if (path.equals("/")) {
            path = "/index.html";
        }

        // 3. Point to your static folder
        // [Relative Path: A file location based on the current working directory of the program]
        File file = new File("src/main/resources/static" + path);

        if (file.exists() && !file.isDirectory()) {
            // [Byte Array: A collection of raw data bits that represent the contents of a file]
            byte[] bytes = Files.readAllBytes(file.toPath());

            // 4. Set the correct MIME Type so the browser knows how to render the file
            String contentType = "text/html";
            if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg")) contentType = "image/jpeg";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            
            // [Output Stream: The 'pipe' used to send data from the server back to the client]
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            // [404 Not Found: The standard error code for a requested resource that does not exist]
            String response = "404 File Not Found";
            exchange.sendResponseHeaders(404, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
});

            server.setExecutor(null);
            server.start();
            System.out.println("\n🌐 API Server is running live on http://localhost:8080");
            
        } catch (IOException e) {
            System.out.println("Failed to start server: " + e.getMessage());
        }
    }

    /* ========================================= */
    /* --- THE HELPER METHODS --- */
    /* ========================================= */
    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey) + searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        
        if (startIndex >= searchKey.length() && endIndex > startIndex) {
            return json.substring(startIndex, endIndex);
        }
        return "";
    }

    private static void handleUserListRequest(HttpExchange exchange, boolean isFollowers) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if ("POST".equals(exchange.getRequestMethod())) {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            String targetUser = extractJsonValue(body, "targetUser");
            String currentUser = extractJsonValue(body, "currentUser");

            String jsonResponse = isFollowers ? 
                FollowSystem.getFollowerList(targetUser, currentUser) : 
                FollowSystem.getFollowingList(targetUser, currentUser);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}