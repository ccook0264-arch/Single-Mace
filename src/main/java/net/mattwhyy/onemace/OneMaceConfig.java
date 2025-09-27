package net.mattwhyy.onemace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class OneMaceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("onemace.json");

    public boolean maceCrafted = false;
    public String maceOwner = null;
    public boolean announceMaceMessages = true;
    public boolean allowMaceInContainers = true;
    public boolean allowLocateForAll = false;
    public boolean coloredName = false;
    public String maceNameColor = "RED";

    public Map<String, Boolean> offlineInventory = new HashMap<>();

    public Messages messages = new Messages();

    public static class Messages {
        public String crafted = "&b[OneMace] &eThe Mace has been crafted!";
        public String lost = "&b[OneMace] &eThe Mace has been lost!";
    }

    // Current instance
    public static OneMaceConfig INSTANCE = new OneMaceConfig();

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, OneMaceConfig.class);
            } else {
                save(); // write defaults
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            String json = GSON.toJson(INSTANCE);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
