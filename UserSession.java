package Main;

public class UserSession {
    private static String username;
    private static int userId;

    public static void saveUserSession(String username, int userId) {
        UserSession.username = username;
        UserSession.userId = userId;
    }

    public static String getUsername() {
        return username;
    }

    public static int getUserId() {
        return userId;
    }

    public static void clearUserSession() {
        username = null;
        userId = -1;
    }
}