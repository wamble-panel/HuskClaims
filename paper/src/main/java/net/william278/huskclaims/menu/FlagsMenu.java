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
import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.command.ClaimFlagsCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Toggle what everyone may do in a claim
 */
public class FlagsMenu extends Menu {

    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("block_place", "bricks"),
            Map.entry("block_break", "cracked_stone_bricks"),
            Map.entry("block_interact", "crafting_table"),
            Map.entry("redstone_interact", "lever"),
            Map.entry("redstone_actuate", "redstone"),
            Map.entry("farm_block_break", "wheat"),
            Map.entry("farm_block_place", "wheat_seeds"),
            Map.entry("farm_block_interact", "composter"),
            Map.entry("player_damage_player", "player_head"),
            Map.entry("player_damage_monster", "zombie_head"),
            Map.entry("player_damage_entity", "leather"),
            Map.entry("player_damage_persistent_entity", "armor_stand"),
            Map.entry("monster_spawn", "spawner"),
            Map.entry("passive_mob_spawn", "egg"),
            Map.entry("monster_damage_terrain", "creeper_head"),
            Map.entry("explosion_damage_terrain", "tnt"),
            Map.entry("explosion_damage_entity", "gunpowder"),
            Map.entry("fire_burn", "campfire"),
            Map.entry("fire_spread", "flint_and_steel"),
            Map.entry("fill_bucket", "bucket"),
            Map.entry("empty_bucket", "water_bucket"),
            Map.entry("place_hanging_entity", "item_frame"),
            Map.entry("break_hanging_entity", "painting"),
            Map.entry("place_vehicle", "minecart"),
            Map.entry("break_vehicle", "oak_boat"),
            Map.entry("entity_interact", "lead"),
            Map.entry("use_spawn_egg", "pig_spawn_egg"),
            Map.entry("ender_pearl_teleport", "ender_pearl"),
            Map.entry("container_open", "chest"),
            Map.entry("start_raid", "bell")
    );

    private final ClaimWorld world;
    private final String worldName;
    private final Claim claim;
    private final int page;

    public FlagsMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull ClaimWorld world,
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
        return items().title("gui_flags_title", manager.getClaimName(claim));
    }

    @Override
    protected void build() {
        final ClaimFlagsCommand command = manager.command(ClaimFlagsCommand.class).orElse(null);
        if (command != null) {
            final boolean allowed = command.canManageFlags(user, claim);
            final List<OperationType> flags = command.getManageableFlags(user);
            final List<OperationType> shown = page(flags, page);
            for (int i = 0; i < shown.size(); i++) {
                final OperationType flag = shown.get(i);
                final boolean enabled = claim.getDefaultFlags().contains(flag);
                final String[] text = getFlagText(flag);
                final List<Component> lines = MenuItems.join(
                        items().lines("gui_flag_item", MenuItems.escape(text[0]), MenuItems.escape(text[1]),
                                items().raw(enabled ? "gui_flag_allowed" : "gui_flag_denied")),
                        items().lines(allowed ? "gui_flag_click" : "gui_locked")
                );
                final String icon = ICONS.getOrDefault(flag.asMinimalString(), "paper");
                if (allowed) {
                    set(i, items().item(icon, lines), () -> {
                        final boolean value = !claim.getDefaultFlags().contains(flag);
                        plugin.runAsync(() -> command.setClaimFlag(user, world, claim, flag, value));
                        refreshLater();
                    });
                } else {
                    set(i, items().item(icon, lines));
                }
            }
            addPageButtons(page, flags.size(), (newPage) -> new FlagsMenu(manager, player, world, worldName,
                    claim, newPage).open());
        }
        set(49, items().item("arrow", items().lines("gui_button_back")),
                () -> new ClaimMenu(manager, player, world, worldName, claim).open());
    }

    // The name and description of a flag from the messages file, or its name spelled out
    @NotNull
    private String[] getFlagText(@NotNull OperationType flag) {
        final String id = flag.asMinimalString();
        final String[] text = plugin.getLocales().getRawLocale("gui_flag_" + id)
                .map(raw -> raw.split("\n", 2))
                .orElseGet(() -> new String[0]);
        final String name = text.length > 0 && !text[0].isBlank() ? text[0] : Arrays.stream(id.split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ENGLISH) + word.substring(1))
                .collect(Collectors.joining(" "));
        return new String[]{name, text.length > 1 ? text[1] : ""};
    }

}
