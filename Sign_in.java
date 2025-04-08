package Main;

import java.sql.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sign_in {
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;

    public Sign_in() {
        frame = new JFrame("Sign In");
        frame.setSize(650, 400); // Kích thước cửa sổ
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Gradient Background Panel
        GradientPanel panel = new GradientPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.anchor = GridBagConstraints.CENTER;

        // Username Label and Field
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        panel.add(usernameLabel, gbc);
        
        usernameField = new JTextField(28);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        // Password Label and Field
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        panel.add(passwordLabel, gbc);
        
        passwordField = new JPasswordField(28);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        // Căn chỉnh các nút
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10)); // Căn giữa với khoảng cách

        // Sign In Button
        JButton signInButton = createButton("Sign In");
        signInButton.addActionListener(e -> loginUser());
        buttonPanel.add(signInButton);

        // Switch to Sign Up Button
        JButton switchToSignUp = createButton("Don't have an account?");
        switchToSignUp.addActionListener(e -> {
            frame.dispose();
            new Sign_up();
        });
        buttonPanel.add(switchToSignUp);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2; // Đặt lại để chiếm toàn bộ chiều ngang
        panel.add(buttonPanel, gbc);

        frame.add(panel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Sign_in::new);
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Kích thước phông chữ
        button.setBackground(new Color(0, 150, 255));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(0, 150, 255), 2, true));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(200, 40)); // Kích thước nút

        // Hiệu ứng hover
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(0, 120, 210));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(0, 150, 255));
            }
        });

        return button;
    }

    private void loginUser() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        
        // Hash the password
        String passwordHash = hashPassword(password);

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "")) {
            String query = "SELECT * FROM users WHERE username = ? AND password_hash = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // Save user session
                int userId = rs.getInt("id");
                UserSession.saveUserSession(username, userId);
                
                JOptionPane.showMessageDialog(frame, "Login Successful!");
                frame.dispose();
                new AdvancedChatApp();
            } else {
                JOptionPane.showMessageDialog(frame, "Invalid Credentials!");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage());
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Custom Gradient Panel
    static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            int width = getWidth();
            int height = getHeight();
            GradientPaint gradient = new GradientPaint(0, 0, new Color(212, 234, 247), 0, height, new Color(230, 212, 247));
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, width, height);
        }
    }
}