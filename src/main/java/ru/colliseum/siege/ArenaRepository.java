package ru.colliseum.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ArenaRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, ArenaData>>() {}.getType();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("colosseum_arenas.json");
    private final Map<String, ArenaData> arenas = new HashMap<>();

    public void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            Map<String, ArenaData> loaded = GSON.fromJson(Files.readString(file), TYPE);
            arenas.clear();
            if (loaded != null) arenas.putAll(loaded);
        } catch (Exception ignored) {
        }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(arenas, TYPE));
    }

    public Map<String, ArenaData> all() {
        return arenas;
    }

    public ArenaData get(String name) {
        return arenas.get(name.toLowerCase());
    }

    public void put(String name, ArenaData data) {
        data.name = name;
        arenas.put(name.toLowerCase(), data);
    }
}
