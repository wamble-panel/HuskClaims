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
import net.william278.huskclaims.user.User;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lists a player's claims on this server
 */
public class ClaimListMenu extends Menu {

    private final User owner;
    private final long claimBlocks;
    private final int page;

    private ClaimListMenu(@NotNull MenuManager manager, @NotNull Player player, @NotNull User owner,
                          long claimBlocks, int page) {
        super(manager, player);
        this.owner = owner;
        this.claimBlocks = claimBlocks;
        this.page = page;
    }

    /**
     * Look up a player's claim blocks off the main thread, then show their claims
     */
    public static void show(@NotNull MenuManager manager, @NotNull Player player, @NotNull User owner) {
        manager.getPlugin().runAsync(() -> {
            long claimBlocks;
            try {
                claimBlocks = manager.getPlugin().getClaimBlocks(owner.getUuid());
            } catch (IllegalArgumentException e) {
                claimBlocks = -1;
            }
            new ClaimListMenu(manager, player, owner, claimBlocks, 0).show();
        });
    }

    @Override
    protected int getRows() {
        return 6;
    }

    @NotNull
    @Override
    protected Component getTitle() {
        final int pages = pageCount(getClaims().size());
        return items().title("gui_claims_title", MenuItems.escape(owner.getName()),
                Integer.toString(Math.min(page + 1, pages)), Integer.toString(pages));
    }

    @Override
    protected void build() {
        final List<ListedClaim> claims = getClaims();
        final List<ListedClaim> shown = page(claims, page);
        for (int i = 0; i < shown.size(); i++) {
            final ListedClaim listed = shown.get(i);
            final List<Component> lines = MenuItems.join(
                    ClaimMenu.getInfoLines(manager, user, listed.world(), listed.worldName(), listed.claim()),
                    items().lines("gui_claim_click")
            );
            set(i, items().item(manager.getWorldIcon(listed.worldName()), lines),
                    () -> new ClaimMenu(manager, player, listed.world(), listed.worldName(), listed.claim()).open());
        }

        final ItemStack info = items().head(owner.getUuid(), items().lines("gui_claims_info",
                MenuItems.escape(owner.getName()), Integer.toString(claims.size()),
                claimBlocks < 0 ? plugin.getLocales().getNotApplicable() : Long.toString(claimBlocks)));
        set(49, info);
        addPageButtons(page, claims.size(), (newPage) -> new ClaimListMenu(manager, player, owner, claimBlocks,
                newPage).open());
    }

    @NotNull
    private List<ListedClaim> getClaims() {
        final List<ListedClaim> claims = Lists.newArrayList();
        for (Map.Entry<String, ClaimWorld> entry : plugin.getClaimWorlds().entrySet()) {
            entry.getValue().getClaimsByUser(owner.getUuid())
                    .forEach(claim -> claims.add(new ListedClaim(entry.getKey(), entry.getValue(), claim)));
        }
        claims.sort(Comparator.comparing(ListedClaim::worldName)
                .thenComparingInt(c -> -c.claim().getRegion().getSurfaceArea()));
        return claims;
    }

    private record ListedClaim(@NotNull String worldName, @NotNull ClaimWorld world, @NotNull Claim claim) {
    }

}
