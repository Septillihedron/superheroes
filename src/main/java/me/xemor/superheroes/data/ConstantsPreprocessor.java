package me.xemor.superheroes.data;

import me.xemor.superheroes.Superheroes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.intellij.lang.annotations.RegExp;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

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

        for (String path : input.getKeys(true)) {
            String value = input.getString(path, null);
            if (value == null) continue;
            if (!value.matches(constantPathRegex)) continue;
            input.set(path, null);

            boolean isSpread = value.startsWith("...", 2);
            String constantPath = extractPath(value, isSpread);

            Optional<Object> constantValueOptional = lookupConstant(localConstants, constantPath)
                    .or(() -> lookupConstant(globalConstants, constantPath));

            if (constantValueOptional.isEmpty()) {
                Superheroes.getInstance().getLogger().severe("Invalid constant path: \"" + constantPath + "\"");
                continue;
            }

            Object constantValue = constantValueOptional.get();

            replaceConstant(input, path, isSpread, constantValue);
        }
    }

    private void replaceConstant(ConfigurationSection input, String path, boolean isSpread, Object constantValue) {
        if (!isSpread) {
            input.set(path, constantValue);
        } else {
            replaceSpreadConstant(input, path, constantValue);
        }
    }

    private void replaceSpreadConstant(ConfigurationSection input, String path, Object constantValue) {
        String parentPath = getParentPath(path);
        if (constantValue instanceof ConfigurationSection constantSection) {
            replaceSpreadConstantSection(input, path, constantSection, parentPath);
        } else if (constantValue instanceof List<?> constantList) {
            replaceSpreadConstantList(input, path, constantList, parentPath);
        } else {
            Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Only sections and lists can be spread");
        }
    }

    private static void replaceSpreadConstantSection(ConfigurationSection input, String path, ConfigurationSection constantSection, String parentPath) {
        ConfigurationSection target = input.getConfigurationSection(parentPath);
        if (target == null) {
            Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Spread operator on section constants must be in a section");
            return;
        }
        constantSection.getValues(false)
                .forEach((key, value) -> {
                    if (target.contains(key)) {
                        Superheroes.getInstance().getLogger().warning("Constant at " + path + " is overriding a value with key: " + key);
                    }
                    target.set(key, value);
                });
    }

    private static void replaceSpreadConstantList(ConfigurationSection input, String path, List<?> constantList, String parentPath) {
        List<?> target = input.getList(parentPath);
        if (target == null) {
            Superheroes.getInstance().getLogger().severe("Invalid spread operator at " + path + ". Spread operator on list constants must be in a list");
            return;
        }
        List<Object> newList = new ArrayList<>(target);
        newList.addAll(constantList);
        input.set(parentPath, newList);
    }

    private String getParentPath(String path) {
        int lastDotIndex = path.lastIndexOf(".");
        if (lastDotIndex == -1) {
            throw new IllegalArgumentException("Failed in getting the parent of a path");
        }
        return path.substring(0, lastDotIndex);
    }

    private static String extractPath(String value, boolean isSpread) {
        return value.substring(
                isSpread? "C{...".length() : "C{".length(),
                value.length()-1
        );
    }

    private static Optional<Object> lookupConstant(@Nullable ConfigurationSection constants, String path) {
        if (constants == null) return Optional.empty();
        if (!constants.contains(path)) return Optional.empty();
        return Optional.of(Objects.requireNonNull(constants.get(path)));
    }

}
