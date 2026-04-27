import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

public class HotelReservationSystemUI extends JFrame {
    private static final String url = "jdbc:mysql://localhost:3306/hotel_db";
    private static final String username = "root";
    private static final String password = "shree";

    private JTextField txtGuestName, txtRoomNumber, txtContactNumber, txtSearch;
    private JTable table;
    private DefaultTableModel tableModel;

    public HotelReservationSystemUI() {
        setTitle("Premium Hotel Reservation System");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Set System Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(41, 128, 185));
        JLabel lblTitle = new JLabel("HOTEL MANAGEMENT SYSTEM");
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        headerPanel.add(lblTitle);
        add(headerPanel, BorderLayout.NORTH);

        // Main Content Panel
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left Panel: Form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createTitledBorder("Add New Reservation"));

        formPanel.add(createInputGroup("Guest Name:", txtGuestName = new JTextField()));
        formPanel.add(createInputGroup("Room Number:", txtRoomNumber = new JTextField()));
        formPanel.add(createInputGroup("Contact:", txtContactNumber = new JTextField()));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton btnAdd = new JButton("Reserve Room");
        JButton btnUpdate = new JButton("Update Selected");
        JButton btnDelete = new JButton("Delete Selected");
        JButton btnClear = new JButton("Clear Form");

        btnAdd.setBackground(new Color(46, 204, 113));
        btnAdd.setForeground(Color.WHITE);
        btnDelete.setBackground(new Color(231, 76, 60));
        btnDelete.setForeground(Color.WHITE);

        btnPanel.add(btnAdd);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnDelete);
        btnPanel.add(btnClear);
        formPanel.add(btnPanel);

        // Right Panel: Table
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("Current Reservations"));

        // Search Bar
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("Search Guest: "), BorderLayout.WEST);
        txtSearch = new JTextField();
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        tablePanel.add(searchPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Guest Name", "Room", "Contact", "Date"}, 0);
        table = new JTable(tableModel);
        table.setRowHeight(25);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        mainPanel.add(formPanel);
        mainPanel.add(tablePanel);
        add(mainPanel, BorderLayout.CENTER);

        // Event Listeners
        btnAdd.addActionListener(e -> reserveRoom());
        btnUpdate.addActionListener(e -> updateReservation());
        btnDelete.addActionListener(e -> deleteReservation());
        btnClear.addActionListener(e -> clearForm());
        txtSearch.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                loadReservations(txtSearch.getText());
            }
        });

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                fillFormFromSelectedRow();
            }
        });

        loadReservations("");
    }

    private JPanel createInputGroup(String labelText, JTextField textField) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        panel.add(new JLabel(labelText), BorderLayout.NORTH);
        panel.add(textField, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return panel;
    }

    private void loadReservations(String query) {
        tableModel.setRowCount(0);
        String sql = "SELECT * FROM reservations WHERE guest_name LIKE ?";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + query + "%");
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("reservation_id"));
                row.add(rs.getString("guest_name"));
                row.add(rs.getInt("room_number"));
                row.add(rs.getString("contact_number"));
                row.add(rs.getTimestamp("reservation_date"));
                tableModel.addRow(row);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading data: " + e.getMessage());
        }
    }

    private void reserveRoom() {
        String name = txtGuestName.getText();
        String room = txtRoomNumber.getText();
        String contact = txtContactNumber.getText();

        if (name.isEmpty() || room.isEmpty() || contact.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields!");
            return;
        }

        String sql = "INSERT INTO reservations (guest_name, room_number, contact_number) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, Integer.parseInt(room));
            pstmt.setString(3, contact);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Reservation successful!");
            clearForm();
            loadReservations("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void updateReservation() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a reservation to update!");
            return;
        }

        int id = (int) tableModel.getValueAt(selectedRow, 0);
        String sql = "UPDATE reservations SET guest_name=?, room_number=?, contact_number=? WHERE reservation_id=?";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, txtGuestName.getText());
            pstmt.setInt(2, Integer.parseInt(txtRoomNumber.getText()));
            pstmt.setString(3, txtContactNumber.getText());
            pstmt.setInt(4, id);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Update successful!");
            loadReservations("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void deleteReservation() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a reservation to delete!");
            return;
        }

        int id = (int) tableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this reservation?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            String sql = "DELETE FROM reservations WHERE reservation_id=?";
            try (Connection conn = DriverManager.getConnection(url, username, password);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
                loadReservations("");
                clearForm();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
            }
        }
    }

    private void fillFormFromSelectedRow() {
        int row = table.getSelectedRow();
        txtGuestName.setText(tableModel.getValueAt(row, 1).toString());
        txtRoomNumber.setText(tableModel.getValueAt(row, 2).toString());
        txtContactNumber.setText(tableModel.getValueAt(row, 3).toString());
    }

    private void clearForm() {
        txtGuestName.setText("");
        txtRoomNumber.setText("");
        txtContactNumber.setText("");
        table.clearSelection();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HotelReservationSystemUI().setVisible(true));
    }
}
