package nodomain.freeyourgadget.gadgetbridge.model;

public class ConnectedUser {
    private String documentId;
    private String username;
    private String email;

    // Constructor
    public ConnectedUser(String documentId, String username, String email) {
        this.documentId = documentId;
        this.username = username;
        this.email = email;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
}

