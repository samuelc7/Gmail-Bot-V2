package Gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import opennlp.tools.doccat.*;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import opennlp.tools.util.model.ModelUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.print.Doc;

public class GmailAccess {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CREDENTIALS_FILE_PATH = "/credential.json";
    public static Gmail service = null;
    private static final String user = "me";
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    private List<String> labelIds = new ArrayList<>();

    public GmailAccess(String labelName) throws GeneralSecurityException, IOException {
        service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName("Gmail-Bot")
                .build();
        getLabels(labelName);
    }

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = GmailAccess.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        clientSecrets.getDetails().setClientId(System.getenv("client_id"));
        clientSecrets.getDetails().setClientSecret(
                System.getenv("client_secret")
        );


        // Set the scopes
        List<String> SCOPES = ImmutableList.of(
                GmailScopes.GMAIL_LABELS,
                GmailScopes.GMAIL_READONLY,
                GmailScopes.MAIL_GOOGLE_COM);

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        //returns an authorized Credential object.
        return credential;
    }
    private void getLabels(String labelName) throws IOException {
        ListLabelsResponse listResponse = service.users().labels().list(user).execute();
        List<Label> labels = listResponse.getLabels();
        if (labels.isEmpty()) {
            System.out.println("No labels found.");
        } else {
            for (Label label : labels) {
                if (label.getName().equals(labelName)) {
                    labelIds.add(label.getId());
                }
            }
        }
    }


    public String getMessage() throws IOException, ParseException, InterruptedException {
        ListMessagesResponse messageResponse = service.users().messages().list(user)
                .setLabelIds(this.labelIds)
                .execute();
        List<Message> messages = messageResponse.getMessages();

        List<Document> docs = new ArrayList<>();
        if (messages == null) {
            return null;
        }
        // Get just the most recent message
        Message message = service.users().messages().get("me", messages.get(0).getId()).execute();
        List<MessagePart> messageParts = message.getPayload().getParts();
        String encoded = "";

        if (messageParts != null) {
            if (messageParts.size() > 1) {
                encoded = messageParts.get(1).getBody().getData();
            } else {
                try {
                    encoded = messageParts.get(0).getParts().get(0).getBody().getData();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
            if (encoded != null) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(encoded);
                String decodedString = new String(decodedBytes);
                return decodedString;
            }
        }
        return "";
    }












}
