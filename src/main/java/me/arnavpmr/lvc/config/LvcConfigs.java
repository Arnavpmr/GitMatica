package me.arnavpmr.lvc.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.arnavpmr.lvc.LvcDiagnostics;
import me.arnavpmr.lvc.LvcReference;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public final class LvcConfigs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = LvcReference.MOD_ID + ".json";
    private static final String MANUAL_ORIGIN_VISIBILITY_KEY = "ManualOriginVisibility";
    private static final Map<String, Boolean> MANUAL_ORIGIN_VISIBILITY =
            new HashMap<>();
    private static volatile boolean debugLoggingEnabled;

    public static final class Generic
    {
        private static final String GENERIC_KEY = LvcReference.MOD_ID + ".config.generic";

        public static final ConfigBoolean DEBUG_LOGGING =
                new ConfigBoolean("debugLogging", false).apply(GENERIC_KEY);
        public static final ConfigBoolean SHOW_CHECKOUT_WARNING =
                new ConfigBoolean("showCheckoutWarning", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SHOW_CLEAR_AREA_WARNING =
                new ConfigBoolean("showClearAreaWarning", true).apply(GENERIC_KEY);
        public static final ConfigBoolean SHOW_DISCARD_CHANGES_WARNING =
                new ConfigBoolean("showDiscardChangesWarning", true).apply(GENERIC_KEY);

        public static final List<IConfigBase> OPTIONS = List.of(
                DEBUG_LOGGING,
                SHOW_CHECKOUT_WARNING,
                SHOW_CLEAR_AREA_WARNING,
                SHOW_DISCARD_CHANGES_WARNING
        );

        private Generic()
        {
        }
    }

    public static void init()
    {
        updateDebugLogging();
        Generic.DEBUG_LOGGING.setValueChangeCallback(config -> updateDebugLogging());
    }

    public static boolean isDebugLoggingEnabled()
    {
        return debugLoggingEnabled;
    }

    public static synchronized boolean isManualOriginVisible(
            Path repositoryDirectory)
    {
        return MANUAL_ORIGIN_VISIBILITY.getOrDefault(
                projectKey(repositoryDirectory), false);
    }

    public static void setManualOriginVisible(
            Path repositoryDirectory,
            boolean visible)
    {
        synchronized (LvcConfigs.class)
        {
            String key = projectKey(repositoryDirectory);

            if (visible)
            {
                MANUAL_ORIGIN_VISIBILITY.put(key, true);
            }
            else
            {
                MANUAL_ORIGIN_VISIBILITY.remove(key);
            }
        }

        saveToFile();
    }

    public static void loadFromFile()
    {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
        clearManualOriginVisibility();

        if (Files.exists(configFile) && Files.isReadable(configFile))
        {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject())
            {
                JsonObject root = element.getAsJsonObject();
                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", LvcHotkeys.HOTKEY_LIST);
                readManualOriginVisibility(root);
            }
            else
            {
                LvcDiagnostics.warn("Failed to load Gitmatica config file '{}'", configFile.toAbsolutePath());
            }
        }

        updateDebugLogging();
    }

    public static void saveToFile()
    {
        Path directory = FileUtils.getConfigDirectory();

        if (!Files.exists(directory))
        {
            FileUtils.createDirectoriesIfMissing(directory);
        }

        if (Files.isDirectory(directory))
        {
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Hotkeys", LvcHotkeys.HOTKEY_LIST);
            writeManualOriginVisibility(root);
            JsonUtils.writeJsonToFile(root, directory.resolve(CONFIG_FILE_NAME));
        }
        else
        {
            LvcDiagnostics.warn("Gitmatica config directory does not exist: '{}'", directory.toAbsolutePath());
        }
    }

    @Override
    public void load()
    {
        loadFromFile();
    }

    @Override
    public void save()
    {
        saveToFile();
    }

    private static void updateDebugLogging()
    {
        debugLoggingEnabled = Generic.DEBUG_LOGGING.getBooleanValue();
    }

    private static synchronized void readManualOriginVisibility(JsonObject root)
    {
        clearManualOriginVisibility();

        if (!JsonUtils.hasObject(root, MANUAL_ORIGIN_VISIBILITY_KEY))
        {
            return;
        }

        JsonObject values = root.getAsJsonObject(MANUAL_ORIGIN_VISIBILITY_KEY);

        for (Map.Entry<String, JsonElement> entry : values.entrySet())
        {
            if (entry.getValue().isJsonPrimitive() &&
                    entry.getValue().getAsJsonPrimitive().isBoolean() &&
                    entry.getValue().getAsBoolean())
            {
                MANUAL_ORIGIN_VISIBILITY.put(entry.getKey(), true);
            }
        }
    }

    private static synchronized void writeManualOriginVisibility(JsonObject root)
    {
        JsonObject values = new JsonObject();

        for (Map.Entry<String, Boolean> entry :
                new TreeMap<>(MANUAL_ORIGIN_VISIBILITY).entrySet())
        {
            values.addProperty(entry.getKey(), entry.getValue());
        }

        root.add(MANUAL_ORIGIN_VISIBILITY_KEY, values);
    }

    private static String projectKey(Path repositoryDirectory)
    {
        return repositoryDirectory.toAbsolutePath().normalize().toString();
    }

    private static synchronized void clearManualOriginVisibility()
    {
        MANUAL_ORIGIN_VISIBILITY.clear();
    }
}
