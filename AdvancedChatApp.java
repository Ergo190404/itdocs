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
    setVisible(true);
}
    private static final String FILE_STORAGE_PATH = "C:/Users/User/Desktop/Folder/ChatSystem_experiment/src/main/java/ChatFiles";
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

    private String listFilesInDirectory() {
        File dir = new File(FILE_STORAGE_PATH);
        if (!dir.exists() || !dir.isDirectory()) {
            return "Storage directory does not exist or is not a directory";
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return "No files in directory";
        }

        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            sb.append(file.getName()).append(" (").append(file.length()).append(" bytes)\n");
        }

        return sb.toString();
    }

    private void checkAndDownloadFile(String fileName) {
        System.out.println("Attempting to download file: " + fileName);

        // First, trim any whitespace that might interfere with the search
        fileName = fileName.trim();

        // Show a debug message with the exact file name we're searching for
        System.out.println("Searching for file with exact name: '" + fileName + "'");

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "")) {
            // Use a more flexible query that will find partial matches if needed
            String query = "SELECT * FROM files WHERE file_name LIKE ? ORDER BY created_at DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                // Use LIKE with % for more flexible matching
                stmt.setString(1, "%" + fileName + "%");

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int fileId = rs.getInt("id");
                    String actualFileName = rs.getString("file_name");
                    System.out.println("Found file in database: ID=" + fileId + ", Name=" + actualFileName);

                    // Try to open the file with the exact name from the database
                    File file = new File(FILE_STORAGE_PATH, actualFileName);
                    if (file.exists()) {
                        try {
                            System.out.println("Opening file: " + file.getAbsolutePath());
                            Desktop.getDesktop().open(file);
                        } catch (IOException e) {
                            e.printStackTrace();
                            JOptionPane.showMessageDialog(this,
                                    "Error opening file: " + e.getMessage() +
                                            "\nPath: " + file.getAbsolutePath());
                        }
                    } else {
                        // Try with just the file name without path
                        String simpleFileName = actualFileName;
                        if (simpleFileName.contains("\\")) {
                            simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("\\") + 1);
                        } else if (simpleFileName.contains("/")) {
                            simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("/") + 1);
                        }

                        File simpleFile = new File(FILE_STORAGE_PATH, simpleFileName);

                        if (simpleFile.exists()) {
                            System.out.println("Opening simplified file: " + simpleFile.getAbsolutePath());
                            Desktop.getDesktop().open(simpleFile);
                        } else {
                            // Show detailed error with file paths for debugging
                            String errorMessage = "File not found on disk. Details:\n" +
                                    "Database filename: " + actualFileName + "\n" +
                                    "Searched path 1: " + file.getAbsolutePath() + "\n" +
                                    "Searched path 2: " + simpleFile.getAbsolutePath() + "\n" +
                                    "Storage directory exists: " + new File(FILE_STORAGE_PATH).exists() + "\n" +
                                    "Files in storage directory: " + listFilesInDirectory();

                            System.err.println(errorMessage);
                            JOptionPane.showMessageDialog(this, errorMessage);
                        }
                    }
                } else {
                    // Debug info about the query
                    System.out.println("No results found for query: " + query.replace("?", "'" + fileName + "'"));

                    // Show all files in the database for debugging
                    Statement debugStmt = conn.createStatement();
                    ResultSet debugRs = debugStmt.executeQuery("SELECT id, file_name FROM files");

                    StringBuilder fileList = new StringBuilder("File not found in database. Available files:\n");
                    boolean hasFiles = false;

                    while (debugRs.next()) {
                        hasFiles = true;
                        fileList.append("ID: ").append(debugRs.getInt("id"))
                                .append(", Name: ").append(debugRs.getString("file_name"))
                                .append("\n");
                    }

                    if (!hasFiles) {
                        fileList.append("No files in database at all!");
                    }

                    System.out.println(fileList.toString());
                    JOptionPane.showMessageDialog(this, fileList.toString());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error when checking file: " + e.getMessage());
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
                String[] parts = message.split(":", 4);
                if (parts.length == 4 && parts[0].equals("FILE")) {
                    String sender = parts[1];
                    String fileName = parts[2];
                    String filePath = parts[3];
                    chatArea.append(sender + " sent a file: " + fileName + " [Click to download]\n");
                    chatArea.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            downloadFile(filePath); // Tải file khi click
                        }
                    });
                } else if (parts.length == 3) {
                    if (parts[0].equals("MESSAGE")) {
                        String sender = parts[1];
                        String msg = parts[2];
                        chatArea.append(sender + ": " + msg + "\n");
                        chatHistory.get(sender).append(sender + ": ").append(msg).append("\n");
                    } else if (parts[1].equals("G")) { // Group message
                        String sender = parts[0];
                        String msg = parts[2];
                        chatArea.append(sender + " (Group): " + msg + "\n");
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength()); // Di chuyển con trỏ tới cuối
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
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
            String filePath = selectedFile.getAbsolutePath();
            String fileName = selectedFile.getName();

            // Gửi file đến server
            try {
                // Fix: Add proper message format with FILE:recipientName:fileName:filePath
                out.println("FILE:" + selectedItem + ":" + fileName + ":" + filePath);
                chatArea.append("You sent a file: " + fileName + "\n");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error sending file: " + e.getMessage());
            }
        }
    }

    private void downloadFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                Desktop.getDesktop().open(file); // Mở file tự động
            } else {
                JOptionPane.showMessageDialog(this, "File does not exist!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyFileWithProgress(File sourceFile, File destFile) {
        // Create progress dialog
        JDialog progressDialog = new JDialog(this, "Downloading File", true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JLabel statusLabel = new JLabel("Downloading: " + sourceFile.getName());
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        progressDialog.setContentPane(panel);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // Start copying in a background thread
        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[8192];
                long totalBytes = sourceFile.length();
                long bytesTransferred = 0;
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    bytesTransferred += bytesRead;

                    // Update progress
                    final int progress = (int)((bytesTransferred * 100) / totalBytes);
                    long finalBytesTransferred = bytesTransferred;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress);
                        progressBar.setString(progress + "%" + " (" + formatFileSize(finalBytesTransferred) +
                                " of " + formatFileSize(totalBytes) + ")");
                    });
                }

                // Done - close dialog and show success message
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(this,
                            "File downloaded successfully to:\n" + destFile.getAbsolutePath());
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(this,
                            "Error downloading file: " + e.getMessage(),
                            "Download Error", JOptionPane.ERROR_MESSAGE);
                });
                e.printStackTrace();
            }
        }).start();

        // Show dialog (will block until disposed)
        progressDialog.setVisible(true);
    }

    // Helper method to format file sizes in KB, MB, etc.
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private void downloadFileWithLocationSelection(String fileName) {
        System.out.println("Preparing to download file: " + fileName);

        // First check if the file exists in our record
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "")) {
            String query = "SELECT id FROM files WHERE file_name = ? ORDER BY created_at DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, fileName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    // File exists in database, let's try to find it on disk
                    File sourceFile = new File(FILE_STORAGE_PATH, fileName);

                    if (!sourceFile.exists()) {
                        JOptionPane.showMessageDialog(this,
                                "The source file was not found on the server.\n" +
                                        "Path checked: " + sourceFile.getAbsolutePath());
                        return;
                    }

                    // Create a file chooser for save location
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Save File As");
                    fileChooser.setSelectedFile(new File(fileName));

                    // Show save dialog
                    int userSelection = fileChooser.showSaveDialog(this);

                    if (userSelection == JFileChooser.APPROVE_OPTION) {
                        File destinationFile = fileChooser.getSelectedFile();

                        // If file exists, confirm overwrite
                        if (destinationFile.exists()) {
                            int response = JOptionPane.showConfirmDialog(this,
                                    "A file with this name already exists. Overwrite?",
                                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION);

                            if (response != JOptionPane.YES_OPTION) {
                                return; // User canceled overwrite
                            }
                        }

                        // Copy the file with progress indicator
                        copyFileWithProgress(sourceFile, destinationFile);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "File not found in the database records.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
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