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
import java.util.Base64;
import java.util.UUID;

public class Main {
    
    public static void main(String[] args) {
        SettingsSystem.removeGhostFollowers();
        SettingsSystem.removeGhostActivity();
        
        System.out.println("=== Starting Social Media Backend ===");
        DatabaseManager.initializeDatabase();
        MessageSystem.ensureSchema();

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
                        String media = saveMediaFile(extractJsonValue(body, "media")); 
                        
                        Integer parentPostId = null;
                        if (body.contains("\"parentPostId\":")) {
                            int startIndex = body.indexOf("\"parentPostId\":") + 15;
                            int endIndex = body.indexOf(",", startIndex);
                            if (endIndex == -1) endIndex = body.indexOf("}", startIndex);
                            
                            String rawId = body.substring(startIndex, endIndex).trim().replace("\"", "");
                            if (!rawId.equals("null") && !rawId.isEmpty()) {
                                try { 
                                    parentPostId = Integer.parseInt(rawId); 
                                } catch (NumberFormatException e) {}
                            }
                        }

                        Integer parentCommentId = null;
                        if (body.contains("\"parentCommentId\":")) {
                            int startIndex = body.indexOf("\"parentCommentId\":") + 18;
                            int endIndex = body.indexOf(",", startIndex);
                            if (endIndex == -1) endIndex = body.indexOf("}", startIndex);
                            
                            String rawId = body.substring(startIndex, endIndex).trim().replace("\"", "");
                            if (!rawId.equals("null") && !rawId.isEmpty()) {
                                try { 
                                    parentCommentId = Integer.parseInt(rawId); 
                                } catch (NumberFormatException e) {}
                            }
                        }
                        
                        boolean success = PostSystem.createPost(username, content, media, parentPostId, parentCommentId);
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
                        String pageStr = extractJsonValue(body, "page");
                        int page = (pageStr == null || pageStr.isEmpty()) ? 1 : Integer.parseInt(pageStr);
                        
                        String jsonResponse = PostSystem.getFeed(currentUser, page);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        String base64Image = saveMediaFile(extractJsonValue(body, "image")); 
                        
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
                            byte[] emptyBytes = emptyResponse.getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                            exchange.sendResponseHeaders(200, emptyBytes.length);
                            OutputStream os = exchange.getResponseBody();
                            os.write(emptyBytes);
                            os.close();
                            return;
                        }

                        String usersJson = ProfileSystem.searchUsers(searchQuery);
                        String postsJson = PostSystem.searchPosts(searchQuery, currentUser);
                        
                        String combinedJsonResponse = "{" +
                                "\"users\":" + usersJson + "," +
                                "\"posts\":" + postsJson +
                                "}";
                        
                        byte[] responseBytes = combinedJsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        String pageStr = extractJsonValue(body, "page");
                        int page = (pageStr == null || pageStr.isEmpty()) ? 1 : Integer.parseInt(pageStr);
                        
                        String jsonResponse = PostSystem.getFollowingFeed(currentUser, page);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
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
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 21. THE CLEAR NOTIFICATIONS ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/markNotificationsRead", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        
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

