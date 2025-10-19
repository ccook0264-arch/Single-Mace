package net.mattwhyy.onemace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class OneMaceConfig {
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("onemace.json");
    public boolean maceCrafted = false;
    public String maceOwner = null;
    public boolean announceMaceMessages = true;
    public boolean allowMaceInContainers = true;
    public boolean allowLocateForAll = false;
    public boolean coloredName = false;
    public String maceNameColor = "RED";
    public Map<String, Boolean> offlineInventory = new HashMap();
    public OneMaceConfig.Messages messages = new OneMaceConfig.Messages();
    public static OneMaceConfig INSTANCE = new OneMaceConfig();

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH, new LinkOption[0])) {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = (OneMaceConfig)GSON.fromJson(json, OneMaceConfig.class);
            } else {
                save();
            }
        } catch (IOException var1) {
            var1.printStackTrace();
        }

    }

    public static void save() {
        try {
            String json = GSON.toJson(INSTANCE);
            Files.writeString(CONFIG_PATH, json, new OpenOption[0]);
        } catch (IOException var1) {
            var1.printStackTrace();
        }

    }

    public static class Messages {
        public String crafted = "&b[OneMace] &eThe Mace has been crafted!";
        public String lost = "&b[OneMace] &eThe Mace has been lost!";
    }
}
