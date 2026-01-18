package sh.joey.mc.steve;

import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * Interface for a Steve AI model that can answer questions.
 */
public interface SteveModel {

    /**
     * A previous conversation turn (question and answer pair).
     */
    record ConversationTurn(String question, String answer) {}

    /**
     * Asks the model a question and returns the answer.
     *
     * @param question the question to ask
     * @return Single emitting the answer
     */
    Single<SteveAnswer> ask(String question);

    /**
     * Asks the model a question with conversation history for context.
     * Implementations should include the history as prior turns in the conversation.
     *
     * @param question the current question to ask
     * @param history previous Q&A turns to include as context (oldest first)
     * @return Single emitting the answer
     */
    default Single<SteveAnswer> ask(String question, List<ConversationTurn> history) {
        // Default implementation ignores history for backwards compatibility
        return ask(question);
    }

    /**
     * Returns metadata about this model.
     *
     * @return model metadata
     */
    SteveModelInfo info();
}