            /* ========================================= */
            /* --- 24. THE QUOTES ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getPostQuotes", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String postId = extractJsonValue(body, "postId");
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = PostSystem.getPostQuotes(postId, currentUser);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 25. THE USER QUOTES ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getUserQuotes", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String targetUser = extractJsonValue(body, "targetUser");
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = PostSystem.getUserQuotes(targetUser, currentUser);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 26. THE USER REPOSTS ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getUserReposts", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String targetUser = extractJsonValue(body, "targetUser");
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = PostSystem.getUserReposts(targetUser, currentUser);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 27. THE EDIT POST ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/editPost", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int postId = Integer.parseInt(extractJsonValue(body, "postId"));
                        String content = extractJsonValue(body, "content");
                        
                        boolean success = PostSystem.editPost(username, postId, content);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 28. THE DELETE POST ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/deletePost", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int postId = Integer.parseInt(extractJsonValue(body, "postId"));
                        
                        boolean success = PostSystem.deletePost(username, postId);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 29. THE EDIT COMMENT ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/editComment", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int commentId = Integer.parseInt(extractJsonValue(body, "commentId"));
                        String content = extractJsonValue(body, "content");
                        
                        boolean success = PostSystem.editComment(username, commentId, content);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 30. THE DELETE COMMENT ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/deleteComment", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int commentId = Integer.parseInt(extractJsonValue(body, "commentId"));
                        
                        boolean success = PostSystem.deleteComment(username, commentId);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 31. THE TOGGLE COMMENT LIKE ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/toggleCommentLike", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String username = extractJsonValue(body, "username");
                        int commentId = Integer.parseInt(extractJsonValue(body, "commentId"));
                        
                        String response = PostSystem.toggleCommentLike(username, commentId);
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 32. THE COMMENT QUOTES ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/getCommentQuotes", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String commentId = extractJsonValue(body, "commentId");
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = PostSystem.getCommentQuotes(commentId, currentUser);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 33. THE SEND MESSAGE ENDPOINT ---     */
            /* ========================================= */
            server.createContext("/api/sendMessage", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String sender = extractJsonValue(body, "sender");
                        String receiver = extractJsonValue(body, "receiver");
                        String content = extractJsonValue(body, "content");
                        String media = saveMediaFile(extractJsonValue(body, "media")); 
                        
                        boolean isForwarded = body.contains("\"isForwarded\":true") || body.contains("\"isForwarded\": true");
                        String replyIdStr = extractJsonValue(body, "replyToId");
                        Integer replyToId = (replyIdStr != null && !replyIdStr.trim().isEmpty()) ? Integer.parseInt(replyIdStr) : null;
                        
                        String response = MessageSystem.sendMessage(sender, receiver, content, media, isForwarded, replyToId);
                        
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 34. THE CHAT HISTORY ENDPOINT ---     */
            /* ========================================= */
            server.createContext("/api/getChatHistory", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String targetUser = extractJsonValue(body, "targetUser");
                        
                        // THE FIX: Smart Delta Polling Parameter
                        String lastMsgIdStr = extractJsonValue(body, "lastMessageId");
                        int lastMessageId = (lastMsgIdStr != null && !lastMsgIdStr.trim().isEmpty() && !lastMsgIdStr.equals("null")) ? Integer.parseInt(lastMsgIdStr) : 0;
                        
                        String jsonResponse = MessageSystem.getChatHistory(currentUser, targetUser, lastMessageId);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 34.5. THE CREATE GROUP ENDPOINT ---   */
            /* ========================================= */
            server.createContext("/api/createGroup", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String creator = extractJsonValue(body, "currentUser");
                        String groupName = extractJsonValue(body, "groupName");
                        String membersStr = extractJsonValue(body, "members"); 
                        String response = MessageSystem.createGroup(creator, groupName, membersStr);
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 34.6. THE USER GROUPS ENDPOINTS ---   */
            /* ========================================= */
            server.createContext("/api/getUserGroups", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String response = MessageSystem.getUserGroups(extractJsonValue(body, "currentUser"));
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            server.createContext("/api/sendGroupMessage", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String sender = extractJsonValue(body, "sender");
                        int groupId = Integer.parseInt(extractJsonValue(body, "groupId"));
                        String content = extractJsonValue(body, "content");
                        String media = saveMediaFile(extractJsonValue(body, "media")); 
                        String replyIdStr = extractJsonValue(body, "replyToId");
                        Integer replyToId = (replyIdStr != null && !replyIdStr.trim().isEmpty()) ? Integer.parseInt(replyIdStr) : null;
                        
                        String response = MessageSystem.sendGroupMessage(sender, groupId, content, media, replyToId);
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            server.createContext("/api/getGroupChatHistory", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String currentUser = extractJsonValue(body, "currentUser");
                        int groupId = Integer.parseInt(extractJsonValue(body, "groupId"));
                        
                        // THE FIX: Smart Delta Polling Parameter
                        String lastMsgIdStr = extractJsonValue(body, "lastMessageId");
                        int lastMessageId = (lastMsgIdStr != null && !lastMsgIdStr.trim().isEmpty() && !lastMsgIdStr.equals("null")) ? Integer.parseInt(lastMsgIdStr) : 0;
                        
                        String response = MessageSystem.getGroupChatHistory(currentUser, groupId, lastMessageId);
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- 34.7. THE CHAT MEDIA ENDPOINTS ---    */
            /* ========================================= */
            server.createContext("/api/getChatMedia", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String currentUser = extractJsonValue(body, "currentUser");
                        String targetUser = extractJsonValue(body, "targetUser");
                        String groupIdStr = extractJsonValue(body, "groupId");
                        Integer groupId = (groupIdStr != null && !groupIdStr.trim().isEmpty() && !groupIdStr.equals("null")) ? Integer.parseInt(groupIdStr) : null;
                        
                        String response = MessageSystem.getChatMedia(currentUser, targetUser, groupId);
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- 34.8. THE GROUP ADMIN ENDPOINTS ---   */
            /* ========================================= */
            server.createContext("/api/getGroupInfo", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String response = MessageSystem.getGroupInfo(extractJsonValue(body, "currentUser"), Integer.parseInt(extractJsonValue(body, "groupId")));
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            server.createContext("/api/updateGroupInfo", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String response = MessageSystem.updateGroupInfo(extractJsonValue(body, "currentUser"), Integer.parseInt(extractJsonValue(body, "groupId")), extractJsonValue(body, "newName"), saveMediaFile(extractJsonValue(body, "newAvatar"))); 
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            server.createContext("/api/manageGroupMember", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String response = MessageSystem.manageGroupMember(extractJsonValue(body, "currentUser"), Integer.parseInt(extractJsonValue(body, "groupId")), extractJsonValue(body, "action"), extractJsonValue(body, "targetUser"));
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            server.createContext("/api/leaveGroup", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        boolean deleteGroup = body.contains("\"deleteGroup\":true") || body.contains("\"deleteGroup\": true");
                        String response = MessageSystem.leaveGroup(extractJsonValue(body, "currentUser"), Integer.parseInt(extractJsonValue(body, "groupId")), deleteGroup);
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- 35. THE INBOX (ACTIVE CHATS) ENDPOINT */
            /* ========================================= */
            server.createContext("/api/getInbox", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = MessageSystem.getInbox(currentUser);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 36. THE UNREAD MESSAGE COUNT ENDPOINT */
            /* ========================================= */
            server.createContext("/api/getUnreadCount", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        
                        String jsonResponse = MessageSystem.getGlobalUnreadCount(currentUser);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 37. THE STATIC FILE ROUTER (CLEANED) --- */
            /* ========================================= */
            server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String path = exchange.getRequestURI().getPath();
                    
                    if (path.equals("/")) {
                        path = "/index.html";
                    }

                    File file = new File("src/main/resources/static" + path);

                    if (file.exists() && !file.isDirectory()) {
                        byte[] bytes = Files.readAllBytes(file.toPath());

                        String contentType = "text/html";
                        if (path.endsWith(".css")) contentType = "text/css";
                        else if (path.endsWith(".js")) contentType = "application/javascript";
                        else if (path.endsWith(".png")) contentType = "image/png";
                        else if (path.endsWith(".jpg")) contentType = "image/jpeg";
                        else if (path.endsWith(".mp4")) contentType = "video/mp4"; 
                        else if (path.endsWith(".pdf")) contentType = "application/pdf"; 
                        else if (path.endsWith(".txt")) contentType = "text/plain"; 

                        exchange.getResponseHeaders().set("Content-Type", contentType);
                        exchange.sendResponseHeaders(200, bytes.length);
                        
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                    } else {
                        String response = "404 File Not Found";
                        exchange.sendResponseHeaders(404, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 38. THE BLOCK SYSTEM ENDPOINTS ---    */
            /* ========================================= */
            server.createContext("/api/toggleBlock", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String response = BlockSystem.toggleBlock(extractJsonValue(body, "currentUser"), extractJsonValue(body, "targetUser"));
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            server.createContext("/api/getBlockStatus", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String response = BlockSystem.getBlockStatus(extractJsonValue(body, "currentUser"), extractJsonValue(body, "targetUser"));
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- 39. EDIT MESSAGE ENDPOINT ---         */
            /* ========================================= */
            server.createContext("/api/editMessage", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        String username = extractJsonValue(body, "username");
                        int messageId = Integer.parseInt(extractJsonValue(body, "messageId"));
                        String newContent = extractJsonValue(body, "content");
                        
                        boolean success = MessageSystem.editMessage(username, messageId, newContent);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        byte[] responseBytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- 40. DELETE MESSAGE ENDPOINT ---       */
            /* ========================================= */
            server.createContext("/api/deleteMessage", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        String username = extractJsonValue(body, "username");
                        int messageId = Integer.parseInt(extractJsonValue(body, "messageId"));
                        
                        boolean success = MessageSystem.deleteMessage(username, messageId);
                        String response = success ? "SUCCESS" : "FAILURE";
                        
                        byte[] responseBytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });

            /* ========================================= */
            /* --- [NEW] DELETE ENTIRE CHAT ENDPOINT --- */
            /* ========================================= */
            server.createContext("/api/clearChatHistory", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String targetUser = extractJsonValue(body, "targetUser");

                        String response = MessageSystem.clearChatHistory(currentUser, targetUser);
                        
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });
            
            /* ========================================= */
            /* --- 41. THE TOP POSTS TIME-SERIES ENDPOINT*/
            /* ========================================= */
            server.createContext("/api/getTopPosts", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUser = extractJsonValue(body, "currentUser");
                        String timeFilter = extractJsonValue(body, "timeFilter");
                        String customDate = extractJsonValue(body, "customDate");
                        
                        String jsonResponse = PostSystem.getTopPosts(currentUser, timeFilter, customDate);
                        
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();
                    }
                }
            });

            /* ========================================= */
            /* --- 42. THE UPDATE USERNAME ENDPOINT ---  */
            /* ========================================= */
            server.createContext("/api/updateUsername", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    if ("POST".equals(exchange.getRequestMethod())) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        
                        String currentUsername = extractJsonValue(body, "currentUsername");
                        String newUsername = extractJsonValue(body, "newUsername");
                        
                        String response = SettingsSystem.updateUsername(currentUsername, newUsername);
                        
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        exchange.getResponseBody().write(responseBytes);
                        exchange.getResponseBody().close();
                    }
                }
            });
                     
            server.setExecutor(null);
            server.start();
            System.out.println("\n API Server is running live on http://localhost:8080");
            
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

            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }

    /* ============================================================== */
    /* --- THE DATABASE OPTIMIZATION ENGINE (FILE SAVER) ---    */
    /* ============================================================== */
    private static String saveMediaFile(String base64Data) {
        if (base64Data == null || base64Data.trim().isEmpty()) return "";
        if (!base64Data.startsWith("data:")) return base64Data; 
        
        try {
            int commaIndex = base64Data.indexOf(",");
            if (commaIndex == -1) return base64Data;
            
            String header = base64Data.substring(0, commaIndex);
            String data = base64Data.substring(commaIndex + 1);
            
            String ext = ".png"; 
            if (header.contains("video/mp4")) ext = ".mp4";
            else if (header.contains("video/webm")) ext = ".webm";
            else if (header.contains("image/jpeg")) ext = ".jpg";
            else if (header.contains("image/gif")) ext = ".gif";
            else if (header.contains("application/pdf")) ext = ".pdf";
            else if (header.contains("text/plain")) ext = ".txt";
            
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            
            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            
            File dir = new File("src/main/resources/static/uploads");
            if (!dir.exists()) dir.mkdirs();
            
            File file = new File(dir, fileName);
            Files.write(file.toPath(), decodedBytes);
            
            return "uploads/" + fileName;
            
        } catch (Exception e) {
            System.out.println("Media Save Error: " + e.getMessage());
            return base64Data; 
        }
    }
}