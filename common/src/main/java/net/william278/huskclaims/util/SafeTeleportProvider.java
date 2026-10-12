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

package net.william278.huskclaims.util;

import com.google.common.collect.Lists;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public interface SafeTeleportProvider {

    default void teleportOutOfClaim(@NotNull OnlineUser user, boolean instant) {
        // Leave the whole claim, not just the subdivision the user is standing in
        final Optional<Region> region = getPlugin().getClaimAt(user.getPosition())
                .map(claim -> claim.getParent().orElse(claim))
                .map(Claim::getRegion);
        if (region.isEmpty()) {
            return;
        }
        final List<Position> positions = this.getPotentialPositions(user.getPosition(), region.get());
        this.teleportOutOfClaim(user, positions, 0, instant);
    }

    // Try each position in turn until a safe one is found
    private void teleportOutOfClaim(@NotNull OnlineUser user, @NotNull List<Position> positions,
                                    int index, boolean instant) {
        if (index >= positions.size()) {
            getPlugin().log(Level.WARNING, "Failed to safely teleport %s after %s attempts"
                    .formatted(user.getName(), positions.size()));
            return;
        }
        findSafePosition(positions.get(index)).thenAccept(found -> found.ifPresentOrElse(
                (safe) -> getPlugin().runSync(() -> user.teleport(safe, instant)),
                () -> teleportOutOfClaim(user, positions, index + 1, instant)
        ));
    }

    // Spots just outside each edge of the claim, unclaimed ones first, then nearest to the user first
    @NotNull
    @Unmodifiable
    private List<Position> getPotentialPositions(@NotNull Position position, @NotNull Region region) {
        final World world = position.getWorld();
        final List<Position> positions = Lists.newArrayList();
        for (int i : new int[]{1, 2, 3, 8, 16}) {
            positions.addAll(List.of(
                    Position.at(region.getNearCorner().getBlockX() - i, 64, position.getBlockZ(), world),
                    Position.at(region.getFarCorner().getBlockX() + i, 64, position.getBlockZ(), world),
                    Position.at(position.getBlockX(), 64, region.getNearCorner().getBlockZ() - i, world),
                    Position.at(position.getBlockX(), 64, region.getFarCorner().getBlockZ() + i, world)
            ));
        }
        positions.sort(Comparator.comparing((Position p) -> getPlugin().getClaimAt(p).isPresent())
                .thenComparingLong(p -> p.distanceFrom(position)));
        return List.copyOf(positions);
    }

    @NotNull
    CompletableFuture<Optional<Position>> findSafePosition(@NotNull Position position);

    @NotNull
    HuskClaims getPlugin();

}
