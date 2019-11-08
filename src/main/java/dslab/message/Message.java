package dslab.message;

import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class Message {

    private ArrayList<String> recipients;
    private String sender;
    private String subject;
    private String content;

    public Message() {
    }

    public boolean isValidEmailAddress(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        Pattern pat = Pattern.compile(emailRegex);
        if (email == null)
            return false;
        return pat.matcher(email).matches();
    }

    public void setRecipients(ArrayList<String> recipients) {
        this.recipients = recipients;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ArrayList<String> getRecipients() {
        return recipients;
    }

    public int getNumberOfRecipients() {
        return recipients.size();
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public String recipientsString() {
        StringJoiner joiner = new StringJoiner(",");
        for (String recipient : recipients) {
            joiner.add(recipient);
        }
        return joiner.toString();
    }
}
