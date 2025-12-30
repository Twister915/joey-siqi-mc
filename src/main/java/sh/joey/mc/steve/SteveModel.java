package sh.joey.mc.steve;

import io.reactivex.rxjava3.core.Single;

/**
 * Interface for a Steve AI model that can answer questions.
 */
public interface SteveModel {

    /**
     * Asks the model a question and returns the answer.
     *
     * @param question the question to ask
     * @return Single emitting the answer
     */
    Single<SteveAnswer> ask(String question);

    /**
     * Returns metadata about this model.
     *
     * @return model metadata
     */
    SteveModelInfo info();
}
