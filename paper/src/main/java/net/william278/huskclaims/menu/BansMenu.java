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
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.command.ClaimBanCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The players banned from a claim
 */
public class BansMenu extends Menu {

    private final ClaimWorld world;
    private final String worldName;
    private final Claim claim;
    private final int page;

    public BansMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull ClaimWorld world,
                    @NotNull String worldName, @NotNull Claim claim, int page) {
        super(manager, player);
        this.world = world;
        this.worldName = worldName;
        this.claim = claim;
        this.page = page;
    }

    @Override
    protected int getRows() {
        return 6;
    }

    @NotNull
    @Override
    protected Component getTitle() {
        return items().title("gui_bans_title", manager.getClaimName(claim));
    }

    @Override
    protected void build() {
        final Optional<ClaimBanCommand> command = manager.command(ClaimBanCommand.class);
        final boolean allowed = command.map(ban -> ban.canUseOn(user, claim)).orElse(false);

        final List<Ban> bans = Lists.newArrayList();
        claim.getBannedUsers().forEach((banned, arbiter) -> bans.add(new Ban(banned,
                manager.getUserName(world, banned), manager.getUserName(world, arbiter))));
        bans.sort(Comparator.comparing(ban -> ban.name().toLowerCase(Locale.ENGLISH)));

        final List<Ban> shown = page(bans, page);
        for (int i = 0; i < shown.size(); i++) {
            final Ban ban = shown.get(i);
            final List<Component> lines = MenuItems.join(
                    items().lines("gui_ban_item", MenuItems.escape(ban.name()), MenuItems.escape(ban.arbiter())),
                    items().lines(allowed ? "gui_ban_click" : "gui_locked")
            );
            if (!allowed) {
                set(i, items().head(ban.uuid(), lines));
                continue;
            }
            set(i, items().head(ban.uuid(), lines), () -> {
                command.ifPresent(unban -> plugin.runAsync(() -> unban.executeOn(user, world, claim,
                        new String[]{"unban", ban.name()})));
                refreshLater();
            });
        }

        set(51, items().item("iron_bars", MenuItems.join(items().lines("gui_button_ban_player"),
                items().lines(allowed ? "gui_button_ban_player_click" : "gui_locked"))), () -> {
            if (allowed) {
                manager.prompt(player, "gui_prompt_ban", (name) -> {
                    command.ifPresent(ban -> plugin.runAsync(() -> ban.executeOn(user, world, claim,
                            new String[]{"ban", name})));
                    showLater();
                }, this::open);
            }
        });

        addPageButtons(page, bans.size(), (newPage) -> new BansMenu(manager, player, world, worldName,
                claim, newPage).open());
        set(49, items().item("arrow", items().lines("gui_button_back")),
                () -> new ClaimMenu(manager, player, world, worldName, claim).open());
    }

    private record Ban(@NotNull UUID uuid, @NotNull String name, @NotNull String arbiter) {
    }

}
