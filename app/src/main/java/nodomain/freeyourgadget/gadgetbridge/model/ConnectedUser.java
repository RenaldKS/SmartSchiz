package nodomain.freeyourgadget.gadgetbridge.model;


public class ConnectedUser {
    private String userId;
    private String email;

    public ConnectedUser(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}

