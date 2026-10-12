/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskclaims.menu;

import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.william278.huskclaims.PaperHuskClaims;
import net.william278.huskclaims.config.Locales;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Builds menu titles and items from the messages file. In a menu message, the first line is the item name and
 * the lines after it are the item lore.
 */
public final class MenuItems {

    private final PaperHuskClaims plugin;

    MenuItems(@NotNull PaperHuskClaims plugin) {
        this.plugin = plugin;
    }

    /**
     * Get the formatted lines of a menu message. Replacements are inserted as-is: escape player-provided text
     * with {@link #escape(String)}
     */
    @NotNull
    public List<Component> lines(@NotNull String key, @NotNull String... replacements) {
        final String raw = plugin.getLocales().getRawLocale(key, replacements).orElse(key);
        return Arrays.stream(raw.split("\n", -1)).map(this::line).toList();
    }

    @NotNull
    public Component title(@NotNull String key, @NotNull String... replacements) {
        return lines(key, replacements).get(0);
    }

    @NotNull
    public String raw(@NotNull String key, @NotNull String... replacements) {
        return plugin.getLocales().getRawLocale(key, replacements).orElse("");
    }

    @NotNull
    public static String escape(@NotNull String text) {
        return Locales.escapeText(text);
    }

    @NotNull
    @SafeVarargs
    public static List<Component> join(@NotNull List<Component>... parts) {
        final List<Component> joined = Lists.newArrayList();
        for (List<Component> part : parts) {
            joined.addAll(part);
        }
        return joined;
    }

    // The material can list fallbacks, separated by commas
    @NotNull
    public ItemStack item(@NotNull String material, @NotNull List<Component> lines) {
        final ItemStack item = new ItemStack(material(material.split(",")));
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!lines.isEmpty()) {
                meta.displayName(lines.get(0));
                meta.lore(lines.subList(1, lines.size()));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @NotNull
    public ItemStack head(@NotNull UUID owner, @NotNull List<Component> lines) {
        final ItemStack item = item("player_head", lines);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(plugin.getServer().getOfflinePlayer(owner));
            item.setItemMeta(skull);
        }
        return item;
    }

    // Look materials up by name, so an icon renamed or removed in a later Minecraft version doesn't break the menu
    @NotNull
    static Material material(@NotNull String... names) {
        for (String name : names) {
            final Material material = Material.matchMaterial(name);
            if (material != null && material.isItem()) {
                return material;
            }
        }
        return Material.PAPER;
    }

    @NotNull
    private Component line(@NotNull String text) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        return Component.text()
                .append(plugin.getLocales().format(text).toComponent())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

}
