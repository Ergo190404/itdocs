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

                // Read user ID
                userId = Integer.parseInt(in.readLine());
                synchronized (clientWriters) {
                    clientWriters.put(userId, out);
                }

                String message;
                while ((message = in.readLine()) != null) {
                    String[] parts = message.split(":", 2);
                    if (parts.length == 2) {
                        String recipient = parts[0];
                        String msg = parts[1];

                        if (recipient.contains(":G:")) { // Group message
                            String[] groupParts = recipient.split(":G:");
                            String groupName = groupParts[0];
                            broadcastToGroup(groupName, userId, msg);
                        } else if (recipient.startsWith("FILE:")) { // File message
                            String[] fileParts = recipient.split(":", 4);
                            if (fileParts.length == 4) {
                                String fileRecipient = fileParts[1];
                                String fileName = fileParts[2];
                                String filePath = fileParts[3];
                                saveFileToServer(filePath, fileName);
                                sendFileToUser(fileRecipient, fileName); // Gửi file đến recipient sau khi lưu
                                saveFileInfoToDatabase(fileName, userId); // Lưu thông tin file vào CSDL
                            }
                        } else {
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

        private void sendFileToUser(String receiver, String fileName) {
            Integer receiverId = getUserIdByUsername(receiver);
            PrintWriter receiverWriter = clientWriters.get(receiverId);
            if (receiverWriter != null) {
                receiverWriter.println("FILE:" + UserSession.getUsername() + ":" + fileName + ":" + (FILE_STORAGE_PATH + "/" + fileName));
            }
        }

        private void saveFileToServer(String filePath, String fileName) {
            // Lưu file vào thư mục đã chỉ định
            try (InputStream fileInput = new FileInputStream(filePath);
                 OutputStream outFile = new FileOutputStream(new File(FILE_STORAGE_PATH, fileName))) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    outFile.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void saveFileInfoToDatabase(String fileName, int senderId) {
            String query = "INSERT INTO files (file_name, sender_id, created_at) VALUES (?, ?, NOW())";
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_system", "root", "");
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, fileName);
                stmt.setInt(2, senderId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
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