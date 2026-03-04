package ru.colliseum.siege;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ArenaSession {
    private final String arenaName;
    private final Difficulty difficulty;
    private final Mode mode;
    private final Set<UUID> participants;
    private final Set<UUID> eligibleForRewards;
    private int currentWave = 1;
    private boolean finished;

    public ArenaSession(String arenaName, Difficulty difficulty, Mode mode, Set<UUID> participants) {
        this.arenaName = arenaName;
        this.difficulty = difficulty;
        this.mode = mode;
        this.participants = new HashSet<>(participants);
        this.eligibleForRewards = new HashSet<>(participants);
    }

    public String arenaName() {
        return arenaName;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public Mode mode() {
        return mode;
    }

    public int currentWave() {
        return currentWave;
    }

    public void nextWave() {
        if (!finished && currentWave < difficulty.waves()) {
            currentWave++;
        }
    }

    public boolean isFinalWave() {
        return currentWave == difficulty.waves();
    }

    public void markLeaver(UUID playerId) {
        eligibleForRewards.remove(playerId);
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public Set<UUID> eligibleForRewards() {
        return Collections.unmodifiableSet(eligibleForRewards);
    }

    public boolean isFinished() {
        return finished;
    }

    public void finish() {
        this.finished = true;
    }
}
