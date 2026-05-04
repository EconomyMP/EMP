package github.nighter.smartspawner.emp.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class EmpConfig {
    private final SmartSpawner plugin;
    private FileConfiguration config;
    private File configFile;

    public EmpConfig(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "emp.yml");
        }

        if (!configFile.exists()) {
            plugin.saveResource("emp.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        load();
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
