package App;

import Gmail.GmailAccess;
import org.apache.commons.codec.binary.Base64;
import org.jsoup.nodes.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;
import java.time.ZoneOffset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@RestController
public class HelloWorld {
    public GmailAccess gmailAccess;

    @PostMapping(value = "/")
    public String display() throws GeneralSecurityException, IOException, ParseException, MessagingException, InterruptedException {

        gmailAccess = new GmailAccess("Test");
        List<Document> docs = gmailAccess.getMessages();

        if (docs == null || docs.isEmpty()) {
            System.out.println("No messages retrieved.");
            return "no messages retrieved";
        }
        this.respond(docs);
        return "thanks";
    }

    private void respond(List<Document> docs) throws MessagingException, IOException {
        boolean introMessage = false;
        String outMessage = "";

        List<String> introWords = new ArrayList<>
                (Arrays.asList("good", "just swell", "fabulous", "wonderful"));

        List<String> messages = new ArrayList<>(Arrays.asList(
                "This is a message", "This is message 2", "This is message 3"));

        String outro = "\n\nI hope you enjoyed his message. Respond to me at anytime and I will get back to you within 10 minutes\n" +
                "Thanks for interacting with SAMSBOT (Sam's Automated Messaging System Robot)\n" +
                "We will talk to you soon.\n" +
                "Have a lovely day,\n\n" +
                "SAMSBOT";

        List<String> introKeyWords = new ArrayList<>(
                Arrays.asList("hi","hey", "hello", "how are you", "how's it going"));

        // Check to see if greeting is in the message
        for (Document doc : docs) {
            String inMessage = doc.body().text().toLowerCase();
            for (String kw : introKeyWords) {
                if (inMessage.contains(kw)) {
                    introMessage = true;
                }
            }
            // build message
            ThreadLocalRandom tlr = ThreadLocalRandom.current();
            int randomNum1 = tlr.nextInt(0, introWords.size());
            int randomNum2 = tlr.nextInt(0, messages.size());

            outMessage += "Hello Test, I hope you are doing " + introWords.get(randomNum1) +
                    "\nHere is a message for you:\n\n" +
                    messages.get(randomNum2) + outro;

            // Create the email content in some convenient way and encode it as a base64url string
            MimeMessage email = this.createEmail(
                    "cummings.samuel007@gmail.com",
                    "cummings.samuel007@gmail.com",
                    "SAMSBOT",
                    outMessage);
            com.google.api.services.gmail.model.Message message = this.createMessageWithEmail(email);
            message = gmailAccess.service.users().messages().send("me", message).execute();
            System.out.println("message sent");
        }
        return;

    }
    public static MimeMessage createEmail(String to, String from, String subject, String bodyText)
                                throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    public static com.google.api.services.gmail.model.Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        com.google.api.services.gmail.model.Message message = new com.google.api.services.gmail.model.Message();
        message.setRaw(encodedEmail);
        return message;
    }


}
