package nodomain.freeyourgadget.gadgetbridge.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FCMNotificationSender {

    private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/PROJECT_ID/messages:send";

    /**
     * Sends a notification using FCM HTTP v1 API.
     *
     * @param accessToken  The access token for authentication.
     * @param deviceToken  The target device FCM token.
     * @param title        The notification title.
     * @param body         The notification body.
     * @throws Exception If an error occurs during the request.
     */
    public static void sendNotification(String accessToken, String deviceToken, String title, String body) throws Exception {
        URL url = new URL(FCM_URL.replace("PROJECT_ID", "smartschiz-6a1d3"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set headers
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // Prepare payload
        String payload = "{"
                + "  \"message\": {"
                + "    \"token\": \"" + deviceToken + "\","
                + "    \"notification\": {"
                + "      \"title\": \"" + title + "\","
                + "      \"body\": \"" + body + "\""
                + "    }"
                + "  }"
                + "}";

        // Write payload to the request body
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload.getBytes());
            outputStream.flush();
        }

        // Check the response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            System.out.println("Notification sent successfully.");
        } else {
            System.err.println("Failed to send notification. Response code: " + responseCode);
        }
    }
}
