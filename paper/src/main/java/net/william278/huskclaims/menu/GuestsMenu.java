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
import net.william278.huskclaims.command.UnTrustCommand;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.trust.TrustLevel;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The players, groups and tags trusted in a claim, and the access everyone has
 */
public class GuestsMenu extends Menu {

    private final ClaimWorld world;
    private final String worldName;
    private final Claim claim;
    private final int page;

    public GuestsMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull ClaimWorld world,
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
        return items().title("gui_guests_title", manager.getClaimName(claim));
    }

    @Override
    protected void build() {
        final Optional<UnTrustCommand> untrust = manager.command(UnTrustCommand.class);
        final boolean allowed = untrust.map(command -> command.canUseOn(user, claim)).orElse(false);

        final List<Guest> guests = getGuests();
        final List<Guest> shown = page(guests, page);
        for (int i = 0; i < shown.size(); i++) {
            final Guest guest = shown.get(i);
            final Optional<TrustLevel> level = plugin.getTrustLevel(guest.levelId());
            final List<Component> lines = MenuItems.join(
                    items().lines("gui_guest_item", MenuItems.escape(guest.name()),
                            level.map(l -> MenuItems.escape(l.getDisplayName())).orElse(guest.levelId()),
                            level.map(TrustLevel::getColor).orElse("#ffffff"),
                            level.map(l -> MenuItems.escape(l.getDescription())).orElse("")),
                    items().lines(allowed ? "gui_guest_click" : "gui_locked")
            );
            final ItemStack item = guest.uuid() != null ? items().head(guest.uuid(), lines)
                    : items().item(guest.identifier().startsWith(getGroupPrefix()) ? "bookshelf" : "name_tag", lines);
            if (!allowed) {
                set(i, item);
                continue;
            }
            set(i, item, (click) -> {
                if (click.isRightClick()) {
                    untrust.ifPresent(command -> plugin.runAsync(() -> command.executeOn(
                            user, world, claim, new String[]{guest.identifier()})));
                    refreshLater();
                    return;
                }
                new TrustLevelMenu(manager, player, world, claim, this, guest.identifier(),
                        MenuItems.escape(guest.name()), guest.levelId()).open();
            });
        }

        // Add a guest by typing their name
        set(47, items().item("emerald", MenuItems.join(items().lines("gui_button_add_guest"),
                items().lines(allowed ? "gui_button_add_guest_click" : "gui_locked"))), () -> {
            if (allowed) {
                manager.prompt(player, "gui_prompt_guest", (name) -> new TrustLevelMenu(manager, player, world,
                        claim, this, name, MenuItems.escape(name), null).open(), this::open);
            }
        });

        // Set what everyone may do, with the public trust tag
        final Settings.TrustTagSettings tags = plugin.getSettings().getTrustTags();
        if (tags.isEnabled()) {
            final String publicLevelId = claim.getTrustedTags().get(tags.getPublicAccessName());
            final String level = publicLevelId == null ? items().raw("gui_no_access")
                    : plugin.getTrustLevel(publicLevelId)
                    .map(l -> "[%s](%s)".formatted(MenuItems.escape(l.getDisplayName()), l.getColor()))
                    .orElse(publicLevelId);
            set(51, items().item("oak_sign", MenuItems.join(items().lines("gui_button_everyone", level),
                    items().lines(allowed ? "gui_button_everyone_click" : "gui_locked"))), () -> {
                if (allowed) {
                    new TrustLevelMenu(manager, player, world, claim, this,
                            tags.getTagSpecifierPrefix() + tags.getPublicAccessName(),
                            items().raw("gui_everyone"), publicLevelId).open();
                }
            });
        }

        addPageButtons(page, guests.size(), (newPage) -> new GuestsMenu(manager, player, world, worldName,
                claim, newPage).open());
        set(49, items().item("arrow", items().lines("gui_button_back")),
                () -> new ClaimMenu(manager, player, world, worldName, claim).open());
    }

    // Trusted users, then groups, then tags (except the public tag, which has its own button)
    @NotNull
    private List<Guest> getGuests() {
        final List<Guest> users = Lists.newArrayList();
        claim.getTrustedUsers().forEach((uuid, level) -> users.add(new Guest(
                manager.getUserName(world, uuid), manager.getUserName(world, uuid), uuid, level)));
        users.sort(Comparator.comparing(guest -> guest.name().toLowerCase()));

        final List<Guest> guests = Lists.newArrayList(users);
        claim.getTrustedGroups().forEach((group, level) -> guests.add(new Guest(
                getGroupPrefix() + group, getGroupPrefix() + group, null, level)));
        final Settings.TrustTagSettings tags = plugin.getSettings().getTrustTags();
        claim.getTrustedTags().forEach((tag, level) -> {
            if (!tags.isEnabled() || !tag.equals(tags.getPublicAccessName())) {
                guests.add(new Guest(tags.getTagSpecifierPrefix() + tag, tags.getTagSpecifierPrefix() + tag,
                        null, level));
            }
        });
        return guests;
    }

    @NotNull
    private String getGroupPrefix() {
        return plugin.getSettings().getUserGroups().getGroupSpecifierPrefix();
    }

    private record Guest(@NotNull String identifier, @NotNull String name, @Nullable UUID uuid,
                         @NotNull String levelId) {
    }

}
