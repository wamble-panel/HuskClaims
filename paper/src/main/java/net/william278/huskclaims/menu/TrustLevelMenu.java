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
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.command.TrustCommand;
import net.william278.huskclaims.command.UnTrustCommand;
import net.william278.huskclaims.trust.TrustLevel;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pick the trust level to give a player, group or tag in a claim
 */
public class TrustLevelMenu extends Menu {

    private static final Map<String, String> ICONS = Map.of(
            "access", "oak_door",
            "container", "chest",
            "build", "bricks",
            "manage", "writable_book"
    );

    private final ClaimWorld world;
    private final Claim claim;
    private final Menu back;
    private final String identifier;
    private final String displayName;
    @Nullable
    private final String currentLevel;

    /**
     * @param identifier   What to trust, as typed in a trust command (a name, {@code @group} or {@code #tag})
     * @param displayName  The name to show, escaped for use in a menu message
     * @param currentLevel The ID of the trust level they have now, if any
     */
    public TrustLevelMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull ClaimWorld world,
                          @NotNull Claim claim, @NotNull Menu back, @NotNull String identifier,
                          @NotNull String displayName, @Nullable String currentLevel) {
        super(manager, player);
        this.world = world;
        this.claim = claim;
        this.back = back;
        this.identifier = identifier;
        this.displayName = displayName;
        this.currentLevel = currentLevel;
    }

    @Override
    protected int getRows() {
        return 3;
    }

    @NotNull
    @Override
    protected Component getTitle() {
        return items().title("gui_trust_level_title", displayName);
    }

    @Override
    protected void build() {
        // Trust levels can only be given with their trust command
        final List<TrustCommand> levels = plugin.getCommands().stream()
                .filter(command -> command instanceof TrustCommand)
                .map(command -> (TrustCommand) command)
                .sorted(Comparator.comparingInt(command -> command.getLevel().getWeight()))
                .toList();
        for (int i = 0; i < levels.size() && i < 9; i++) {
            final TrustCommand command = levels.get(i);
            final TrustLevel level = command.getLevel();
            final boolean current = level.getId().equalsIgnoreCase(currentLevel);
            final List<Component> lines = MenuItems.join(
                    items().lines("gui_trust_level_item", MenuItems.escape(level.getDisplayName()), level.getColor(),
                            MenuItems.escape(level.getDescription())),
                    items().lines(current ? "gui_trust_level_current" : "gui_trust_level_click")
            );
            final String icon = ICONS.getOrDefault(level.getId().toLowerCase(Locale.ENGLISH), "paper");
            set(centered(1, i, levels.size()), items().item(icon, lines), () -> {
                if (!current) {
                    plugin.runAsync(() -> command.executeOn(user, world, claim, new String[]{identifier}));
                }
                back.showLater();
            });
        }

        set(18, items().item("arrow", items().lines("gui_button_back")), back::open);
        if (currentLevel != null) {
            manager.command(UnTrustCommand.class).ifPresent(untrust -> set(26,
                    items().item("barrier", items().lines("gui_button_remove_guest")), () -> {
                        plugin.runAsync(() -> untrust.executeOn(user, world, claim, new String[]{identifier}));
                        back.showLater();
                    }));
        }
    }

}
