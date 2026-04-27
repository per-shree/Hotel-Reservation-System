import java.sql.*;

public class DBInit {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/hotel_db";
        try (Connection conn = DriverManager.getConnection(url, "root", "shree");
             Statement stmt = conn.createStatement()) {
            
            // Create managers table for authentication
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
            System.out.println("Managers table created/verified successfully!");

            // Add manager_id column to reservations if not exists
            try {
                stmt.executeUpdate("ALTER TABLE reservations ADD COLUMN manager_id INT DEFAULT NULL");
                System.out.println("Added manager_id column to reservations.");
            } catch (Exception ignore) {
                // Column already exists
            }

            // Update the room rates to realistic Rupee values
            stmt.executeUpdate("UPDATE rooms SET price = 2500.00 WHERE room_type = 'Standard'");
            stmt.executeUpdate("UPDATE rooms SET price = 5000.00 WHERE room_type = 'Deluxe'");
            stmt.executeUpdate("UPDATE rooms SET price = 12000.00 WHERE room_type = 'Suite'");
            
            System.out.println("Room Rates successfully updated to realistic Rupee amounts!");
            System.out.println("Standard: \u20B92500 | Deluxe: \u20B95000 | Suite: \u20B912000");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
