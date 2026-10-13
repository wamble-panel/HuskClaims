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

package net.william278.huskclaims.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.william278.cloplib.operation.Operation;
import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.BukkitHuskClaims;
import net.william278.huskclaims.moderation.SignWrite;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PaperListener extends BukkitListener {

    public PaperListener(@NotNull BukkitHuskClaims plugin) {
        super(plugin);
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        setInspectorCallbacks();
    }

    @EventHandler
    public void onSignEdit(@NotNull SignChangeEvent e) {
        filterSign(
                handleSignEdit(SignWrite.create(
                        getPlugin().getOnlineUser(e.getPlayer()),
                        BukkitHuskClaims.Adapter.adapt(e.getBlock().getLocation()),
                        e.getSide() == Side.FRONT ? SignWrite.Type.SIGN_EDIT_FRONT : SignWrite.Type.SIGN_EDIT_BACK,
                        e.lines(),
                        plugin.getServerName()
                )), e
        );
    }

    // ---- Cushions (Minecraft 26.3) ----
    // Cushions are entities cloplib doesn't know about, so placing, breaking and sitting on them are checked
    // here: placing and breaking like decorations (paintings, item frames), sitting like using an entity.
    // They're recognised by name, so this does nothing on versions without cushions.

    private static boolean isCushion(@Nullable Entity entity) {
        return entity != null && ((Keyed) entity.getType()).getKey().getKey().contains("cushion");
    }

    private static boolean isCushionItem(@Nullable ItemStack item) {
        return item != null && ((Keyed) item.getType()).getKey().getKey().endsWith("_cushion");
    }

    @Nullable
    private static Player playerBehind(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // Whether the operation is stopped; HuskClaims tells the player why
    private boolean denied(@NotNull Player player, @NotNull OperationType type, @NotNull Location location) {
        return plugin.cancelOperation(Operation.of(
                plugin.getOnlineUser(player), type, BukkitHuskClaims.Adapter.adapt(location)
        ));
    }

    private void check(@NotNull Cancellable event, @Nullable Player player, @NotNull OperationType type,
                       @NotNull Location location) {
        if (player != null && denied(player, type, location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionPlaceClick(@NotNull PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !isCushionItem(e.getItem())) {
            return;
        }
        final Location target = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation();
        if (denied(e.getPlayer(), OperationType.PLACE_HANGING_ENTITY, target)) {
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionPlace(@NotNull EntityPlaceEvent e) {
        if (isCushion(e.getEntity())) {
            check(e, e.getPlayer(), OperationType.PLACE_HANGING_ENTITY, e.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionAttack(@NotNull PrePlayerAttackEntityEvent e) {
        if (isCushion(e.getAttacked())) {
            check(e, e.getPlayer(), OperationType.BREAK_HANGING_ENTITY, e.getAttacked().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionDamage(@NotNull EntityDamageByEntityEvent e) {
        if (isCushion(e.getEntity())) {
            check(e, playerBehind(e.getDamager()), OperationType.BREAK_HANGING_ENTITY, e.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionHangingBreak(@NotNull HangingBreakByEntityEvent e) {
        if (isCushion(e.getEntity())) {
            check(e, playerBehind(e.getRemover()), OperationType.BREAK_HANGING_ENTITY, e.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionVehicleDamage(@NotNull VehicleDamageEvent e) {
        if (isCushion(e.getVehicle())) {
            check(e, playerBehind(e.getAttacker()), OperationType.BREAK_HANGING_ENTITY, e.getVehicle().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionVehicleDestroy(@NotNull VehicleDestroyEvent e) {
        if (isCushion(e.getVehicle())) {
            check(e, playerBehind(e.getAttacker()), OperationType.BREAK_HANGING_ENTITY, e.getVehicle().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCushionSit(@NotNull PlayerInteractEntityEvent e) {
        if (isCushion(e.getRightClicked())) {
            check(e, e.getPlayer(), OperationType.ENTITY_INTERACT, e.getRightClicked().getLocation());
        }
    }

    // Apply filter edits to a sign if needed
    private void filterSign(@NotNull SignWrite write, @NotNull SignChangeEvent e) {
        if (write.getFilteredText() == null) {
            return;
        }
        for (int l = 0; l < e.lines().size(); l++) {
            e.line(l, Component.text(
                    write.getFilteredText().get(l),
                    Objects.requireNonNull(e.line(l)).color()
            ));
        }
    }

}
