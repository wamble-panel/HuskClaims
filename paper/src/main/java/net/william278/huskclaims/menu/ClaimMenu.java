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
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.command.ClaimBanCommand;
import net.william278.huskclaims.command.ClaimFlagsCommand;
import net.william278.huskclaims.command.ClaimPrivateCommand;
import net.william278.huskclaims.command.TransferClaimCommand;
import net.william278.huskclaims.command.UnClaimCommand;
import net.william278.huskclaims.command.UnTrustCommand;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Manage one claim: guests, flags, bans, entry whitelist, name, transfer and unclaiming
 */
public class ClaimMenu extends Menu {

    private final ClaimWorld world;
    private final String worldName;
    private final Claim claim;

    public ClaimMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull ClaimWorld world,
                     @NotNull String worldName, @NotNull Claim claim) {
        super(manager, player);
        this.world = world;
        this.worldName = worldName;
        this.claim = claim;
    }

    /**
     * The lines describing a claim, shared with the claims list
     */
    @NotNull
    static List<Component> getInfoLines(@NotNull MenuManager manager, @NotNull OnlineUser viewer,
                                        @NotNull ClaimWorld world, @NotNull String worldName, @NotNull Claim claim) {
        final MenuItems items = manager.getItems();
        final String ownerLine;
        if (claim.isAdminClaim() || claim.getOwner().isEmpty()) {
            ownerLine = items.raw("gui_claim_owner_admin");
        } else if (claim.getOwner().get().equals(viewer.getUuid())) {
            ownerLine = items.raw("gui_claim_owner_you");
        } else {
            ownerLine = items.raw("gui_claim_owner_other",
                    MenuItems.escape(manager.getUserName(world, claim.getOwner().get())));
        }
        final Region.Point center = claim.getRegion().getCenter();
        return items.lines("gui_claim_item",
                manager.getClaimName(claim),
                ownerLine,
                MenuItems.escape(worldName),
                Integer.toString(claim.getRegion().getSurfaceArea()),
                Integer.toString(center.getBlockX()),
                Integer.toString(center.getBlockZ()),
                Integer.toString(claim.getChildren().size()),
                Integer.toString(claim.getTrustedUsers().size() + claim.getTrustedGroups().size()
                                 + claim.getTrustedTags().size()),
                Integer.toString(claim.getBannedUsers().size()),
                manager.getStatus(claim.isPrivateClaim())
        );
    }

    @Override
    public void open() {
        if (!manager.exists(world, claim)) {
            plugin.getLocales().getLocale("gui_error_claim_gone").ifPresent(user::sendMessage);
            player.closeInventory();
            return;
        }
        super.open();
    }

    @Override
    protected int getRows() {
        return 5;
    }

    @NotNull
    @Override
    protected Component getTitle() {
        return items().title("gui_claim_title", manager.getClaimName(claim));
    }

    @Override
    protected void build() {
        set(4, items().item(manager.getWorldIcon(worldName),
                getInfoLines(manager, user, world, worldName, claim)));

        final List<Button> buttons = getButtons();
        for (int i = 0; i < buttons.size(); i++) {
            final Button button = buttons.get(i);
            final List<Component> lines = MenuItems.join(
                    items().lines("gui_button_" + button.key(), button.replacements()),
                    items().lines(button.allowed() ? "gui_button_" + button.key() + "_click" : "gui_locked")
            );
            if (button.allowed()) {
                set(centered(2, i, buttons.size()), items().item(button.material(), lines), button.action());
            } else {
                set(centered(2, i, buttons.size()), items().item(button.material(), lines));
            }
        }

        set(40, items().item("arrow", items().lines("gui_button_back")), this::showOwnerClaims);
    }

    @NotNull
    private List<Button> getButtons() {
        final List<Button> buttons = Lists.newArrayList();
        manager.command(UnTrustCommand.class).ifPresent(untrust -> buttons.add(new Button(
                "guests", "player_head", untrust.canUseOn(user, claim),
                () -> new GuestsMenu(manager, player, world, worldName, claim, 0).open()
        )));
        manager.command(ClaimFlagsCommand.class).ifPresent(flags -> buttons.add(new Button(
                "flags", "redstone_torch",
                flags.canManageFlags(user, claim) && !flags.getManageableFlags(user).isEmpty(),
                () -> new FlagsMenu(manager, player, world, worldName, claim, 0).open()
        )));
        manager.command(ClaimBanCommand.class).ifPresent(bans -> buttons.add(new Button(
                "bans", "barrier", bans.canUseOn(user, claim),
                () -> new BansMenu(manager, player, world, worldName, claim, 0).open()
        )));
        manager.command(ClaimPrivateCommand.class).ifPresent(privacy -> buttons.add(new Button(
                "entry_whitelist", "oak_door", privacy.canUseOn(user, claim),
                () -> {
                    final String[] args = {claim.isPrivateClaim() ? "off" : "on"};
                    plugin.runAsync(() -> privacy.executeOn(user, world, claim, args));
                    refreshLater();
                },
                manager.getStatus(claim.isPrivateClaim())
        )));
        buttons.add(new Button(
                "rename", "name_tag", manager.canRename(user, claim),
                () -> manager.prompt(player, "gui_prompt_rename", (name) -> {
                    manager.renameClaim(user, world, claim, name);
                    open();
                }, this::open)
        ));
        manager.command(TransferClaimCommand.class).ifPresent(transfer -> buttons.add(new Button(
                "transfer", "lead", transfer.canUseOn(user, claim) && manager.canRename(user, claim),
                () -> manager.prompt(player, "gui_prompt_transfer", (name) -> new ConfirmMenu(
                        manager, player, "gui_confirm_transfer", "lead", this,
                        () -> {
                            plugin.runAsync(() -> transfer.executeOn(user, world, claim, new String[]{name}));
                            later(this::showOwnerClaims);
                        }, MenuItems.escape(name), manager.getClaimName(claim)
                ).open(), this::open)
        )));
        manager.command(UnClaimCommand.class).ifPresent(unclaim -> buttons.add(new Button(
                "unclaim", "tnt", unclaim.canUseOn(user, claim) && manager.canRename(user, claim),
                () -> new ConfirmMenu(
                        manager, player, "gui_confirm_unclaim", "tnt", this,
                        () -> {
                            plugin.runAsync(() -> unclaim.executeOn(user, world, claim, new String[]{"confirm"}));
                            later(this::showOwnerClaims);
                        }, manager.getClaimName(claim)
                ).open()
        )));
        return buttons;
    }

    // Back to the claims list of this claim's owner, or the viewer's own if they may not see the owner's
    private void showOwnerClaims() {
        final UUID owner = claim.getOwner().orElse(null);
        if (owner != null && (owner.equals(user.getUuid()) || user.hasPermission(MenuManager.OTHER_PERMISSION))) {
            ClaimListMenu.show(manager, player, User.of(owner, manager.getUserName(world, owner)));
            return;
        }
        ClaimListMenu.show(manager, player, user);
    }

    private record Button(@NotNull String key, @NotNull String material, boolean allowed,
                          @NotNull Runnable action, @NotNull String... replacements) {
    }

}
