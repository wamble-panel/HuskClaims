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

import com.google.common.collect.Maps;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.william278.huskclaims.PaperHuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.ClaimingMode;
import net.william278.huskclaims.command.Command;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tracks open claim menus, protects their contents and collects text typed in chat for them
 */
public class MenuManager implements Listener {

    public static final String NAME_KEY = "name";
    public static final String OTHER_PERMISSION = "huskclaims.command.claims.other";
    private static final long PROMPT_TIMEOUT_MILLIS = 60_000;
    private static final int MAX_NAME_LENGTH = 32;

    @Getter
    private final PaperHuskClaims plugin;
    @Getter
    private final MenuItems items;
    private final Map<UUID, Menu> openMenus = Maps.newConcurrentMap();
    private final Map<UUID, Prompt> prompts = Maps.newConcurrentMap();

    public MenuManager(@NotNull PaperHuskClaims plugin) {
        this.plugin = plugin;
        this.items = new MenuItems(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void setOpenMenu(@NotNull Player player, @NotNull Menu menu) {
        openMenus.put(player.getUniqueId(), menu);
    }

    boolean isViewing(@NotNull Player player, @NotNull Menu menu) {
        return openMenus.get(player.getUniqueId()) == menu;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        menu.click(event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            openMenus.remove(event.getPlayer().getUniqueId(), menu);
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || prompt.expiry() < System.currentTimeMillis()) {
            return;
        }
        event.setCancelled(true);
        final String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.runSync(player, () -> {
            if (input.equalsIgnoreCase("cancel")) {
                plugin.getLocales().getLocale("gui_prompt_cancelled")
                        .ifPresent(plugin.getOnlineUser(player)::sendMessage);
                prompt.onCancel().run();
                return;
            }
            prompt.onInput().accept(input);
        });
    }

    /**
     * Close the menu and ask the player to type something in chat
     *
     * @param player   The player
     * @param message  The message key explaining what to type
     * @param onInput  Called on the player's thread with what they typed
     * @param onCancel Called on the player's thread if they type "cancel"
     */
    public void prompt(@NotNull Player player, @NotNull String message, @NotNull Consumer<String> onInput,
                       @NotNull Runnable onCancel) {
        player.closeInventory();
        prompts.put(player.getUniqueId(), new Prompt(onInput, onCancel,
                System.currentTimeMillis() + PROMPT_TIMEOUT_MILLIS));
        plugin.getLocales().getLocale(message)
                .ifPresent(plugin.getOnlineUser(player)::sendMessage);
    }

    /**
     * Get a registered command
     */
    @NotNull
    public <T extends Command> Optional<T> command(@NotNull Class<T> type) {
        return plugin.getCommand(type);
    }

    /**
     * Returns if a user may manage a claim in some way, i.e. whether opening its menu is worthwhile
     */
    public boolean canManage(@NotNull OnlineUser user, @NotNull Claim claim) {
        if (user.hasPermission(OTHER_PERMISSION)) {
            return true;
        }
        if (claim.isAdminClaim()) {
            return ClaimingMode.ADMIN_CLAIMS.canUse(user);
        }
        return claim.getOwner().map(owner -> owner.equals(user.getUuid())).orElse(false)
                || Arrays.stream(TrustLevel.Privilege.values())
                .anyMatch(privilege -> claim.isPrivilegeAllowed(privilege, user, plugin));
    }

    /**
     * Returns if a user may rename a claim
     */
    public boolean canRename(@NotNull OnlineUser user, @NotNull Claim claim) {
        if (user.hasPermission(OTHER_PERMISSION)) {
            return true;
        }
        if (claim.isAdminClaim()) {
            return ClaimingMode.ADMIN_CLAIMS.canUse(user);
        }
        return claim.getOwner().map(owner -> owner.equals(user.getUuid())).orElse(false)
                || claim.isPrivilegeAllowed(TrustLevel.Privilege.MANAGE_CHILD_CLAIMS, user, plugin);
    }

    /**
     * Rename a claim, checking the user may and the name is valid. Saves off the main thread
     */
    public void renameClaim(@NotNull OnlineUser user, @NotNull ClaimWorld world, @NotNull Claim claim,
                            @NotNull String name) {
        if (!canRename(user, claim)) {
            plugin.getLocales().getLocale("no_claim_privilege").ifPresent(user::sendMessage);
            return;
        }
        final String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH || trimmed.chars().anyMatch(Character::isISOControl)) {
            plugin.getLocales().getLocale("gui_error_invalid_name", Integer.toString(MAX_NAME_LENGTH))
                    .ifPresent(user::sendMessage);
            return;
        }
        claim.getMetadata().put(NAME_KEY, trimmed);
        plugin.runAsync(() -> plugin.getDatabase().updateClaimWorld(world));
        plugin.getLocales().getLocale("gui_claim_renamed", trimmed).ifPresent(user::sendMessage);
    }

    /**
     * Get the display name of a claim, escaped for use in a menu message
     */
    @NotNull
    public String getClaimName(@NotNull Claim claim) {
        final String name = claim.getMetadata().get(NAME_KEY);
        if (name != null && !name.isBlank()) {
            return MenuItems.escape(name);
        }
        return items.raw(claim.isAdminClaim() ? "gui_admin_claim" : "gui_unnamed_claim");
    }

    /**
     * Returns if a claim (or the parent of a child claim) still exists in a world
     */
    public boolean exists(@NotNull ClaimWorld world, @NotNull Claim claim) {
        return claim.getParent().map(parent -> world.contains(parent) && parent.getChildren().contains(claim))
                .orElseGet(() -> world.contains(claim));
    }

    /**
     * Get the icon for claims in a world
     */
    @NotNull
    public String getWorldIcon(@NotNull String worldName) {
        final World world = plugin.getServer().getWorld(worldName);
        final String environment = world == null ? "" : world.getEnvironment().name().toLowerCase(Locale.ENGLISH);
        return switch (environment) {
            case "nether" -> "netherrack";
            case "the_end" -> "end_stone";
            default -> "grass_block";
        };
    }

    @NotNull
    public String getStatus(boolean enabled) {
        return items.raw(enabled ? "gui_enabled" : "gui_disabled");
    }

    // The name a user was last seen with in a world, or their server name
    @NotNull
    public String getUserName(@NotNull ClaimWorld world, @NotNull UUID uuid) {
        return world.getUser(uuid).map(user -> user.getName())
                .or(() -> Optional.ofNullable(plugin.getServer().getOfflinePlayer(uuid).getName()))
                .orElse(uuid.toString().substring(0, 8));
    }

    private record Prompt(@NotNull Consumer<String> onInput, @NotNull Runnable onCancel, long expiry) {
    }

}
