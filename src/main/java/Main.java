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
        SettingsSystem.removeGhostFollowers();
        SettingsSystem.removeGhostActivity();
        
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
                        
                        // Extract the page number from the frontend (defaulting to Page 1 if missing)
                        String pageStr = extractJsonValue(body, "page");
                        int page = (pageStr == null || pageStr.isEmpty()) ? 1 : Integer.parseInt(pageStr);
                        
                        // Pass both arguments to the newly updated engine
                        String jsonResponse = PostSystem.getFeed(currentUser, page);
                        
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
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
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
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
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
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
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
                        
                        exchange.sendResponseHeaders(200, jsonResponse.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(jsonResponse.getBytes());
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
                        String media = extractJsonValue(body, "media"); // Extracting the image!
                        
                        String response = MessageSystem.sendMessage(sender, receiver, content, media);
                        
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
                        
                        String jsonResponse = MessageSystem.getChatHistory(currentUser, targetUser);
                        
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