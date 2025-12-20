package sh.joey.mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Global Gson configuration for the plugin.
 * Use this instead of creating new Gson instances.
 */
public final class Json {

    public static final Gson GSON = new GsonBuilder()
            .create();

    private Json() {}
}
