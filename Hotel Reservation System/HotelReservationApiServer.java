import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class HotelReservationApiServer {
    // Deployment-ready configuration using environment variables with local fallbacks
    private static final String url = System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/hotel_db";
    private static final String username = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
    private static final String password = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "shree";

    // Simple in-memory session store: sessionToken -> managerId
    private static final Map<String, Integer> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL Driver not found: " + e.getMessage());
        }

        // Ensure all tables exist on startup
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            
            // 1. Managers Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS managers (" +
                "  manager_id INT AUTO_INCREMENT PRIMARY KEY," +
                "  hotel_name VARCHAR(255) NOT NULL," +
                "  manager_name VARCHAR(255) NOT NULL," +
                "  email VARCHAR(255) NOT NULL UNIQUE," +
                "  password VARCHAR(255) NOT NULL," +
                "  phone VARCHAR(20)," +
                "  address TEXT," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 2. Rooms Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS rooms (" +
                "  room_number INT PRIMARY KEY," +
                "  room_type VARCHAR(50)," +
                "  price DOUBLE," +
                "  is_available BOOLEAN DEFAULT TRUE" +
                ")"
            );

            // 3. Reservations Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS reservations (" +
                "  reservation_id INT AUTO_INCREMENT PRIMARY KEY," +
                "  guest_name VARCHAR(255)," +
                "  room_number INT," +
                "  contact_number VARCHAR(20)," +
                "  nights INT," +
                "  total_price DOUBLE," +
                "  reservation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  manager_id INT," +
                "  check_in_date DATE" +
                ")"
            );

            System.out.println("Database tables verified and ready.");
        } catch (SQLException e) {
            System.out.println("Warning: Could not verify tables: " + e.getMessage());
        }

        // Use environment PORT for cloud deployment (e.g., Railway/Render)
        int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("API and Web Server running on port: " + port);
        
        // Auth endpoints
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/logout", new LogoutHandler());
        server.createContext("/api/auth/check", new AuthCheckHandler());

        // Existing API endpoints
        server.createContext("/api/dashboard", new DashboardHandler());
        server.createContext("/api/rooms", new RoomsHandler(true));
        server.createContext("/api/all-rooms", new RoomsHandler(false));
        server.createContext("/api/reservations", new ReservationsHandler());
        server.createContext("/api/guests", new GuestsHandler());
        server.createContext("/api/reports", new ReportsHandler());

        // Settings endpoints
        server.createContext("/api/settings/rooms", new SettingsRoomsHandler());
        server.createContext("/api/settings/rooms/price", new UpdateRoomPriceHandler());
        server.createContext("/api/settings/rooms/toggle", new ToggleRoomHandler());
        server.createContext("/api/settings/rooms/delete", new DeleteRoomHandler());

        server.createContext("/", new StaticFileHandler());
        
        server.setExecutor(null);
        server.start();
        System.out.println("API and Web Server running at http://localhost:8080");
    }

    static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // Validate session from Authorization header
    static int getAuthenticatedManagerId(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Integer managerId = sessions.get(token);
            return managerId != null ? managerId : -1;
        }
        return -1;
    }

    static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int idx = json.indexOf(searchKey);
        if (idx == -1) return "";
        int start = idx + searchKey.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }

    // ======================== AUTH HANDLERS ========================

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String hotelName = extractJsonValue(body, "hotelName");
            String managerName = extractJsonValue(body, "managerName");
            String email = extractJsonValue(body, "email");
            String pass = extractJsonValue(body, "password");
            String phone = extractJsonValue(body, "phone");
            String address = extractJsonValue(body, "address");

            if (hotelName.isEmpty() || managerName.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Hotel name, manager name, email, and password are required.\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                // Check if email already exists
                try (PreparedStatement check = conn.prepareStatement("SELECT manager_id FROM managers WHERE email = ?")) {
                    check.setString(1, email);
                    ResultSet rs = check.executeQuery();
                    if (rs.next()) {
                        sendJsonResponse(exchange, 409, "{\"error\":\"An account with this email already exists.\"}");
                        return;
                    }
                }

                // Insert new manager
                try (PreparedStatement pst = conn.prepareStatement(
                        "INSERT INTO managers (hotel_name, manager_name, email, password, phone, address) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    pst.setString(1, hotelName);
                    pst.setString(2, managerName);
                    pst.setString(3, email);
                    pst.setString(4, pass);
                    pst.setString(5, phone);
                    pst.setString(6, address);
                    pst.executeUpdate();

                    ResultSet keys = pst.getGeneratedKeys();
                    keys.next();
                    int managerId = keys.getInt(1);

                    // Create session token
                    String token = UUID.randomUUID().toString();
                    sessions.put(token, managerId);

                    String json = String.format(
                        "{\"success\":true,\"token\":\"%s\",\"manager\":{\"id\":%d,\"hotelName\":\"%s\",\"managerName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}}",
                        token, managerId, hotelName, managerName, email, phone
                    );
                    sendJsonResponse(exchange, 201, json);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"Registration failed: " + e.getMessage() + "\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String email = extractJsonValue(body, "email");
            String pass = extractJsonValue(body, "password");

            if (email.isEmpty() || pass.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Email and password are required.\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement pst = conn.prepareStatement("SELECT * FROM managers WHERE email = ? AND password = ?")) {
                pst.setString(1, email);
                pst.setString(2, pass);
                ResultSet rs = pst.executeQuery();

                if (rs.next()) {
                    int managerId = rs.getInt("manager_id");
                    String hotelName = rs.getString("hotel_name");
                    String managerName = rs.getString("manager_name");
                    String phone = rs.getString("phone");

                    String token = UUID.randomUUID().toString();
                    sessions.put(token, managerId);

                    String json = String.format(
                        "{\"success\":true,\"token\":\"%s\",\"manager\":{\"id\":%d,\"hotelName\":\"%s\",\"managerName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}}",
                        token, managerId, hotelName, managerName, email, phone != null ? phone : ""
                    );
                    sendJsonResponse(exchange, 200, json);
                } else {
                    sendJsonResponse(exchange, 401, "{\"error\":\"Invalid email or password.\"}");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"Login failed: " + e.getMessage() + "\"}");
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                sessions.remove(authHeader.substring(7));
            }
            sendJsonResponse(exchange, 200, "{\"success\":true}");
        }
    }

    static class AuthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            if (managerId == -1) {
                sendJsonResponse(exchange, 401, "{\"authenticated\":false}");
                return;
            }

            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement pst = conn.prepareStatement("SELECT * FROM managers WHERE manager_id = ?")) {
                pst.setInt(1, managerId);
                ResultSet rs = pst.executeQuery();
                if (rs.next()) {
                    String json = String.format(
                        "{\"authenticated\":true,\"manager\":{\"id\":%d,\"hotelName\":\"%s\",\"managerName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}}",
                        rs.getInt("manager_id"), rs.getString("hotel_name"),
                        rs.getString("manager_name"), rs.getString("email"),
                        rs.getString("phone") != null ? rs.getString("phone") : ""
                    );
                    sendJsonResponse(exchange, 200, json);
                } else {
                    sendJsonResponse(exchange, 401, "{\"authenticated\":false}");
                }
            } catch (SQLException e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Auth check failed\"}");
            }
        }
    }

    // ======================== EXISTING HANDLERS ========================

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            String filePath = "../web" + path;
            try {
                byte[] response = Files.readAllBytes(Paths.get(filePath));
                if (path.endsWith(".html")) exchange.getResponseHeaders().set("Content-Type", "text/html");
                else if (path.endsWith(".css")) exchange.getResponseHeaders().set("Content-Type", "text/css");
                else if (path.endsWith(".js")) exchange.getResponseHeaders().set("Content-Type", "application/javascript");
                
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                String response = "404 File Not Found: " + filePath;
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }

    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                int totalRes = 0; double revenue = 0;
                String resFilter = managerId > 0 ? "SELECT COUNT(*) FROM reservations WHERE manager_id = ?" : "SELECT COUNT(*) FROM reservations WHERE manager_id IS NULL AND 1=0";
                try (PreparedStatement ps = conn.prepareStatement(resFilter)) {
                    if (managerId > 0) ps.setInt(1, managerId);
                    ResultSet rs = ps.executeQuery(); rs.next(); totalRes = rs.getInt(1);
                }
                String revFilter = managerId > 0 ? "SELECT IFNULL(SUM(total_price),0) FROM reservations WHERE manager_id = ?" : "SELECT 0";
                try (PreparedStatement ps = conn.prepareStatement(revFilter)) {
                    if (managerId > 0) ps.setInt(1, managerId);
                    ResultSet rs = ps.executeQuery(); rs.next(); revenue = rs.getDouble(1);
                }
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM rooms WHERE is_available = TRUE");
                    rs2.next(); int availableRooms = rs2.getInt(1); rs2.close();
                    ResultSet rs3 = stmt.executeQuery("SELECT COUNT(*) FROM rooms WHERE is_available = FALSE");
                    rs3.next(); int occupiedRooms = rs3.getInt(1); rs3.close();
                    String json = String.format("{\"totalReservations\":%d,\"availableRooms\":%d,\"occupiedRooms\":%d,\"revenue\":%.2f}", 
                                               totalRes, availableRooms, occupiedRooms, revenue);
                    sendJsonResponse(exchange, 200, json);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    static class RoomsHandler implements HttpHandler {
        boolean availableOnly;
        RoomsHandler(boolean availableOnly) { this.availableOnly = availableOnly; }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            String query = availableOnly ? "SELECT * FROM rooms WHERE is_available = TRUE" : "SELECT * FROM rooms";
            try (Connection conn = DriverManager.getConnection(url, username, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"roomNumber\":").append(rs.getInt("room_number")).append(",");
                    json.append("\"roomType\":\"").append(rs.getString("room_type")).append("\",");
                    json.append("\"price\":").append(rs.getDouble("price")).append(",");
                    json.append("\"available\":").append(rs.getBoolean("is_available"));
                    json.append("}");
                    first = false;
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    static class ReservationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if ("GET".equals(exchange.getRequestMethod())) {
                handleGet(exchange);
            } else if ("POST".equals(exchange.getRequestMethod())) {
                handlePost(exchange);
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            int managerId = getAuthenticatedManagerId(exchange);
            String query = managerId > 0 
                ? "SELECT * FROM reservations WHERE manager_id = ? ORDER BY reservation_id DESC"
                : "SELECT * FROM reservations WHERE 1=0";
            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement pst = conn.prepareStatement(query)) {
                if (managerId > 0) pst.setInt(1, managerId);
                ResultSet rs = pst.executeQuery();
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("reservation_id")).append(",");
                    json.append("\"guestName\":\"").append(rs.getString("guest_name")).append("\",");
                    json.append("\"roomNumber\":").append(rs.getInt("room_number")).append(",");
                    json.append("\"contactNumber\":\"").append(rs.getString("contact_number")).append("\",");
                    json.append("\"totalPrice\":").append(rs.getDouble("total_price")).append(",");
                    json.append("\"checkInDate\":\"").append(rs.getDate("check_in_date") != null ? rs.getDate("check_in_date").toString() : "").append("\",");
                    json.append("\"date\":\"").append(rs.getTimestamp("reservation_date").toString()).append("\"");
                    json.append("}");
                    first = false;
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) { e.printStackTrace(); }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            String guestName = extractJsonValue(body, "guestName");
            int roomNumber = Integer.parseInt(extractJsonValue(body, "roomNumber"));
            String contact = extractJsonValue(body, "contactNumber");
            int nights = Integer.parseInt(extractJsonValue(body, "nights"));
            String checkInDate = extractJsonValue(body, "checkInDate");

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                conn.setAutoCommit(false);
                try {
                    // Get room price
                    double price = 0;
                    try (PreparedStatement pst = conn.prepareStatement("SELECT price FROM rooms WHERE room_number = ?")) {
                        pst.setInt(1, roomNumber);
                        ResultSet rs = pst.executeQuery();
                        if (rs.next()) price = rs.getDouble(1);
                    }
                    
                    double totalPrice = price * nights;

                    // Insert reservation with manager_id and check_in_date
                    int managerId = getAuthenticatedManagerId(exchange);
                    try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO reservations (guest_name, room_number, contact_number, nights, total_price, manager_id, check_in_date) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        pstmt.setString(1, guestName);
                        pstmt.setInt(2, roomNumber);
                        pstmt.setString(3, contact);
                        pstmt.setInt(4, nights);
                        pstmt.setDouble(5, totalPrice);
                        if (managerId > 0) pstmt.setInt(6, managerId); else pstmt.setNull(6, java.sql.Types.INTEGER);
                        if (checkInDate != null && !checkInDate.isEmpty()) {
                            pstmt.setDate(7, java.sql.Date.valueOf(checkInDate));
                        } else {
                            pstmt.setNull(7, java.sql.Types.DATE);
                        }
                        pstmt.executeUpdate();
                    }

                    // Update room status
                    try (PreparedStatement pst2 = conn.prepareStatement("UPDATE rooms SET is_available = FALSE WHERE room_number = ?")) {
                        pst2.setInt(1, roomNumber);
                        pst2.executeUpdate();
                    }

                    conn.commit();

                    String response = String.format("{\"success\":true, \"bill\": {\"guest\":\"%s\", \"room\":%d, \"nights\":%d, \"total\":%.2f}}", 
                                                    guestName, roomNumber, nights, totalPrice);
                    sendJsonResponse(exchange, 200, response);

                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"error\": \"" + e.getMessage() + "\"}";
                sendJsonResponse(exchange, 500, response);
            }
        }
    }

    static class GuestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            String query = managerId > 0 
                ? "SELECT guest_name, contact_number, COUNT(*) as total_visits, SUM(total_price) as total_spent FROM reservations WHERE manager_id = ? GROUP BY guest_name, contact_number ORDER BY total_spent DESC"
                : "SELECT guest_name, contact_number, 0 as total_visits, 0 as total_spent FROM reservations WHERE 1=0";
            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement pst = conn.prepareStatement(query)) {
                if (managerId > 0) pst.setInt(1, managerId);
                ResultSet rs = pst.executeQuery();
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"guestName\":\"").append(rs.getString("guest_name")).append("\",");
                    json.append("\"contactNumber\":\"").append(rs.getString("contact_number")).append("\",");
                    json.append("\"visits\":").append(rs.getInt("total_visits")).append(",");
                    json.append("\"spent\":").append(rs.getDouble("total_spent"));
                    json.append("}");
                    first = false;
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    static class ReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            if (managerId == -1) { sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}"); return; }

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                String query = "SELECT r.room_type, COUNT(res.reservation_id) as total_bookings, IFNULL(SUM(res.total_price), 0) as total_revenue " +
                             "FROM rooms r " +
                             "LEFT JOIN reservations res ON r.room_number = res.room_number " +
                             "WHERE res.manager_id = ? " +
                             "GROUP BY r.room_type";
                
                try (PreparedStatement pst = conn.prepareStatement(query)) {
                    pst.setInt(1, managerId);
                    ResultSet rs = pst.executeQuery();
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append("{");
                        json.append("\"roomType\":\"").append(rs.getString("room_type")).append("\",");
                        json.append("\"totalBookings\":").append(rs.getInt("total_bookings")).append(",");
                        json.append("\"totalRevenue\":").append(rs.getDouble("total_revenue"));
                        json.append("}");
                        first = false;
                    }
                    json.append("]");
                    sendJsonResponse(exchange, 200, json.toString());
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"Failed to fetch reports\"}");
            }
        }
    }

    // ======================== SETTINGS HANDLERS ========================

    // GET: list all rooms | POST: add a new room
    static class SettingsRoomsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            if (managerId == -1) { sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}"); return; }

            if ("GET".equals(exchange.getRequestMethod())) {
                try (Connection conn = DriverManager.getConnection(url, username, password);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM rooms ORDER BY room_number")) {
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append(String.format(
                            "{\"roomNumber\":%d,\"roomType\":\"%s\",\"price\":%.2f,\"available\":%b}",
                            rs.getInt("room_number"), rs.getString("room_type"),
                            rs.getDouble("price"), rs.getBoolean("is_available")
                        ));
                        first = false;
                    }
                    json.append("]");
                    sendJsonResponse(exchange, 200, json.toString());
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendJsonResponse(exchange, 500, "{\"error\":\"Failed to load rooms\"}");
                }
            } else if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String roomType = extractJsonValue(body, "roomType");
                String priceStr = extractJsonValue(body, "price");
                String countStr = extractJsonValue(body, "count");

                if (roomType.isEmpty() || priceStr.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Room type and price are required\"}");
                    return;
                }

                double price = Double.parseDouble(priceStr);
                int count = 1;
                try { count = Integer.parseInt(countStr); } catch (Exception ignored) {}
                if (count < 1) count = 1;
                if (count > 50) count = 50;

                try (Connection conn = DriverManager.getConnection(url, username, password)) {
                    // Find the current max room number
                    int maxRoom = 0;
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT MAX(room_number) FROM rooms")) {
                        if (rs.next()) maxRoom = rs.getInt(1);
                    }

                    int added = 0;
                    try (PreparedStatement pst = conn.prepareStatement(
                            "INSERT INTO rooms (room_number, room_type, price, is_available) VALUES (?, ?, ?, TRUE)")) {
                        for (int i = 0; i < count; i++) {
                            maxRoom++;
                            pst.setInt(1, maxRoom);
                            pst.setString(2, roomType);
                            pst.setDouble(3, price);
                            pst.executeUpdate();
                            added++;
                        }
                    }

                    sendJsonResponse(exchange, 201, String.format(
                        "{\"success\":true,\"added\":%d,\"message\":\"%d %s room(s) added successfully\"}",
                        added, added, roomType
                    ));
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendJsonResponse(exchange, 500, "{\"error\":\"Failed to add rooms: " + e.getMessage() + "\"}");
                }
            } else {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        }
    }

    // PUT: update room price by room type or specific room number
    static class UpdateRoomPriceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            if (managerId == -1) { sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}"); return; }

            if (!"PUT".equals(exchange.getRequestMethod()) && !"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String priceStr = extractJsonValue(body, "price");
            String roomType = extractJsonValue(body, "roomType");
            String roomNumStr = extractJsonValue(body, "roomNumber");

            if (priceStr.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Price is required\"}");
                return;
            }

            double price = Double.parseDouble(priceStr);

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                int updated = 0;
                if (!roomNumStr.isEmpty()) {
                    // Update specific room
                    try (PreparedStatement pst = conn.prepareStatement("UPDATE rooms SET price = ? WHERE room_number = ?")) {
                        pst.setDouble(1, price);
                        pst.setInt(2, Integer.parseInt(roomNumStr));
                        updated = pst.executeUpdate();
                    }
                } else if (!roomType.isEmpty()) {
                    // Update all rooms of a type
                    try (PreparedStatement pst = conn.prepareStatement("UPDATE rooms SET price = ? WHERE room_type = ?")) {
                        pst.setDouble(1, price);
                        pst.setString(2, roomType);
                        updated = pst.executeUpdate();
                    }
                } else {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Specify roomType or roomNumber\"}");
                    return;
                }

                sendJsonResponse(exchange, 200, String.format(
                    "{\"success\":true,\"updated\":%d,\"message\":\"%d room(s) price updated to ₹%.2f\"}",
                    updated, updated, price
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"Failed to update price\"}");
            }
        }
    }

    // POST: toggle room availability
    static class ToggleRoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            if (managerId == -1) { sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}"); return; }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String roomNumStr = extractJsonValue(body, "roomNumber");

            if (roomNumStr.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"roomNumber is required\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement pst = conn.prepareStatement("UPDATE rooms SET is_available = NOT is_available WHERE room_number = ?")) {
                pst.setInt(1, Integer.parseInt(roomNumStr));
                int updated = pst.executeUpdate();

                // Fetch new status
                boolean newStatus = false;
                try (PreparedStatement ps2 = conn.prepareStatement("SELECT is_available FROM rooms WHERE room_number = ?")) {
                    ps2.setInt(1, Integer.parseInt(roomNumStr));
                    ResultSet rs = ps2.executeQuery();
                    if (rs.next()) newStatus = rs.getBoolean(1);
                }

                sendJsonResponse(exchange, 200, String.format(
                    "{\"success\":true,\"roomNumber\":%s,\"available\":%b}",
                    roomNumStr, newStatus
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"Failed to toggle room\"}");
            }
        }
    }

    // POST: delete a room
    static class DeleteRoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            int managerId = getAuthenticatedManagerId(exchange);
            if (managerId == -1) { sendJsonResponse(exchange, 401, "{\"error\":\"Unauthorized\"}"); return; }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String roomNumStr = extractJsonValue(body, "roomNumber");

            if (roomNumStr.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"roomNumber is required\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                // Check if room has active reservations
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT COUNT(*) FROM reservations WHERE room_number = ?")) {
                    check.setInt(1, Integer.parseInt(roomNumStr));
                    ResultSet rs = check.executeQuery();
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        sendJsonResponse(exchange, 409, "{\"error\":\"Cannot delete room with existing reservations. Please clear reservations first.\"}");
                        return;
                    }
                }

                try (PreparedStatement pst = conn.prepareStatement("DELETE FROM rooms WHERE room_number = ?")) {
                    pst.setInt(1, Integer.parseInt(roomNumStr));
                    int deleted = pst.executeUpdate();
                    sendJsonResponse(exchange, 200, String.format(
                        "{\"success\":true,\"deleted\":%d,\"message\":\"Room %s deleted\"}",
                        deleted, roomNumStr
                    ));
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"Failed to delete room\"}");
            }
        }
    }
}
