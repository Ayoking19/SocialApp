package main.java;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {
    
    public static void main(String[] args) {
        
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
                        
                        // Extracting the text, the author, and the new Base64 Image string
                        String username = extractJsonValue(body, "username");
                        String content = extractJsonValue(body, "content");
                        String media = extractJsonValue(body, "media");
                        
                        // Handing the data to our upgraded PostSystem engine
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
            /* --- THE FEED ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getFeed", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    // We upgraded this to a POST request so it can securely receive the username JSON!
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
            /* --- 5. THE PROFILE ENDPOINT (NEW) --- */
            /* ========================================= */
            server.createContext("/api/profile", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        /* We extract the username the HTML is asking for */
                        String targetUser = extractJsonValue(body, "username");
                        
                        /* We hand the username to our new ProfileSystem */
                        String jsonResponse = ProfileSystem.getUserProfile(targetUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });
            /* ========================================= */
            /* --- 7. THE EDIT BIO ENDPOINT (NEW) --- */
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
            /* --- 6. THE USER POSTS ENDPOINT (UPDATED) --- */
            /* ========================================= */
            server.createContext("/api/userPosts", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        /* Extract BOTH the profile owner AND the logged-in user! */
                        String targetUser = extractJsonValue(body, "username");
                        String currentUser = extractJsonValue(body, "currentUser"); 
                        
                        /* Trigger our new targeted engine with BOTH pieces of data */
                        String jsonResponse = PostSystem.getUserPosts(targetUser, currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
                        os.close();
                    }
                }
            });
            /* ========================================= */
            /* --- 8. THE TOGGLE LIKE ENDPOINT (NEW) --- */
            /* ========================================= */
            server.createContext("/api/toggleLike", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        /* [Type Conversion: The JSON gives us a string, but our Java method requires a mathematical Integer for the ID!] */
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
            /* --- THE GET COMMENTS ENDPOINT --- */
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
            /* --- 14. THE UNIVERSAL SEARCH ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/search", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        /* We grab the typed text AND the current user (so the Follow buttons on posts work correctly!) */
                        String searchQuery = extractJsonValue(body, "query");
                        String currentUser = extractJsonValue(body, "currentUser"); 
                        
                        // Security check: If empty, send back a master object with empty arrays
                        if (searchQuery == null || searchQuery.trim().isEmpty()) {
                            String emptyResponse = "{\"users\":[],\"posts\":[]}";
                            exchange.sendResponseHeaders(200, emptyResponse.length());
                            OutputStream os = exchange.getResponseBody();
                            os.write(emptyResponse.getBytes());
                            os.close();
                            return;
                        }

                        /* [The Multi-Index Search] */
                        // 1. We ask the ProfileSystem for the users array
                        String usersJson = ProfileSystem.searchUsers(searchQuery);
                        
                        // 2. We ask the PostSystem for the posts array
                        String postsJson = PostSystem.searchPosts(searchQuery, currentUser);
                        
                        /* 3. THE MASTER MERGE: We combine both lists into a single, beautiful JSON dictionary! */
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
                        
                        /* Extract the logged-in user making the request */
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        /* Trigger our new Relational Join engine! */
                        String jsonResponse = PostSystem.getFollowingFeed(currentUser);
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
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
    /* --- THE HELPER METHOD (JSON PARSER) --- */
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
}