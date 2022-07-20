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
        String inputText = gmailAccess.getMessage();

        if (inputText.equals("") || inputText.isEmpty()) {
            System.out.println("No messages retrieved.");
            return "no messages retrieved";
        }
        this.respond(inputText);
        return "thanks";
    }

    private void respond(String inputText) throws MessagingException, IOException {
        // Create model
        BotModel model = new BotModel(inputText);

        // Run model and get out message
        model.runModel();

        String outText = model.getOutMessage();
        // Create the email content in some convenient way and encode it as a base64url string
        MimeMessage email = this.createEmail(
                "cummings.samuel007@gmail.com",
                "cummings.samuel007@gmail.com",
                "SAMSBOT",
                outText);
        com.google.api.services.gmail.model.Message message = this.createMessageWithEmail(email);
        message = gmailAccess.service.users().messages().send("me", message).execute();
        System.out.println("message '" + outText + "' sent to " + "cummings.samuel007@gmail.com");

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
