package ru.colliseum.siege;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class QueueLobby {
    public final String arena;
    public final Difficulty difficulty;
    public final Mode mode;
    public final long expiresAtMs;
    public final Set<UUID> players = new LinkedHashSet<>();

    public QueueLobby(String arena, Difficulty difficulty, Mode mode, long expiresAtMs, UUID host) {
        this.arena = arena;
        this.difficulty = difficulty;
        this.mode = mode;
        this.expiresAtMs = expiresAtMs;
        this.players.add(host);
    }
}
