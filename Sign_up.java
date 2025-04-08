package Main;

import java.sql.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sign_up {
    private JFrame frame;
    private JTextField usernameField, emailField;
    private JPasswordField passwordField;

    public Sign_up() {
        frame = new JFrame("Sign Up");
        frame.setSize(600, 400); // Kích thước cửa sổ
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
        usernameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16)); // Kích thước phông chữ lớn hơn
        panel.add(usernameLabel, gbc);
        
        usernameField = new JTextField(28);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 14)); // Kích thước phông chữ lớn hơn
        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        // Email Label and Field
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setFont(new Font("Segoe UI", Font.BOLD, 16)); // Kích thước phông chữ lớn hơn
        panel.add(emailLabel, gbc);
        
        emailField = new JTextField(28);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 14)); // Kích thước phông chữ lớn hơn
        gbc.gridx = 1;
        panel.add(emailField, gbc);

        // Password Label and Field
        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 16)); // Kích thước phông chữ lớn hơn
        panel.add(passwordLabel, gbc);
        
        passwordField = new JPasswordField(28);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14)); // Kích thước phông chữ lớn hơn
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        // Căn chỉnh các nút
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10)); // Căn giữa với khoảng cách

        // Sign Up Button
        JButton signUpButton = createButton("Sign Up");
        signUpButton.addActionListener(e -> registerUser());
        buttonPanel.add(signUpButton);

        // Switch to Sign In Button
        JButton switchToSignIn = createButton("Already have an account? Sign In");
        switchToSignIn.addActionListener(e -> {
            frame.dispose();
            new Sign_in();
        });
        buttonPanel.add(switchToSignIn);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2; // Chiếm toàn bộ chiều ngang
        panel.add(buttonPanel, gbc);

        frame.add(panel);
        frame.setLocationRelativeTo(null); // Căn giữa cửa sổ
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Sign_up::new);
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
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

    private void registerUser() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Kiểm tra tính hợp lệ của dữ liệu nhập
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Vui lòng điền đầy đủ các trường.");
            return;
        }

        // Băm mật khẩu bằng SHA-256
        String passwordHash = hashPassword(password);

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "")) {
            String query = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, passwordHash);
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(frame, "Đăng ký thành công!");
            frame.dispose();
            new Sign_in();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Lỗi: " + ex.getMessage());
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