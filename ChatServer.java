package Main;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Map<Integer, PrintWriter> clientWriters = new HashMap<>();
    private static final String FILE_STORAGE_PATH = "C:/Users/User/Desktop/Folder/ChatSystem_experiment/src/main/java/ChatFiles"; // Đường dẫn lưu file

    public static void main(String[] args) {
        // Tạo thư mục nếu chưa tồn tại
        File fileStorage = new File(FILE_STORAGE_PATH);
        if (!fileStorage.exists()) {
            fileStorage.mkdirs();
        }

        System.out.println("Chat server started...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private int userId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read the first message - it should be the user ID
                String firstMessage = in.readLine();
                if (firstMessage != null && !firstMessage.startsWith("FILE:") && !firstMessage.contains(":G:") && !firstMessage.contains(":")) {
                    try {
                        userId = Integer.parseInt(firstMessage);
                        System.out.println("User connected: " + userId);

                        synchronized (clientWriters) {
                            clientWriters.put(userId, out);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Expected user ID but received: " + firstMessage);
                        e.printStackTrace();
                        return; // Exit the thread if we can't get a valid user ID
                    }
                } else {
                    System.err.println("First message was not a user ID: " + firstMessage);
                    return; // Exit the thread
                }

                // Continue handling messages
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("FILE:")) {
                        // File message handling
                        handleFileMessage(message);
                    } else if (message.contains(":G:")) {
                        // Group message handling
                        String[] groupParts = message.split(":G:", 2);
                        if (groupParts.length == 2) {
                            String groupName = groupParts[0];
                            String msg = groupParts[1];
                            broadcastToGroup(groupName, userId, msg);
                        }
                    } else {
                        // Regular message
                        String[] parts = message.split(":", 2);
                        if (parts.length == 2) {
                            String recipient = parts[0];
                            String msg = parts[1];
                            sendMessageToUser(recipient, msg);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (clientWriters) {
                    clientWriters.remove(userId);
                }
            }
        }

        private void handleFileMessage(String message) {
            // File message format: FILE:recipientName:fileName:filePath
            String[] fileParts = message.split(":", 4);
            if (fileParts.length == 4) {
                String fileRecipient = fileParts[1];
                String fileName = fileParts[2];
                String filePath = fileParts[3];

                String serverFilePath = saveFileToServer(filePath, fileName);
                if (serverFilePath != null) {
                    // Save file info to database
                    saveFileInfoToDatabase(fileName, userId, fileRecipient);

                    // Store file message in messages or group_messages table too
                    storeFileMessageInChat(fileRecipient, fileName);

                    // Send file notification to recipient
                    sendFileToUser(fileRecipient, fileName, serverFilePath);

                    // Confirmation to sender
                    out.println("FILE_SENT:SUCCESS:" + fileName);
                } else {
                    out.println("FILE_SENT:FAILED:" + fileName);
                }
            }
        }

        private void storeFileMessageInChat(String recipient, String fileName) {
            if (isGroupName(recipient)) {
                // It's a group - save to group_messages
                int groupId = getGroupIdByName(recipient);
                String content = "[FILE] " + fileName; // Special marker for file messages

                String query = "INSERT INTO group_messages (group_id, sender_id, content, created_at) VALUES (?, ?, ?, NOW())";
                try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                     PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, groupId);
                    stmt.setInt(2, userId);
                    stmt.setString(3, content);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                // It's a direct message - save to messages
                int recipientId = getUserIdByUsername(recipient);
                String content = "[FILE] " + fileName; // Special marker for file messages

                String query = "INSERT INTO messages (sender_id, receiver_id, content, is_read, created_at) VALUES (?, ?, ?, 0, NOW())";
                try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                     PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, recipientId);
                    stmt.setString(3, content);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }


        private void sendMessageToUser(String receiver, String message) {
            Integer receiverId = getUserIdByUsername(receiver);
            PrintWriter receiverWriter = clientWriters.get(receiverId);
            if (receiverWriter != null) {
                receiverWriter.println("MESSAGE:" + UserSession.getUsername() + ":" + message);
            }
        }

        private void broadcastToGroup(String groupName, int senderId, String message) {
            List<Integer> groupMembers = getGroupMembers(groupName);
            for (Integer memberId : groupMembers) {
                PrintWriter memberWriter = clientWriters.get(memberId);
                if (memberWriter != null) {
                    memberWriter.println("MESSAGE:" + UserSession.getUsername() + " (Group): " + message);
                }
            }
            saveGroupMessageToDatabase(groupName, senderId, message);
        }

        private void sendFileToUser(String receiver, String fileName, String serverFilePath) {
            // Extract just the filename without path for notification
            String simpleFileName = fileName;
            if (simpleFileName.contains("\\")) {
                simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("\\") + 1);
            } else if (simpleFileName.contains("/")) {
                simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("/") + 1);
            }

            Integer receiverId = getUserIdByUsername(receiver);
            PrintWriter receiverWriter = clientWriters.get(receiverId);
            if (receiverWriter != null) {
                // Send just the filename, not the path
                receiverWriter.println("FILE:" + UserSession.getUsername() + ":" + simpleFileName + ":" + serverFilePath);
                System.out.println("File notification sent to " + receiver + " for file " + simpleFileName);
            } else {
                System.out.println("Recipient not online: " + receiver);
            }
        }

        private String saveFileToServer(String filePath, String fileName) {
            // Ensure the storage directory exists
            File storageDir = new File(FILE_STORAGE_PATH);
            if (!storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    System.err.println("Failed to create storage directory: " + FILE_STORAGE_PATH);
                    return null;
                }
            }

            // Make sure we just have the filename without path
            String simpleFileName = fileName;
            if (simpleFileName.contains("\\")) {
                simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("\\") + 1);
            } else if (simpleFileName.contains("/")) {
                simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("/") + 1);
            }

            File destFile = new File(storageDir, simpleFileName);

            try (InputStream fileInput = new FileInputStream(filePath);
                 OutputStream outFile = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                long total = 0;

                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    outFile.write(buffer, 0, bytesRead);
                    total += bytesRead;
                }

                System.out.println("File saved successfully: " + destFile.getAbsolutePath() +
                        " (" + total + " bytes)");

                // Return just the simple file name, not the full path
                return simpleFileName;
            } catch (IOException e) {
                System.err.println("Error saving file " + fileName + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        private void saveFileInfoToDatabase(String fileName, int senderId, String recipientName) {
            // Extract just the filename without path
            String simpleFileName = fileName;
            if (simpleFileName.contains("\\")) {
                simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("\\") + 1);
            } else if (simpleFileName.contains("/")) {
                simpleFileName = simpleFileName.substring(simpleFileName.lastIndexOf("/") + 1);
            }

            System.out.println("Saving file to database: " + simpleFileName);

            try {
                // Get recipient id
                Integer recipientId = null;
                boolean isGroup = false;

                if (isGroupName(recipientName)) {
                    isGroup = true;
                    recipientId = getGroupIdByName(recipientName);
                } else {
                    recipientId = getUserIdByUsername(recipientName);
                }

                if (recipientId == null) {
                    System.err.println("Recipient not found: " + recipientName);
                    return;
                }

                String query = "INSERT INTO files (file_name, sender_id, receiver_id, is_group, created_at) VALUES (?, ?, ?, ?, NOW())";
                try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                     PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

                    stmt.setString(1, simpleFileName);
                    stmt.setInt(2, senderId);
                    stmt.setInt(3, recipientId);
                    stmt.setBoolean(4, isGroup);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        ResultSet generatedKeys = stmt.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            int fileId = generatedKeys.getInt(1);
                            System.out.println("File saved in database with ID " + fileId);
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Database error saving file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // Helper method to check if a name is a group
        private boolean isGroupName(String name) {
            String query = "SELECT COUNT(*) FROM group_chats WHERE group_name = ?";
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        private List<Integer> getGroupMembers(String groupName) {
            List<Integer> memberIds = new ArrayList<>();
            String query = "SELECT user_id FROM group_members gm JOIN group_chats gc ON gm.group_id = gc.id WHERE gc.group_name = ?";
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, groupName);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    memberIds.add(rs.getInt("user_id"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return memberIds;
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
    }
}