package ru.colliseum.siege;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {

    private final Map<Difficulty, Duration> winCooldowns = new EnumMap<>(Difficulty.class);
    private final Map<Difficulty, Duration> loseCooldowns = new EnumMap<>(Difficulty.class);
    private final Map<UUID, Instant> cooldownByPlayer = new ConcurrentHashMap<>();

    public CooldownService(Map<Difficulty, Duration> winCooldowns, Map<Difficulty, Duration> loseCooldowns) {
        this.winCooldowns.putAll(winCooldowns);
        this.loseCooldowns.putAll(loseCooldowns);
    }

    public Instant getCooldownEnd(UUID playerId) {
        return cooldownByPlayer.get(playerId);
    }

    public Duration getRemaining(UUID playerId) {
        Instant until = cooldownByPlayer.get(playerId);
        if (until == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), until);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean isOnCooldown(UUID playerId) {
        return !getRemaining(playerId).isZero();
    }

    public void applyWinCooldown(UUID playerId, Difficulty difficulty) {
        apply(playerId, winCooldowns.getOrDefault(difficulty, Duration.ZERO));
    }

    public void applyLoseCooldown(UUID playerId, Difficulty difficulty) {
        apply(playerId, loseCooldowns.getOrDefault(difficulty, Duration.ZERO));
    }

    public void clearCooldown(UUID playerId) {
        cooldownByPlayer.remove(playerId);
    }

    private void apply(UUID playerId, Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            cooldownByPlayer.remove(playerId);
            return;
        }
        cooldownByPlayer.put(playerId, Instant.now().plus(duration));
    }
}
