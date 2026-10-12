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
import net.kyori.adventure.text.Component;
import net.william278.huskclaims.PaperHuskClaims;
import net.william278.huskclaims.user.BukkitUser;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest menu shown to one player
 */
public abstract class Menu implements InventoryHolder {

    // How long after an action to re-render, so the change (made off the main thread) shows
    private static final Duration REFRESH_DELAY = Duration.ofMillis(400);
    private static final long CLICK_COOLDOWN_MILLIS = 250;
    protected static final int PAGE_SIZE = 45;

    protected final MenuManager manager;
    protected final PaperHuskClaims plugin;
    protected final Player player;
    protected final BukkitUser user;
    private final Map<Integer, Consumer<ClickType>> actions = Maps.newHashMap();
    private Inventory inventory;
    private long lastClick;

    protected Menu(@NotNull MenuManager manager, @NotNull Player player) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.player = player;
        this.user = plugin.getOnlineUser(player);
    }

    protected abstract int getRows();

    @NotNull
    protected abstract Component getTitle();

    protected abstract void build();

    /**
     * Open (or re-render) this menu. Must be called on the player's thread
     */
    public void open() {
        actions.clear();
        this.inventory = plugin.getServer().createInventory(this, getRows() * 9, getTitle());
        build();
        player.openInventory(inventory);
        manager.setOpenMenu(player, this);
    }

    /**
     * Open this menu on the player's thread
     */
    public void show() {
        plugin.runSync(player, this::open);
    }

    /**
     * Open this menu shortly, after an action run off the main thread has applied
     */
    public void showLater() {
        later(this::open);
    }

    /**
     * Run something on the player's thread shortly, after an action run off the main thread has applied
     */
    protected void later(@NotNull Runnable runnable) {
        plugin.runSyncDelayed(() -> plugin.runSync(player, runnable), REFRESH_DELAY);
    }

    /**
     * Re-render this menu shortly, if the player still has it open
     */
    protected void refreshLater() {
        later(() -> {
            if (manager.isViewing(player, this)) {
                open();
            }
        });
    }

    protected void set(int slot, @NotNull ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void set(int slot, @NotNull ItemStack item, @NotNull Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    protected void set(int slot, @NotNull ItemStack item, @NotNull Runnable action) {
        set(slot, item, (click) -> action.run());
    }

    // Lay buttons out evenly along one row
    protected static int centered(int row, int index, int count) {
        final int start = (9 - Math.min(count, 9)) / 2;
        return row * 9 + start + index;
    }

    // Add previous / next page buttons to the bottom row
    protected void addPageButtons(int page, int itemCount, @NotNull Consumer<Integer> showPage) {
        final int bottom = (getRows() - 1) * 9;
        if (page > 0) {
            set(bottom, items().item("arrow", items().lines("gui_button_previous_page")),
                    () -> showPage.accept(page - 1));
        }
        if ((page + 1) * PAGE_SIZE < itemCount) {
            set(bottom + 8, items().item("arrow", items().lines("gui_button_next_page")),
                    () -> showPage.accept(page + 1));
        }
    }

    protected static int pageCount(int itemCount) {
        return Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @NotNull
    protected static <T> List<T> page(@NotNull List<T> list, int page) {
        final int from = Math.min(page * PAGE_SIZE, list.size());
        return list.subList(from, Math.min(from + PAGE_SIZE, list.size()));
    }

    void click(int slot, @NotNull ClickType click) {
        final Consumer<ClickType> action = actions.get(slot);
        if (action == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastClick < CLICK_COOLDOWN_MILLIS) {
            return;
        }
        lastClick = now;
        action.accept(click);
    }

    @NotNull
    protected MenuItems items() {
        return manager.getItems();
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

}
