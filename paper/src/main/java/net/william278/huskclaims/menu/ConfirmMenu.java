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

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Asks the player to confirm an action that can't be undone
 */
public class ConfirmMenu extends Menu {

    private final String key;
    private final String material;
    private final Menu back;
    private final Runnable onConfirm;
    private final String[] replacements;

    /**
     * @param key          The message key describing the action; {@code key + "_title"} is the menu title
     * @param material     The icon for the action
     * @param back         The menu to return to on cancel
     * @param onConfirm    What to do once confirmed (on the player's thread)
     * @param replacements Replacements for the action message and title, escaped for use in a menu message
     */
    public ConfirmMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull String key,
                       @NotNull String material, @NotNull Menu back, @NotNull Runnable onConfirm,
                       @NotNull String... replacements) {
        super(manager, player);
        this.key = key;
        this.material = material;
        this.back = back;
        this.onConfirm = onConfirm;
        this.replacements = replacements;
    }

    @Override
    protected int getRows() {
        return 3;
    }

    @NotNull
    @Override
    protected Component getTitle() {
        return items().title(key + "_title", replacements);
    }

    @Override
    protected void build() {
        set(13, items().item(material, items().lines(key, replacements)));
        set(11, items().item("lime_concrete", items().lines("gui_button_confirm")), () -> {
            player.closeInventory();
            onConfirm.run();
        });
        set(15, items().item("red_concrete", items().lines("gui_button_cancel")), back::open);
    }

}
