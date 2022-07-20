package App;

import com.google.api.client.util.ArrayMap;
import opennlp.tools.doccat.DoccatModel;

import java.io.IOException;
import java.util.Scanner;

public class DebugModel extends BotModel {
    public DebugModel(String in) {
        super(in);
    }

    public void runModel() throws IOException {
        // Train categorizer model to the training data we created.
        DoccatModel model = trainCategorizerModel();

        // Take chat inputs from console (user) in a loop.
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Get chat input from user.
            System.out.println("##### You:");
            String userInput = scanner.nextLine();

            // Break users chat input into sentences using sentence detection.
            String[] sentences = breakSentences(userInput);

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
                break;
            }

        }
    }
}
