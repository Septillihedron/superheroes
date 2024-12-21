package me.xemor.superheroes.data;

import me.xemor.superheroes.Superheroes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.intellij.lang.annotations.RegExp;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

public class ConstantsPreprocessor {

    private ConfigurationSection globalConstants;
    @RegExp
    public static final String constantPathRegex = "C\\{.*}";

    protected ConstantsPreprocessor(File dataFolder) {
        reloadConfig(dataFolder);
    }

    protected void reloadConfig(File dataFolder) {
        this.globalConstants = YamlConfiguration.loadConfiguration(new File(dataFolder, "constants.yml"));
        process(this.globalConstants, false);
    }

    public void process(ConfigurationSection input) {
        process(input, true);
    }

    private void process(ConfigurationSection input, boolean hasLocalConstants) {
        ConfigurationSection localConstants = hasLocalConstants? input.getConfigurationSection("constants") : null;
        processSection(input, localConstants);
    }

    private void processSection(ConfigurationSection input, ConfigurationSection localConstants) {
        input.getValues(false).forEach((key, value) -> {
            if (value instanceof ConfigurationSection section) {
                processSection(section, localConstants);
                return;
            }
            if (value instanceof List<?> list) {
                input.set(key, processList(list, localConstants));
                return;
            }
            if (value instanceof String path) {
                replaceConstantInSection(input, localConstants, key, path);
            }
        });
    }

    private void replaceConstantInSection(ConfigurationSection input, ConfigurationSection localConstants, String key, String path) {
        ConstantData constantData = getConstantData(localConstants, path);
        if (constantData == null) return;

        if (!constantData.isSpread) {
            input.set(path, constantData.value);
            return;
        }
        if (constantData.value instanceof ConfigurationSection constantSection) {
            input.set(key, null);
            replaceSpreadConstantSection(input, path, constantSection, input);
        } else if (constantData.value instanceof List<?>) {
            Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Lists cannot be spread in a section");
        } else {
            Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Only sections and lists can be spread");
        }
    }

    private List<?> processList(List<?> input, ConfigurationSection localConstants) {
        ArrayList<Object> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Object value = input.get(i);
            if (value instanceof ConfigurationSection section) {
                processSection(section, localConstants);
                result.add(section);
                continue;
            }
            if (value instanceof List<?> list) {
                result.add(processList(list, localConstants));
                continue;
            }
            if (value instanceof String path) {
                ConstantData constantData = getConstantData(localConstants, path);
                if (constantData == null) continue;

                if (!constantData.isSpread) {
                    result.add(i, constantData.value);
                    continue;
                }
                if (constantData.value instanceof List<?> list) {
                    result.addAll(list);
                } else if (constantData.value instanceof ConfigurationSection) {
                    Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Sections cannot be spread in a list");
                } else {
                    Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Only sections and lists can be spread");
                }
            }
        }
        return result;
    }

    private @org.jetbrains.annotations.Nullable ConstantData getConstantData(ConfigurationSection localConstants, String path) {
        if (!path.matches(constantPathRegex)) return null;
        boolean isSpread = path.startsWith("...", 2);
        String constantPath = extractPath(path, isSpread);
        Object constantValue = lookupConstant(localConstants, constantPath, path);
        return new ConstantData(constantValue, isSpread);
    }

    private static void replaceSpreadConstantSection(ConfigurationSection input, String path, ConfigurationSection constantSection, ConfigurationSection parent) {
        constantSection.getValues(false)
                .forEach((key, value) -> {
                    if (parent.contains(key)) {
                        Superheroes.getInstance().getLogger().warning("Constant at " + path + " is overriding a value with key: " + key);
                    }
                    parent.set(key, value);
                });
    }

    private static String extractPath(String value, boolean isSpread) {
        int prefixLength = isSpread? "C{...".length() : "C{".length();
        return value.substring(
                prefixLength,
                value.length()-1
        );
    }

    private @Nullable Object lookupConstant(@Nullable ConfigurationSection localConstants, String path, String location) {
        if (localConstants != null && localConstants.contains(path)) {
            return localConstants.get(path);
        }
        if (globalConstants.contains(path)) {
            return globalConstants.get(path);
        }
        Superheroes.getInstance().getLogger().severe("Invalid constant path: \"" + path + "\" at "+location);
        return null;
    }

    private record ConstantData(Object value, boolean isSpread) {}

}
