package Main;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Map<Integer, PrintWriter> clientWriters = new HashMap<>();
    private static Map<Integer, Socket> clientSockets = new HashMap<>();
    private static final String FILE_STORAGE_PATH = "C:/Users/User/Desktop/Folder/ChatSystem_experiment/src/main/java/ChatFiles";

    public static void main(String[] args) {
        // Create directory if it doesn't exist
        File fileStorage = new File(FILE_STORAGE_PATH);
        if (!fileStorage.exists()) {
            fileStorage.mkdirs();
        }

        System.out.println("Chat server started on port " + PORT + " at " + new java.util.Date());
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                System.out.println("Waiting for client connection...");
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress().getHostAddress());
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private int userId;
        private String currentUsername;
        private boolean isFileTransferConnection = false;
        private boolean isFileDownloadConnection = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                socket.setSoTimeout(30000); // 30 seconds timeout
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read the first message to determine connection type
                String firstMessage = in.readLine();
                System.out.println("First message: " + firstMessage);

                // Handle file transfer connection
                if (firstMessage != null && firstMessage.startsWith("FILE_TRANSFER:")) {
                    isFileTransferConnection = true;
                    // Extract user ID
                    String userIdStr = firstMessage.substring("FILE_TRANSFER:".length());
                    try {
                        userId = Integer.parseInt(userIdStr);
                        System.out.println("File transfer connection established for user " + userId);
                        currentUsername = getUsernameById(userId);

                        // Handle file transfer and then exit
                        handleFileTransferConnection();
                        return;
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid user ID for file transfer: " + userIdStr);
                        return;
                    }
                }


                // Handle file download connection
                else if (firstMessage != null && firstMessage.startsWith("FILE_DOWNLOAD:")) {
                    isFileDownloadConnection = true;
                    // Extract user ID
                    String userIdStr = firstMessage.substring("FILE_DOWNLOAD:".length());
                    try {
                        userId = Integer.parseInt(userIdStr);
                        System.out.println("File download connection established for user " + userId);
                        currentUsername = getUsernameById(userId);

                        // Handle file download and then exit
                        handleFileDownloadConnection();
                        return;
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid user ID for file download: " + userIdStr);
                        return;
                    }
                }
                // Regular chat connection handling
                else if (firstMessage != null && !firstMessage.startsWith("FILE:") && !firstMessage.startsWith("FILE_BEGIN:")
                        && !firstMessage.contains(":G:") && !firstMessage.contains(":")) {
                    try {
                        userId = Integer.parseInt(firstMessage);
                        System.out.println("User connected: " + userId);

                        // Get username for this user
                        currentUsername = getUsernameById(userId);
                        System.out.println("Username: " + currentUsername);

                        synchronized (clientWriters) {
                            clientWriters.put(userId, out);
                        }

                        synchronized (clientSockets) {
                            clientSockets.put(userId, socket);
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

                // Continue handling regular chat messages
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received message: " + message);

                    if (message.startsWith("FILE:")) {
                        // Legacy file message handling (file path based)
                        handleFileMessage(message);
                    } else if (message.contains(":G:")) {
                        // Group message handling
                        String[] groupParts = message.split(":G:", 2);
                        if (groupParts.length == 2) {
                            String groupName = groupParts[0];
                            String msg = groupParts[1];
                            broadcastToGroup(groupName, userId, msg);
                        }
                    }
                    else if (message.startsWith("SIGN_OUT:")) {
                        // Extract user ID from the message
                        String userId = message.substring("SIGN_OUT:".length());
                        System.out.println("User " + userId + " (" + currentUsername + ") is signing out");

                        // Exit the loop to close this client's connection
                        break;
                        }
                    else {
                        // Regular message
                        String[] parts = message.split(":", 2);
                        if (parts.length == 2) {
                            String recipient = parts[0];
                            String msg = parts[1];
                            sendMessageToUser(recipient, msg);
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                if (isFileTransferConnection || isFileDownloadConnection) {
                    System.out.println("File transfer timed out");
                } else {
                    System.out.println("Client connection timed out for user " + userId);
                }
            } catch (IOException e) {
                if (e instanceof SocketException && e.getMessage().contains("Connection reset")) {
                    System.out.println("Client disconnected abruptly");
                } else {
                    System.err.println("Error handling client: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                try {
                    System.out.println("Closing connection for user " + userId);
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
                if (!isFileTransferConnection && !isFileDownloadConnection) {
                    synchronized (clientWriters) {
                        clientWriters.remove(userId);
                    }
                    synchronized (clientSockets) {
                        clientSockets.remove(userId);
                    }
                    System.out.println("Client handler for user " + userId + " terminated");
                }
            }
        }

        private void handleFileTransferConnection() {
            try {
                String fileInfo = in.readLine();

                if (fileInfo != null && fileInfo.startsWith("FILE_BEGIN:")) {
                    String[] parts = fileInfo.split(":", 4);
                    if (parts.length == 4) {
                        String recipient = parts[1];
                        String fileName = parts[2];
                        long fileSize = Long.parseLong(parts[3]);

                        System.out.println("File transfer started: " + fileName + " (" + fileSize + " bytes) for " + recipient);

                        // Create file in storage
                        File destFile = new File(FILE_STORAGE_PATH, fileName);
                        if (!destFile.getParentFile().exists()) {
                            destFile.getParentFile().mkdirs();
                        }

                        try (FileOutputStream fileOut = new FileOutputStream(destFile)) {
                            InputStream socketIn = socket.getInputStream();

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalReceived = 0;

                            while (totalReceived < fileSize &&
                                    (bytesRead = socketIn.read(buffer)) > 0) {
                                fileOut.write(buffer, 0, bytesRead);
                                totalReceived += bytesRead;

                                // Log progress occasionally
                                if (totalReceived % 81920 == 0 || totalReceived == fileSize) {
                                    System.out.println("Received " + totalReceived + "/" + fileSize + " bytes for " + fileName);
                                }
                            }

                            // Send confirmation
                            out.println("FILE_RECEIVED:" + fileName);

                            // Save file info to database
                            saveFileInfoToDatabase(fileName, userId, recipient);
                            storeFileMessageInChat(recipient, fileName);

                            // Notify recipients about the file
                            notifyRecipientsAboutFile(recipient, fileName, fileSize);

                            System.out.println("File transfer completed: " + fileName);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in file transfer: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleFileDownloadConnection() {
            try {
                String fileRequest = in.readLine();

                if (fileRequest != null && fileRequest.startsWith("FILE_REQUEST:")) {
                    String fileName = fileRequest.substring("FILE_REQUEST:".length());
                    File requestedFile = new File(FILE_STORAGE_PATH, fileName);

                    if (requestedFile.exists()) {
                        long fileSize = requestedFile.length();

                        // Send file info
                        out.println("FILE_INFO:" + fileName + ":" + fileSize);

                        // Send the file
                        try (FileInputStream fileIn = new FileInputStream(requestedFile)) {
                            OutputStream socketOut = socket.getOutputStream();

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalSent = 0;

                            while ((bytesRead = fileIn.read(buffer)) != -1) {
                                socketOut.write(buffer, 0, bytesRead);
                                totalSent += bytesRead;

                                // Log progress occasionally
                                if (totalSent % 81920 == 0) {
                                    System.out.println("Sent " + totalSent + "/" + fileSize + " bytes for " + fileName);
                                }
                            }

                            socketOut.flush();
                            System.out.println("File download completed: " + fileName);
                        }
                    } else {
                        out.println("FILE_NOT_FOUND");
                        System.out.println("File not found: " + fileName);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in file download: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void notifyRecipientsAboutFile(String recipient, String fileName, long fileSize) {
            if (isGroupName(recipient)) {
                // Group notification
                List<Integer> groupMembers = getGroupMembers(recipient);
                for (Integer memberId : groupMembers) {
                    if (memberId != userId) { // Don't notify self
                        PrintWriter memberWriter = clientWriters.get(memberId);
                        if (memberWriter != null) {
                            memberWriter.println("FILE_AVAILABLE:" + currentUsername + ":" + fileName + ":" + fileSize);
                        }
                    }
                }
            } else {
                // Direct notification
                int recipientId = getUserIdByUsername(recipient);
                PrintWriter recipientWriter = clientWriters.get(recipientId);
                if (recipientWriter != null) {
                    recipientWriter.println("FILE_AVAILABLE:" + currentUsername + ":" + fileName + ":" + fileSize);
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
                    System.err.println("Database error storing group file message: " + e.getMessage());
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
                    System.err.println("Database error storing direct file message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private void sendMessageToUser(String receiver, String message) {
            Integer receiverId = getUserIdByUsername(receiver);
            PrintWriter receiverWriter = clientWriters.get(receiverId);
            if (receiverWriter != null) {
                receiverWriter.println("MESSAGE:" + currentUsername + ":" + message);
                System.out.println("Message sent to " + receiver + ": " + message);
            } else {
                System.out.println("User " + receiver + " is offline, message stored in database");
            }
        }

        private void broadcastToGroup(String groupName, int senderId, String message) {
            List<Integer> groupMembers = getGroupMembers(groupName);
            System.out.println("Broadcasting to group " + groupName + " with " + groupMembers.size() + " members");

            for (Integer memberId : groupMembers) {
                PrintWriter memberWriter = clientWriters.get(memberId);
                if (memberWriter != null) {
                    memberWriter.println("MESSAGE:" + currentUsername + " (Group): " + message);
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
                receiverWriter.println("FILE:" + currentUsername + ":" + simpleFileName + ":" + serverFilePath);
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

        private String getUsernameById(int userId) {
            String username = null;
            String query = "SELECT username FROM users WHERE id = ?";
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    username = rs.getString("username");
                }
            } catch (SQLException e) {
                System.err.println("Database error getting username: " + e.getMessage());
                e.printStackTrace();
            }
            return username;
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
                System.err.println("Database error checking group name: " + e.getMessage());
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
                System.err.println("Database error getting group members: " + e.getMessage());
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
                System.err.println("Database error getting user ID: " + e.getMessage());
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
                System.err.println("Database error saving group message: " + e.getMessage());
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
                System.err.println("Database error getting group ID: " + e.getMessage());
                e.printStackTrace();
            }
            return groupId;
        }
    }
}
