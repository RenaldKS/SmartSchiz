package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import nodomain.freeyourgadget.gadgetbridge.R;

public class FCMAccessTokenProvider {

    private static final String SCOPES = "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Generates an OAuth2 Access Token for FCM.
     *
     * @param context The context of the application.
     * @return The generated access token.
     * @throws IOException If an error occurs while reading the service account file.
     */
    public static String getAccessToken(Context context) throws IOException {
        // Accessing the service account file from the raw resources directory
        InputStream serviceAccountStream = context.getResources().openRawResource(R.raw.smartschizserviceaccount);

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(serviceAccountStream)
                .createScoped(Collections.singleton(SCOPES));

        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}
