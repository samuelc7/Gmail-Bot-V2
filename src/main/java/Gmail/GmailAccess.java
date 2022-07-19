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


    public List<Document> getMessages() throws IOException, ParseException, InterruptedException {
        ListMessagesResponse messageResponse = service.users().messages().list(user)
                .setLabelIds(this.labelIds)
                .execute();
        List<Message> messages = messageResponse.getMessages();

        List<Document> docs = new ArrayList<>();
        // Iterate through messages and find those sent most recently from passed in email
        if (messages == null) {
            return null;
        }
        for (Message message : messages) {
            message = service.users().messages().get("me", message.getId()).execute();
            if (message != null) {
                SimpleDateFormat messageFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                messageFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                SimpleDateFormat systemFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

                Date systemDate = new Date(System.currentTimeMillis());
                LocalDateTime sDate = LocalDateTime.parse(systemFormat.format(systemDate));
                Date messageDate = new Date(message.getInternalDate());
                LocalDateTime mDate = LocalDateTime.parse(messageFormat.format(messageDate));

                LocalDateTime lowerBound = sDate.minusMinutes(10L);

                // Only check the messages that were sent 10 mins before runtime
                if (mDate.isAfter(lowerBound)) {
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

                            trainCategorizerModel();
                            String inputMessage = decodedString;
                            controller(inputMessage);


                            Document doc = Jsoup.parse(decodedString);
                            docs.add(doc);
                        }
                    }
                }
            }
        }
        return docs;
    }
    /**
     * Train categorizer model as per the category sample training data we created.
     *
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static DoccatModel trainCategorizerModel() throws FileNotFoundException, IOException {
        // faq-categorizer.txt is a custom training data with categories as per our chat
        // requirements.
        InputStreamFactory inputStreamFactory = new MarkableFileInputStreamFactory(new File("src/main/java/App/faq-categorizer.txt"));
        ObjectStream<String> lineStream = new PlainTextByLineStream(inputStreamFactory, StandardCharsets.UTF_8);
        ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

        DoccatFactory factory = new DoccatFactory(new FeatureGenerator[] { new BagOfWordsFeatureGenerator() });

        TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, 0);

        // Train a model with classifications from above file.
        DoccatModel model = DocumentCategorizerME.train("en", sampleStream, params, factory);
        return model;
    }

    private static Map<String, String> questionAnswer = new HashMap<>();

    /*
     * Define answers for each given category.
     */
    static {
        //questionAnswer.put("greeting", "Hello there, how can I help you?");
        questionAnswer.put("wedding", "Congrats on the upcoming wedding!\n\nOur wedding packages start at $500.00.\nWould you like to know more?");
        questionAnswer.put("family", "We would love to take your family photos for you. We typically do an hour long session starting at $100.00.\nWould you like to know more?");
        questionAnswer.put("senior", "Congrats to the almost Grad!\n\nWe'd love to take your senior photos for you.\nOur most popular senior package includes an hour shoot, and two locations.\nWould you like to know more?");
        questionAnswer.put("conversation-continue", "What else can I help you with?");
        questionAnswer.put("conversation-complete", "Nice chatting with you. Bbye.");
    }

    public static void controller(String inputMessage) throws FileNotFoundException, IOException, InterruptedException {

        // Train categorizer model to the training data we created.
        DoccatModel model = trainCategorizerModel();

        // Break users chat input into sentences using sentence detection.
        String[] sentences = breakSentences(inputMessage);

        String answer = "";
        boolean conversationComplete = false;

        // Loop through sentences.
        for (String sentence : sentences) {

            // Separate words from each sentence using tokenizer.
            String[] tokens = tokenizeSentence(sentence);

            // Tag separated words with POS tags to understand their gramatical structure.
            String[] posTags = detectPOSTags(tokens);

            // Lemmatize each word so that its easy to categorize.
            String[] lemmas = lemmatizeTokens(tokens, posTags);

            // Determine BEST category using lemmatized tokens used a mode that we trained
            // at start.
            String category = detectCategory(model, lemmas);

            // Get predefined answer from given category & add to answer.
            answer = answer + " " + questionAnswer.get(category);

            // If category conversation-complete, we will end chat conversation.
            if ("conversation-complete".equals(category)) {
                conversationComplete = true;
            }
        }

        // Print answer back to user. If conversation is marked as complete, then end
        // loop & program.
        System.out.println("##### Chat Bot: " + answer);
        if (conversationComplete) {
            return;
        }
    }
    /**
     * Detect category using given token. Use categorizer feature of Apache OpenNLP.
     *
     * @param model
     * @param finalTokens
     * @return
     * @throws IOException
     */
    private static String detectCategory(DoccatModel model, String[] finalTokens) throws IOException {
        // Initialize document categorizer tool
        DocumentCategorizerME myCategorizer = new DocumentCategorizerME(model);

        // Get best possible category.
        double[] probabilitiesOfOutcomes = myCategorizer.categorize(finalTokens);
        String category = myCategorizer.getBestCategory(probabilitiesOfOutcomes);
        System.out.println("Category: " + category);

        return category;
    }

    /**
     * Break data into sentences using sentence detection feature of Apache OpenNLP.
     *
     * @param data
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static String[] breakSentences(String data) throws FileNotFoundException, IOException {
        // Better to read file once at start of program & store model in instance
        // variable. but keeping here for simplicity in understanding.
        InputStream inputSteam = new FileInputStream("src/main/java/App/en-sent.bin");
        SentenceModel model = new SentenceModel(inputSteam);
        SentenceDetectorME detector = new SentenceDetectorME(model);
        String sentences[] = detector.sentDetect(data);
        return sentences;
    }

    /**
     * Break sentence into words & punctuation marks using tokenizer feature of
     * Apache OpenNLP.
     *
     * @param sentence
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static String[] tokenizeSentence(String sentence) throws FileNotFoundException, IOException {
        // Better to read file once at start of program & store model in instance
        // variable. but keeping here for simplicity in understanding.
        try (InputStream modelIn = new FileInputStream("src/main/java/App/en-token.bin")) {
            // Initialize tokenizer tool
            TokenizerME myCategorizer = new TokenizerME(new TokenizerModel(modelIn));

            // Tokenize sentence.
            String[] tokens = myCategorizer.tokenize(sentence);
            System.out.println("Tokenizer : " + Arrays.stream(tokens).collect(Collectors.joining(" | ")));

            return tokens;
        }
    }

    /**
     * Find part-of-speech or POS tags of all tokens using POS tagger feature of
     * Apache OpenNLP.
     *
     * @param tokens
     * @return
     * @throws IOException
     */
    private static String[] detectPOSTags(String[] tokens) throws IOException {
        // Better to read file once at start of program & store model in instance
        // variable. but keeping here for simplicity in understanding.
        try (InputStream modelIn = new FileInputStream("src/main/java/App/en-pos-maxent.bin")) {
            // Initialize POS tagger tool
            POSTaggerME myCategorizer = new POSTaggerME(new POSModel(modelIn));

            // Tag sentence.
            String[] posTokens = myCategorizer.tag(tokens);
            System.out.println("POS Tags : " + Arrays.stream(posTokens).collect(Collectors.joining(" | ")));

            return posTokens;
        }
    }

    /**
     * Find lemma of tokens using lemmatizer feature of Apache OpenNLP.
     *
     * @param tokens
     * @param posTags
     * @return
     * @throws InvalidFormatException
     * @throws IOException
     */
    private static String[] lemmatizeTokens(String[] tokens, String[] posTags)
            throws InvalidFormatException, IOException {
        // Better to read file once at start of program & store model in instance
        // variable. but keeping here for simplicity in understanding.
        try (InputStream modelIn = new FileInputStream("src/main/java/App/en-lemmatizer.dict")) {

            // Tag sentence.
            DictionaryLemmatizer lemmatizer = new DictionaryLemmatizer(modelIn);
            String[] lemmaTokens = lemmatizer.lemmatize(tokens, posTags);
            System.out.println("Lemmatizer : " + Arrays.stream(lemmaTokens).collect(Collectors.joining(" | ")));

            return lemmaTokens;
        }
    }
}
