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

package net.william278.huskclaims.command;

import net.william278.huskclaims.PaperHuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.menu.ClaimListMenu;
import net.william278.huskclaims.menu.ClaimMenu;
import net.william278.huskclaims.menu.MenuManager;
import net.william278.huskclaims.user.BukkitUser;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.SavedUser;
import net.william278.huskclaims.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opens the claim menus: the claim you're standing in (if you can manage it), otherwise your claims list
 */
public class ClaimMenuCommand extends OnlineUserCommand implements UserListTabCompletable {

    private final PaperHuskClaims paper;

    public ClaimMenuCommand(@NotNull PaperHuskClaims plugin) {
        super(List.of("claimmenu", "claimgui", "cmenu"), "[player]", plugin);
        this.paper = plugin;
        addAdditionalPermissions(Map.of("other", true));
    }

    @Override
    public void execute(@NotNull OnlineUser executor, @NotNull String[] args) {
        final MenuManager menus = paper.getMenuManager();
        if (menus == null) {
            return;
        }
        final Player player = ((BukkitUser) executor).getBukkitPlayer();

        // Another player's claims
        final Optional<String> name = parseStringArg(args, 0);
        if (name.isPresent()) {
            if (!hasPermission(executor, "other")) {
                plugin.getLocales().getLocale("error_no_permission")
                        .ifPresent(executor::sendMessage);
                return;
            }
            final Optional<User> user = plugin.getDatabase().getUser(name.get()).map(SavedUser::getUser);
            if (user.isEmpty()) {
                plugin.getLocales().getLocale("error_invalid_user", name.get())
                        .ifPresent(executor::sendMessage);
                return;
            }
            ClaimListMenu.show(menus, player, user.get());
            return;
        }

        // The claim they're standing in, if they can manage it
        final String worldName = executor.getWorld().getName();
        final Optional<ClaimWorld> world = plugin.getClaimWorld(worldName);
        final Optional<Claim> claim = world.flatMap(w -> w.getClaimAt(executor.getPosition()));
        if (world.isPresent() && claim.isPresent() && menus.canManage(executor, claim.get())) {
            new ClaimMenu(menus, player, world.get(), worldName, claim.get()).show();
            return;
        }
        ClaimListMenu.show(menus, player, executor);
    }

}
