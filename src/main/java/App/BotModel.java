package App;

import opennlp.tools.doccat.*;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import opennlp.tools.util.model.ModelUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class BotModel {
    private String inputText;
    private String outMessage;
    static Map<String, String> questionAnswer = new HashMap<>();

    public BotModel(String in) {
        this.inputText = in;

        questionAnswer.put("greeting", "Hello there, how can I help you?");
        questionAnswer.put("wedding-inquiry", "Congrats on the upcoming wedding!\n\n" +
                "Our wedding packages start at $500.00.\nWould you like to know more?");
        questionAnswer.put("fam-inquiry", "We would love to take your family photos for you." +
                " We typically do an hour long session starting at $100.00.\nWould you like to know more?");
        questionAnswer.put("senior-inquiry", "Congrats to the almost Grad!\n\nWe'd love to take your senior" +
                " photos for you.\nOur most popular senior package includes an hour shoot, and two locations." +
                "\nWould you like to know more?");
        questionAnswer.put("baby-inquiry", "We would love to take new born pictures for you!\n" +
                "We typically do an hour long session starting at $100.00");
        questionAnswer.put("couples-inquiry", "We would love to take a couple shoot for you!" +
                " The couples/engagement package starts at $150.00 per hour");
        questionAnswer.put("conversation-continue", "What else can I help you with?");
        questionAnswer.put("conversation-complete", "Nice chatting with you. Bbye.");

    }

    public void runModel() throws IOException {
        // Train categorizer model to the training data we created.
        DoccatModel model = trainCategorizerModel();

        // Break users chat input into sentences using sentence detection.
        String[] sentences = breakSentences(this.inputText);

        String answer = "";

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
            this.outMessage = answer;
        }
    }



    /**
     * Train categorizer model as per the category sample training data we created.
     *
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    static DoccatModel trainCategorizerModel() throws FileNotFoundException, IOException {
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

    /**
     * Detect category using given token. Use categorizer feature of Apache OpenNLP.
     *
     * @param model
     * @param finalTokens
     * @return
     * @throws IOException
     */
    static String detectCategory(DoccatModel model, String[] finalTokens) throws IOException {
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
    static String[] breakSentences(String data) throws FileNotFoundException, IOException {
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
    static String[] tokenizeSentence(String sentence) throws FileNotFoundException, IOException {
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
    static String[] detectPOSTags(String[] tokens) throws IOException {
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
    static String[] lemmatizeTokens(String[] tokens, String[] posTags)
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

    public String getOutMessage() {
        return this.outMessage;
    }
}
