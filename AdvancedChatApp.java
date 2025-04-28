package Main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import javax.swing.Timer;
import java.util.List;
import javax.swing.JList;

public class AdvancedChatApp extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton, sendFileButton;
    private JList<String> combinedList; // Danh sách kết hợp
    private DefaultListModel<String> combinedListModel; // Model cho danh sách kết hợp
    private Set<String> groupNames; // Set để lưu tên nhóm
    private Map<String, StringBuilder> chatHistory;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Timer messageRefreshTimer; // Timer để cập nhật tin nhắn

    public AdvancedChatApp() {
        setTitle("Advanced Chat App");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());

        chatHistory = new HashMap<>();
        combinedListModel = new DefaultListModel<>();
        groupNames = new HashSet<>();

        // Kết nối đến server
        try {
            connectToServer();
            loadFriendsAndGroups();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Thiết lập giao diện
        setupUI();

        // Lắng nghe tin nhắn từ server
        new Thread(this::listenForMessages).start();

        // Cập nhật tin nhắn định kỳ
        messageRefreshTimer = new Timer(2000, e -> refreshMessages());
        messageRefreshTimer.start();
    }

    private void connectToServer() throws IOException {
        socket = new Socket("localhost", 12345);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send user ID as the first message after connecting
        out.println(String.valueOf(UserSession.getUserId()));
    }

    private void loadFriendsAndGroups() {
        int userId = UserSession.getUserId();

        // Load Friends
        String friendQuery = "SELECT u.username FROM friends f JOIN users u ON f.friend_id = u.id WHERE f.user_id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(friendQuery)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String friendName = rs.getString("username");
                combinedListModel.addElement(friendName);
                chatHistory.put(friendName, new StringBuilder());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Load Groups
        String groupQuery = "SELECT group_name FROM group_chats WHERE creator_id = ? OR id IN (SELECT group_id FROM group_members WHERE user_id = ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(groupQuery)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String groupName = rs.getString("group_name");
                combinedListModel.addElement(groupName);
                groupNames.add(groupName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupUI() {
        // Thanh tiêu đề
        GradientPanel topPanel = new GradientPanel();
        topPanel.setLayout(new BorderLayout());
        topPanel.setPreferredSize(new Dimension(getWidth(), 70));
        String userName = UserSession.getUsername();
        JLabel titleLabel = new JLabel(userName, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(Color.BLACK);
        topPanel.add(titleLabel, BorderLayout.CENTER);

        // Thanh công cụ với 5 nút
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        toolBar.setBackground(new Color(240, 240, 240));

        // Nút Add Friend
        JButton addFriendButton = new JButton("➕ Add Friend");
        addFriendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        addFriendButton.setBackground(new Color(100, 181, 246));
        addFriendButton.setForeground(Color.WHITE);
        addFriendButton.addActionListener(e -> addFriend());
        toolBar.add(addFriendButton);

        // Nút Remove Friend
        JButton removeFriendButton = new JButton("❌ Remove Friend");
        removeFriendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        removeFriendButton.setBackground(new Color(255, 69, 58));
        removeFriendButton.setForeground(Color.WHITE);
        removeFriendButton.addActionListener(e -> removeFriend());
        toolBar.add(removeFriendButton);

        // Nút Send File
        JButton sendFileButton = new JButton("📂 Send File");
        sendFileButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendFileButton.setBackground(new Color(0, 150, 255));
        sendFileButton.setForeground(Color.WHITE);
        sendFileButton.addActionListener(e -> sendFile());
        toolBar.add(sendFileButton);

        // Nút Create Group
        JButton createGroupButton = new JButton("👥 Create Group");
        createGroupButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        createGroupButton.setBackground(new Color(76, 175, 80));
        createGroupButton.setForeground(Color.WHITE);
        createGroupButton.addActionListener(e -> createGroup());
        toolBar.add(createGroupButton);

        // Nút Remove Group
        JButton removeGroupButton = new JButton("👥 Remove Group");
        removeGroupButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        removeGroupButton.setBackground(new Color(236, 171, 83));
        removeGroupButton.setForeground(Color.WHITE);
        removeGroupButton.addActionListener(e -> removeGroup());
        toolBar.add(removeGroupButton);

        // Nút Update Group
        JButton updateGroupButton = new JButton("👥 Update Group");
        updateGroupButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        updateGroupButton.setBackground(new Color(32 ,178, 170));
        updateGroupButton.setForeground(Color.WHITE);
        updateGroupButton.addActionListener(e -> updateGroup());
        toolBar.add(updateGroupButton);

        JButton signOutButton = new JButton("🚪 Sign Out");
        signOutButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        signOutButton.setBackground(new Color(220, 53, 69)); // Red color for sign out
        signOutButton.setForeground(Color.WHITE);
        signOutButton.addActionListener(e -> signOut());
        toolBar.add(signOutButton);

        // Danh sách kết hợp
        combinedList = new JList<>(combinedListModel);
        combinedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        combinedList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        combinedList.setFixedCellHeight(50);
        combinedList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        combinedList.setBackground(new Color(230, 230, 250));
        combinedList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selectedItem = combinedList.getSelectedValue();
                if (selectedItem != null) {
                    if (isGroup(selectedItem)) {
                        loadMessagesForGroup(selectedItem);
                    } else {
                        loadMessagesForUser(selectedItem);
                    }
                }
            }
        });
        JScrollPane combinedScrollPane = new JScrollPane(combinedList);
        combinedScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Khu vực chat
        GradientPanel chatBackgroundPanel = new GradientPanel();
        chatBackgroundPanel.setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setOpaque(false);
        chatArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    int lineIndex = chatArea.getLineOfOffset(chatArea.viewToModel2D(e.getPoint()));
                    int startOffset = chatArea.getLineStartOffset(lineIndex);
                    int endOffset = chatArea.getLineEndOffset(lineIndex);
                    String clickedLine = chatArea.getText(startOffset, endOffset - startOffset);

                    if (clickedLine.contains("[Click to download]") || clickedLine.contains("sent a file:")) {
                        // Extract the file name
                        String fileName = null;

                        // Try to find the file name pattern: "sent a file: filename.ext"
                        int fileMarkerIndex = clickedLine.indexOf("sent a file:");
                        if (fileMarkerIndex != -1) {
                            // Start after "sent a file: "
                            int filenameStart = fileMarkerIndex + "sent a file:".length() + 1;

                            // Find where the filename ends (before " [Click to download]")
                            int filenameEnd = clickedLine.indexOf(" [Click", filenameStart);
                            if (filenameEnd == -1) { // If not found, try with "("
                                filenameEnd = clickedLine.indexOf(" (", filenameStart);
                            }

                            if (filenameEnd != -1) {
                                fileName = clickedLine.substring(filenameStart, filenameEnd).trim();
                            } else {
                                // Just take everything after "sent a file: "
                                fileName = clickedLine.substring(filenameStart).trim();
                            }
                        } else if (clickedLine.contains("You sent a file:")) {
                            // Pattern: "You sent a file: filename.ext"
                            int filenameStart = clickedLine.indexOf("You sent a file:") + "You sent a file:".length() + 1;
                            int filenameEnd = clickedLine.indexOf(" (", filenameStart);

                            if (filenameEnd != -1) {
                                fileName = clickedLine.substring(filenameStart, filenameEnd).trim();
                            } else {
                                fileName = clickedLine.substring(filenameStart).trim();
                            }
                        }

                        if (fileName != null && !fileName.isEmpty()) {
                            downloadFileWithLocationSelection(fileName);
                        } else {
                            JOptionPane.showMessageDialog(AdvancedChatApp.this,
                                    "Could not extract file name from: " + clickedLine);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(AdvancedChatApp.this,
                            "Error processing click: " + ex.getMessage());
                }
            }
        });
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setOpaque(false);
        chatScrollPane.getViewport().setOpaque(false);
        chatBackgroundPanel.add(chatScrollPane, BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));

        sendButton = new JButton("📨 Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        sendButton.setBackground(new Color(0, 150, 255));
        sendButton.setForeground(Color.WHITE);
        sendButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        sendButton.addActionListener(e -> sendMessage());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(toolBar, BorderLayout.NORTH); // Thêm thanh công cụ vào phía trên
        chatPanel.add(chatBackgroundPanel, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, combinedScrollPane, chatPanel);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.3);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                signOut();
            }
        });
        setVisible(true);
    }
    private void createGroup() {
        String groupName = JOptionPane.showInputDialog(this, "Enter group name:");
        if (groupName != null && !groupName.trim().isEmpty()) {
            List<String> selectedFriends = getSelectedFriends();
            if (selectedFriends.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one friend to add to the group.");
                return;
            }

            // Thêm nhóm vào cơ sở dữ liệu
            int groupId = addGroupToDatabase(groupName, UserSession.getUserId());
            if (groupId != -1) {
                for (String friend : selectedFriends) {
                    int friendId = getUserIdByUsername(friend);
                    addMemberToGroup(groupId, friendId);
                }
                JOptionPane.showMessageDialog(this, "Group created successfully!");

                // Tải lại danh sách bạn bè và nhóm
                combinedListModel.clear(); // Xóa danh sách hiện tại
                loadFriendsAndGroups(); // Tải lại danh sách bạn bè và nhóm
            }
        }
    }


    private void updateGroup() {
        String selectedGroup = (String) JOptionPane.showInputDialog(
                this,
                "Select a group to update:",
                "Update Group",
                JOptionPane.PLAIN_MESSAGE,
                null,
                getGroups(),
                null
        );

        if (selectedGroup != null) {
            // Hiển thị danh sách bạn bè hiện có để chọn
            List<String> selectedFriends = getSelectedFriends();
            if (selectedFriends.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one friend to add or remove.");
                return;
            }

            // Hỏi người dùng có muốn thêm hay xóa bạn bè
            String[] options = {"Add Friends", "Remove Friends"};
            int choice = JOptionPane.showOptionDialog(this,
                    "Would you like to add or remove friends?",
                    "Update Group",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) { // Thêm bạn bè
                for (String friend : selectedFriends) {
                    int friendId = getUserIdByUsername(friend);
                    addMemberToGroup(getGroupIdByName(selectedGroup), friendId);
                }
                JOptionPane.showMessageDialog(this, "Friends added to the group successfully!");
            } else if (choice == 1) { // Xóa bạn bè
                for (String friend : selectedFriends) {
                    int friendId = getUserIdByUsername(friend);
                    removeMemberFromGroup(getGroupIdByName(selectedGroup), friendId);
                }
                JOptionPane.showMessageDialog(this, "Friends removed from the group successfully!");
            }
        }
    }

    private void removeMemberFromGroup(int groupId, int userId) {
        String query = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<String> getSelectedFriends() {
        // Tạo một mảng từ combinedListModel
        String[] friendsArray = new String[combinedListModel.size()];
        for (int i = 0; i < combinedListModel.size(); i++) {
            friendsArray[i] = combinedListModel.getElementAt(i);
        }

        // Tạo JList từ mảng
        JList<String> friendList = new JList<>(friendsArray);
        friendList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Hiển thị hộp thoại để người dùng chọn bạn bè
        JOptionPane.showMessageDialog(this, new JScrollPane(friendList), "Select Friends", JOptionPane.PLAIN_MESSAGE);

        return friendList.getSelectedValuesList();
    }
    private int addGroupToDatabase(String groupName, int creatorId) {
        // Thêm nhóm vào cơ sở dữ liệu và trả về ID của nhóm
        String query = "INSERT INTO group_chats (group_name, creator_id) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, groupName);
            stmt.setInt(2, creatorId);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1); // ID của nhóm vừa tạo
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // Thất bại
    }

    private void addMemberToGroup(int groupId, int userId) {
        // Thêm thành viên vào nhóm trong cơ sở dữ liệu
        String query = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void removeGroup() {
        // Lấy danh sách nhóm mà người dùng tham gia
        String[] groups = getGroups();
        if (groups.length == 0) {
            JOptionPane.showMessageDialog(this, "You are not a member of any groups.");
            return;
        }

        String selectedGroup = (String) JOptionPane.showInputDialog(this, "Select a group to remove:", "Remove Group", JOptionPane.PLAIN_MESSAGE, null, groups, groups[0]);

        if (selectedGroup != null) {
            int groupId = getGroupIdByName(selectedGroup);
            if (removeGroupFromDatabase(groupId)) {
                JOptionPane.showMessageDialog(this, "Group removed successfully!");
                combinedListModel.removeElement(selectedGroup); // Cập nhật danh sách nhóm
            } else {
                JOptionPane.showMessageDialog(this, "Failed to remove group.");
            }
        }
    }

    private String[] getGroups() {
        List<String> userGroups = new ArrayList<>();
        int userId = UserSession.getUserId(); // Lấy ID của người dùng hiện tại
        String query = "SELECT gc.group_name FROM group_chats gc " +
                "JOIN group_members gm ON gc.id = gm.group_id " +
                "WHERE gm.user_id = ? OR gc.creator_id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                userGroups.add(rs.getString("group_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userGroups.toArray(new String[0]);
    }

    private boolean removeGroupFromDatabase(int groupId) {
        // Xóa nhóm khỏi cơ sở dữ liệu
        String query = "DELETE FROM group_chats WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, groupId);
            return stmt.executeUpdate() > 0; // Trả về true nếu xóa thành công
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // Thất bại
    }

    private boolean isGroup(String name) {
        return groupNames.contains(name);
    }

    private void loadMessagesForUser(String username) {
        chatArea.setText("");
        String messagesQuery = "SELECT m.content, u.username, m.created_at FROM messages m JOIN users u ON m.sender_id = u.id WHERE (m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?) ORDER BY m.created_at";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(messagesQuery)) {
            int userId = UserSession.getUserId();
            int friendId = getUserIdByUsername(username);
            stmt.setInt(1, userId);
            stmt.setInt(2, friendId);
            stmt.setInt(3, friendId);
            stmt.setInt(4, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String msg = rs.getString("content");
                String sender = rs.getString("username");
                String timestamp = rs.getString("created_at");

                // Handle file messages specially
                if (msg.startsWith("[FILE] ")) {
                    String fileName = msg.substring(7); // Remove the [FILE] prefix

                    // For files, add download information
                    if (sender.equals(UserSession.getUsername())) {
                        chatArea.append("You sent a file: " + fileName + " (" + timestamp + ")\n");
                    } else {
                        // Add a clickable message for received files
                        chatArea.append(sender + " sent a file: " + fileName + " [Click to download] (" + timestamp + ")\n");
                    }
                } else {
                    // Regular text message
                    chatArea.append(sender + ": " + msg + " (" + timestamp + ")\n");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadMessagesForGroup(String groupName) {
        chatArea.setText("");
        String groupMessagesQuery = "SELECT gm.content, u.username, gm.created_at FROM group_messages gm JOIN users u ON gm.sender_id = u.id JOIN group_chats gc ON gm.group_id = gc.id WHERE gc.group_name = ? ORDER BY gm.created_at";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(groupMessagesQuery)) {
            stmt.setString(1, groupName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String msg = rs.getString("content");
                String sender = rs.getString("username");
                String timestamp = rs.getString("created_at");

                // Handle file messages specially
                if (msg.startsWith("[FILE] ")) {
                    String fileName = msg.substring(7); // Remove the [FILE] prefix

                    // For files, add download information
                    if (sender.equals(UserSession.getUsername())) {
                        chatArea.append("You sent a file: " + fileName + " (" + timestamp + ")\n");
                    } else {
                        // Add a clickable message for received files
                        chatArea.append(sender + " sent a file: " + fileName + " [Click to download] (" + timestamp + ")\n");
                    }
                } else {
                    // Regular text message
                    chatArea.append(sender + ": " + msg + " (" + timestamp + ")\n");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void addFriend() {
        String friendUsername = JOptionPane.showInputDialog(this, "Enter friend's username:");
        if (friendUsername != null && !friendUsername.trim().isEmpty()) {
            int userId = UserSession.getUserId();
            int friendId = getUserIdByUsername(friendUsername);
            if (friendId != -1) {
                try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                     PreparedStatement stmt = conn.prepareStatement("INSERT INTO friends (user_id, friend_id) VALUES (?, ?)")) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, friendId);
                    stmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Friend added successfully!");

                    // Reload friends and groups
                    combinedListModel.clear();
                    loadFriendsAndGroups();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(this, "User not found!");
            }
        }
    }

    private void removeFriend() {
        String selectedFriend = combinedList.getSelectedValue();

        if (selectedFriend == null) {
            JOptionPane.showMessageDialog(this, "Please select a friend to remove!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove " + selectedFriend + " from your friends list?",
                "Confirm Remove Friend", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            int userId = UserSession.getUserId();
            int friendId = getUserIdByUsername(selectedFriend);

            if (friendId != -1) {
                try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM friends WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)")) {

                    stmt.setInt(1, userId);
                    stmt.setInt(2, friendId);
                    stmt.setInt(3, friendId);
                    stmt.setInt(4, userId);
                    int affectedRows = stmt.executeUpdate();

                    if (affectedRows > 0) {
                        combinedListModel.removeElement(selectedFriend);
                        JOptionPane.showMessageDialog(this, selectedFriend + " has been removed from your friend list.");
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to remove friend. Please try again.");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Database error while removing friend.");
                }
            } else {
                JOptionPane.showMessageDialog(this, "Friend not found in database.");
            }
        }
    }



    private int getUserIdByUsername(String username) {
        int userId = -1;
        String query = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                userId = rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return userId;
    }

    private void refreshMessages() {
        String selectedItem = combinedList.getSelectedValue();
        if (selectedItem != null) {
            if (isGroup(selectedItem)) {
                loadMessagesForGroup(selectedItem);
            } else {
                loadMessagesForUser(selectedItem);
            }
        }
    }

    private void saveMessageToDatabase(int senderId, int receiverId, String content) {
        String query = "INSERT INTO messages (sender_id, receiver_id, content, is_read, created_at) VALUES (?, ?, ?, 0, NOW())";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setString(3, content);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveGroupMessageToDatabase(String groupName, int senderId, String content) {
        String query = "INSERT INTO group_messages (group_id, sender_id, content, created_at) VALUES (?, ?, ?, NOW())";
        int groupId = getGroupIdByName(groupName);
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, senderId);
            stmt.setString(3, content);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getGroupIdByName(String groupName) {
        int groupId = -1;
        String query = "SELECT id FROM group_chats WHERE group_name = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, groupName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                groupId = rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groupId;
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("FILE_BEGIN:")) {
                    // File transfer is starting
                    handleIncomingFile(message);
                } else if (message.startsWith("FILE_AVAILABLE:")) {
                    // File is available on the server
                    String[] parts = message.split(":", 4);
                    if (parts.length == 4) {
                        String sender = parts[1];
                        String fileName = parts[2];
                        String fileSize = parts[3];
                        chatArea.append(sender + " sent a file: " + fileName + " [Click to download] (" + fileSize + " bytes)\n");
                        // Store reference to file
                        final String fileNameFinal = fileName;
                        chatArea.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                downloadFileWithLocationSelection(fileNameFinal);
                            }
                        });
                    }
                } else if (message.startsWith("FILE:")) {
                    // Legacy file notification (keep for backward compatibility)
                    String[] parts = message.split(":", 4);
                    if (parts.length == 4) {
                        String sender = parts[1];
                        String fileName = parts[2];
                        String filePath = parts[3];
                        chatArea.append(sender + " sent a file: " + fileName + " [Click to download]\n");
                        // Store reference to file
                        final String fileNameFinal = fileName;
                        chatArea.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                downloadFileWithLocationSelection(fileNameFinal);
                            }
                        });
                    }
                } else {
                    // Regular message handling
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        if (parts[0].equals("MESSAGE")) {
                            String sender = parts[1];
                            String msg = parts[2];
                            chatArea.append(sender + ": " + msg + "\n");
                            if (chatHistory.containsKey(sender)) {
                                chatHistory.get(sender).append(sender + ": ").append(msg).append("\n");
                            }
                        } else if (parts[1].equals("G")) { // Group message
                            String sender = parts[0];
                            String msg = parts[2];
                            chatArea.append(sender + " (Group): " + msg + "\n");
                        }
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingFile(String message) {
        try {
            // Parse the metadata
            String[] parts = message.split(":", 4);
            if (parts.length == 4) {
                String sender = parts[1];
                String fileName = parts[2];
                long fileSize = Long.parseLong(parts[3]);

                // Create progress dialog
                JDialog progressDialog = new JDialog(this, "Receiving File", true);
                JProgressBar progressBar = new JProgressBar(0, 100);
                progressBar.setStringPainted(true);
                progressDialog.add(BorderLayout.CENTER, new JLabel("Receiving file: " + fileName));
                progressDialog.add(BorderLayout.SOUTH, progressBar);
                progressDialog.setSize(300, 100);
                progressDialog.setLocationRelativeTo(this);

                // Ask user where to save the file
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Save File As");
                fileChooser.setSelectedFile(new File(fileName));

                int userSelection = fileChooser.showSaveDialog(this);

                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    File destFile = fileChooser.getSelectedFile();

                    // Start receiving in a background thread
                    new Thread(() -> {
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            InputStream socketIn = socket.getInputStream();
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalReceived = 0;

                            // Read until we get all the data
                            while (totalReceived < fileSize &&
                                    (bytesRead = socketIn.read(buffer, 0,
                                            (int)Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                totalReceived += bytesRead;

                                // Update progress
                                final int progress = (int)((totalReceived * 100) / fileSize);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                            }

                            SwingUtilities.invokeLater(() -> {
                                progressDialog.dispose();
                                JOptionPane.showMessageDialog(AdvancedChatApp.this,
                                        "File received successfully: " + destFile.getAbsolutePath());
                                chatArea.append(sender + " sent a file: " + fileName + " (Saved to " +
                                        destFile.getAbsolutePath() + ")\n");
                            });

                        } catch (IOException e) {
                            SwingUtilities.invokeLater(() -> {
                                progressDialog.dispose();
                                JOptionPane.showMessageDialog(AdvancedChatApp.this,
                                        "Error receiving file: " + e.getMessage());
                            });
                            e.printStackTrace();
                        }
                    }).start();

                    // Show progress dialog
                    SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error processing file: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String selectedItem = combinedList.getSelectedValue();
        String message = inputField.getText().trim();
        if (!message.isEmpty() && selectedItem != null) {
            String timestamp = new Timestamp(System.currentTimeMillis()).toString(); // Lấy thời gian hiện tại
            if (isGroup(selectedItem)) {
                chatArea.append("You (Group): " + message + " (" + timestamp + ")\n");
                out.println(selectedItem + ":G:" + message);
                saveGroupMessageToDatabase(selectedItem, UserSession.getUserId(), message);
            } else {
                chatHistory.get(selectedItem).append("You: ").append(message).append("\n");
                chatArea.append("You: " + message + " (" + timestamp + ")\n");
                out.println(selectedItem + ":" + message);
                saveMessageToDatabase(UserSession.getUserId(), getUserIdByUsername(selectedItem), message);
            }
            inputField.setText("");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } else {
            JOptionPane.showMessageDialog(this, "Please select a friend or group to chat with!");
        }
    }

    private void sendFile() {
        if (socket == null || socket.isClosed() || out == null) {
            JOptionPane.showMessageDialog(this, "Not connected to server. Please restart the application.");
            return;
        }

        String selectedItem = combinedList.getSelectedValue();
        if (selectedItem == null) {
            JOptionPane.showMessageDialog(this, "Please select a friend or group to send a file to!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String fileName = selectedFile.getName();
            long fileSize = selectedFile.length();

            try {
                // Create a dedicated socket for file transfer to avoid interfering with chat socket
                Socket fileSocket = new Socket("localhost", 12345);
                PrintWriter fileOut = new PrintWriter(fileSocket.getOutputStream(), true);

                // Identify this connection as a file transfer socket
                fileOut.println("FILE_TRANSFER:" + UserSession.getUserId());

                // Send file metadata
                fileOut.println("FILE_BEGIN:" + selectedItem + ":" + fileName + ":" + fileSize);

                // Create a progress dialog
                JDialog progressDialog = new JDialog(this, "Sending File", true);
                JProgressBar progressBar = new JProgressBar(0, 100);
                progressBar.setStringPainted(true);
                progressDialog.add(BorderLayout.CENTER, new JLabel("Sending file: " + fileName));
                progressDialog.add(BorderLayout.SOUTH, progressBar);
                progressDialog.setSize(300, 100);
                progressDialog.setLocationRelativeTo(this);

                // Start sending in a background thread
                new Thread(() -> {
                    try (FileInputStream fis = new FileInputStream(selectedFile);
                         OutputStream socketOut = fileSocket.getOutputStream()) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalSent = 0;

                        while ((bytesRead = fis.read(buffer)) != -1) {
                            socketOut.write(buffer, 0, bytesRead);
                            totalSent += bytesRead;

                            // Update progress
                            final int progress = (int)((totalSent * 100) / fileSize);
                            SwingUtilities.invokeLater(() -> progressBar.setValue(progress));

                            // Add a small delay to prevent socket overloading
                            Thread.sleep(1);
                        }

                        // Send a special end-of-file marker (empty buffer)
                        socketOut.write(new byte[0]);
                        socketOut.flush();

                        // Wait for server confirmation
                        try (BufferedReader confirmReader = new BufferedReader(new InputStreamReader(fileSocket.getInputStream()))) {
                            String confirmation = confirmReader.readLine();
                            if (confirmation != null && confirmation.startsWith("FILE_RECEIVED:")) {
                                SwingUtilities.invokeLater(() -> {
                                    progressDialog.dispose();
                                    chatArea.append("You sent a file: " + fileName + "\n");
                                    JOptionPane.showMessageDialog(AdvancedChatApp.this,
                                            "File sent successfully!");
                                });
                            }
                        }

                        // Close file socket after transfer
                        fileSocket.close();

                    } catch (IOException | InterruptedException e) {
                        SwingUtilities.invokeLater(() -> {
                            progressDialog.dispose();
                            JOptionPane.showMessageDialog(AdvancedChatApp.this,
                                    "Error sending file: " + e.getMessage());
                        });
                        e.printStackTrace();
                    }
                }).start();

                // Show progress dialog (non-modal so it doesn't block)
                progressDialog.setModal(false);
                SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error preparing file transfer: " + e.getMessage());
            }
        }
    }

    private void requestFileDownload(String fileName) {
        try {
            // Open a dedicated connection for file download
            Socket downloadSocket = new Socket("localhost", 12345);
            PrintWriter downloadOut = new PrintWriter(downloadSocket.getOutputStream(), true);

            // Identify this connection as a file download
            downloadOut.println("FILE_DOWNLOAD:" + UserSession.getUserId());

            // Request specific file
            downloadOut.println("FILE_REQUEST:" + fileName);

            // Listen for response on this socket
            BufferedReader downloadIn = new BufferedReader(new InputStreamReader(downloadSocket.getInputStream()));
            String response = downloadIn.readLine();

            if (response != null && response.startsWith("FILE_INFO:")) {
                // Parse file info
                String[] parts = response.split(":", 3);
                if (parts.length == 3) {
                    String receivedFileName = parts[1];
                    long fileSize = Long.parseLong(parts[2]);

                    // Choose save location
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Save File As");
                    fileChooser.setSelectedFile(new File(receivedFileName));

                    int userSelection = fileChooser.showSaveDialog(this);
                    if (userSelection == JFileChooser.APPROVE_OPTION) {
                        File destFile = fileChooser.getSelectedFile();

                        // Show progress
                        JDialog progressDialog = new JDialog(this, "Downloading File", false);
                        JProgressBar progressBar = new JProgressBar(0, 100);
                        progressBar.setStringPainted(true);
                        progressDialog.add(BorderLayout.CENTER, new JLabel("Downloading: " + receivedFileName));
                        progressDialog.add(BorderLayout.SOUTH, progressBar);
                        progressDialog.setSize(300, 100);
                        progressDialog.setLocationRelativeTo(this);
                        progressDialog.setVisible(true);

                        // Receive file data
                        try (FileOutputStream fileOut = new FileOutputStream(destFile)) {
                            InputStream socketIn = downloadSocket.getInputStream();

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalReceived = 0;

                            while (totalReceived < fileSize &&
                                    (bytesRead = socketIn.read(buffer)) > 0) {
                                fileOut.write(buffer, 0, bytesRead);
                                totalReceived += bytesRead;

                                final int progress = (int)((totalReceived * 100) / fileSize);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                            }

                            progressDialog.dispose();
                            JOptionPane.showMessageDialog(this, "File downloaded successfully!");
                        }
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "File not available for download.");
            }

            downloadSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error downloading file: " + e.getMessage());
        }
    }

    private void signOut() {
        int response = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to sign out?",
                "Confirm Sign Out",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            try {
                // Notify the server that the user is signing out
                if (out != null && !socket.isClosed()) {
                    out.println("SIGN_OUT:" + UserSession.getUserId());
                }

                // Close socket connections
                closeConnections();

                // Clear the user session
                UserSession.clearUserSession();

                // Close the current window
                this.dispose();

                // Open the sign-in screen
                SwingUtilities.invokeLater(Sign_in::new);

                JOptionPane.showMessageDialog(
                        null,
                        "You have signed out successfully.",
                        "Sign Out",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        this,
                        "Error during sign out: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void closeConnections() {
        try {
            if (messageRefreshTimer != null) {
                messageRefreshTimer.stop();
            }

            if (in != null) {
                in.close();
            }

            if (out != null) {
                out.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            System.out.println("All connections closed");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        closeConnections();
        super.dispose();
    }

    private void downloadFileWithLocationSelection(String fileName) {
        System.out.println("Requesting download for file: " + fileName);
        requestFileDownload(fileName);
    }

    private static class GradientPanel extends JPanel {
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdvancedChatApp::new);
    }
}
