package Main;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Map<Integer, PrintWriter> clientWriters = new HashMap<>();
    private static final String FILE_STORAGE_PATH = "C:/Users/User/Desktop/Folder/ChatSystem_experiment/src/main/java/ChatFiles"; // Directory to store files

    public static void main(String[] args) {
        // Create directory if it doesn't exist
        File fileStorage = new File(FILE_STORAGE_PATH);
        if (!fileStorage.exists()) {
            fileStorage.mkdirs();
        }

        System.out.println("Chat server started on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
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
        private boolean isDataChannel = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read the first message to determine connection type
                String firstMessage = in.readLine();

                if (firstMessage != null && firstMessage.startsWith("DATA_CHANNEL:")) {
                    // This is a data channel for file transfer
                    isDataChannel = true;
                    userId = Integer.parseInt(firstMessage.substring(13));
                    System.out.println("Data channel opened for user: " + userId);
                    handleDataChannel();
                } else if (firstMessage != null && !firstMessage.startsWith("FILE:") &&
                        !firstMessage.contains(":G:") && !firstMessage.contains(":")) {
                    // Regular connection with user ID
                    try {
                        userId = Integer.parseInt(firstMessage);
                        System.out.println("User connected: " + userId);

                        synchronized (clientWriters) {
                            clientWriters.put(userId, out);
                        }

                        // Handle regular message loop
                        handleRegularMessages();
                    } catch (NumberFormatException e) {
                        System.err.println("Expected user ID but received: " + firstMessage);
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("Invalid first message: " + firstMessage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (!isDataChannel) {
                    synchronized (clientWriters) {
                        clientWriters.remove(userId);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleDataChannel() throws IOException {
            String command = in.readLine();
            System.out.println("Data channel command: " + command);

            if (command.startsWith("FILE_UPLOAD_START:")) {
                // Handle file upload
                String[] parts = command.split(":", 4);
                if (parts.length == 4) {
                    String recipient = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);

                    handleFileUpload(recipient, fileName, fileSize);
                }
            } else if (command.startsWith("FILE_DOWNLOAD_REQUEST:")) {
                // Handle file download
                String fileName = command.substring(21);
                handleFileDownload(fileName);
            }
        }

        private void handleFileUpload(String recipient, String fileName, long fileSize) throws IOException {
            System.out.println("Starting file upload: " + fileName + " (" + fileSize + " bytes) for " + recipient);

            // Create unique file name to prevent overwrites
            String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
            File destFile = new File(FILE_STORAGE_PATH, uniqueFileName);

            // Tell client we're ready
            out.println("READY_FOR_FILE");

            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                // Read the file data from socket
                InputStream socketIn = socket.getInputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;

                // Read until we get the whole file
                while (totalBytesRead < fileSize && (bytesRead = socketIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    // Print progress for debugging
                    if (totalBytesRead % (1024 * 1024) == 0) { // Every 1MB
                        System.out.println("Received " + (totalBytesRead / (1024 * 1024)) + "MB");
                    }
                }

                System.out.println("File received successfully: " + destFile.getAbsolutePath());

                // Save file info to database
                saveFileInfoToDatabase(uniqueFileName, userId, recipient);

                // Store file message in messages or group_messages table
                storeFileMessageInChat(recipient, fileName);

                // Send file notification to recipient
                sendFileNotificationToUser(recipient, fileName, uniqueFileName);

                // Confirmation to sender
                out.println("FILE_UPLOAD_SUCCESS");

            } catch (IOException e) {
                System.err.println("Error receiving file: " + e.getMessage());
                out.println("ERROR: File upload failed: " + e.getMessage());
                throw e;
            }
        }

        private void handleFileDownload(String fileName) {
            System.out.println("File download request for: " + fileName);

            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "")) {
                // Find the file in the database
                String query = "SELECT file_name FROM files WHERE file_name LIKE ? ORDER BY created_at DESC LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, "%" + fileName + "%");
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String actualFileName = rs.getString("file_name");
                        File file = new File(FILE_STORAGE_PATH, actualFileName);

                        if (file.exists()) {
                            // Send file size
                            out.println("FILE_SIZE:" + file.length());
                            System.out.println("Sending file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");

                            // Then send file data
                            try (FileInputStream fis = new FileInputStream(file)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                long totalBytesSent = 0;

                                while ((bytesRead = fis.read(buffer)) != -1) {
                                    socket.getOutputStream().write(buffer, 0, bytesRead);
                                    totalBytesSent += bytesRead;

                                    if (totalBytesSent % (1024 * 1024) == 0) {
                                        System.out.println("Sent " + (totalBytesSent / (1024 * 1024)) + "MB");
                                    }
                                }

                                socket.getOutputStream().flush();
                                System.out.println("File sent completely: " + totalBytesSent + " bytes");
                            }
                        } else {
                            System.err.println("File not found on disk: " + file.getAbsolutePath());
                            out.println("ERROR:File not found on server disk");
                        }
                    } else {
                        System.err.println("File not found in database: " + fileName);
                        out.println("ERROR:File not found in database");
                    }
                }
            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
                out.println("ERROR:Database error: " + e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("IO error during file transfer: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleRegularMessages() throws IOException {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("FILE_TRANSFER_START:")) {
                    // Legacy file transfer protocol - for backward compatibility
                    handleFileMessage(message);
                } else if (message.startsWith("FILE:")) {
                    // Legacy file message handling
                    handleFileMessage(message);
                } else if (message.contains(":G:")) {
                    // Group message handling
                    String[] groupParts = message.split(":G:", 2);
                    if (groupParts.length == 2) {
                        String groupName = groupParts[0];
                        String msg = groupParts[1];
                        broadcastToGroup(groupName, userId, msg);
                    }
                } else if (message.contains(":")) {
                    // Regular message
                    String[] parts = message.split(":", 2);
                    if (parts.length == 2) {
                        String recipient = parts[0];
                        String msg = parts[1];
                        sendMessageToUser(recipient, msg);
                    }
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
                    sendFileNotificationToUser(fileRecipient, fileName, serverFilePath);

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
                String senderUsername = getUsernameById(userId);
                receiverWriter.println("MESSAGE:" + senderUsername + ":" + message);
            }
        }

        private void broadcastToGroup(String groupName, int senderId, String message) {
            List<Integer> groupMembers = getGroupMembers(groupName);
            String senderUsername = getUsernameById(senderId);

            for (Integer memberId : groupMembers) {
                if (memberId != senderId) { // Don't send back to the original sender
                    PrintWriter memberWriter = clientWriters.get(memberId);
                    if (memberWriter != null) {
                        memberWriter.println("MESSAGE:" + senderUsername + " (Group): " + message);
                    }
                }
            }
            saveGroupMessageToDatabase(groupName, senderId, message);
        }

        private void sendFileNotificationToUser(String recipient, String fileName, String serverFileName) {
            if (isGroupName(recipient)) {
                // It's a group, send notification to all members
                List<Integer> groupMembers = getGroupMembers(recipient);
                String senderUsername = getUsernameById(userId);

                for (Integer memberId : groupMembers) {
                    if (memberId != userId) { // Don't notify the sender
                        PrintWriter memberWriter = clientWriters.get(memberId);
                        if (memberWriter != null) {
                            memberWriter.println("FILE_NOTIFICATION:" + senderUsername + ":" + fileName + ":" + serverFileName);
                        }
                    }
                }
            } else {
                // It's a direct message
                Integer recipientId = getUserIdByUsername(recipient);
                PrintWriter recipientWriter = clientWriters.get(recipientId);

                if (recipientWriter != null) {
                    String senderUsername = getUsernameById(userId);
                    recipientWriter.println("FILE_NOTIFICATION:" + senderUsername + ":" + fileName + ":" + serverFileName);
                    System.out.println("File notification sent to " + recipient);
                } else {
                    System.out.println("Recipient not online: " + recipient + " (notification stored in database)");
                }
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

            // Create unique file name with timestamp
            String uniqueFileName = System.currentTimeMillis() + "_" + simpleFileName;
            File destFile = new File(storageDir, uniqueFileName);

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

                // Return the unique filename used on server
                return uniqueFileName;
            } catch (IOException e) {
                System.err.println("Error saving file " + fileName + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        private void saveFileInfoToDatabase(String fileName, int senderId, String recipientName) {
            System.out.println("Saving file to database: " + fileName);

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

                    stmt.setString(1, fileName);
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

        private String getUsernameById(int id) {
            String username = null;
            String query = "SELECT username FROM users WHERE id = ?";
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, id);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    username = rs.getString("username");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return username;
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