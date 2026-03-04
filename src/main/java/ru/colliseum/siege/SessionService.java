package ru.colliseum.siege;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public final class SessionService {

    public static final class ActiveSession {
        public final ArenaData arena;
        public final Difficulty difficulty;
        public final Mode mode;
        public final Set<UUID> participants;
        public final Set<UUID> eligible;
        public final Map<UUID, EliteInventorySnapshot> eliteSnapshots = new HashMap<>();
        public int wave = 0;
        public UUID bossId;
        public boolean started;
        public boolean finished;
        public final Set<UUID> currentWaveMobs = new HashSet<>();

        ActiveSession(ArenaData arena, Difficulty difficulty, Mode mode, Set<UUID> participants) {
            this.arena = arena;
            this.difficulty = difficulty;
            this.mode = mode;
            this.participants = new HashSet<>(participants);
            this.eligible = new HashSet<>(participants);
        }
    }

    private final Map<String, QueueLobby> lobbies = new HashMap<>();
    private final Map<String, ActiveSession> sessions = new HashMap<>();
    private final CooldownService cooldownService;
    private final RewardService rewardService;

    public SessionService(CooldownService cooldownService, RewardService rewardService) {
        this.cooldownService = cooldownService;
        this.rewardService = rewardService;
    }

    public QueueLobby openQueue(String arena, Difficulty d, Mode mode, UUID host, int seconds) {
        QueueLobby q = new QueueLobby(arena, d, mode, System.currentTimeMillis() + seconds * 1000L, host);
        lobbies.put(arena.toLowerCase(), q);
        return q;
    }

    public QueueLobby getLobby(String arena) { return lobbies.get(arena.toLowerCase()); }

    public boolean joinLobby(String arena, UUID playerId, int maxPlayers) {
        QueueLobby q = lobbies.get(arena.toLowerCase());
        if (q == null || System.currentTimeMillis() > q.expiresAtMs) return false;
        if (q.players.size() >= maxPlayers) return false;
        q.players.add(playerId);
        return true;
    }

    public ActiveSession startFromLobby(MinecraftServer server, ArenaData arena) {
        QueueLobby q = lobbies.remove(arena.name.toLowerCase());
        if (q == null) return null;
        ActiveSession s = new ActiveSession(arena, q.difficulty, q.mode, q.players);
        sessions.put(arena.name.toLowerCase(), s);
        start(server, s);
        return s;
    }


    public Set<String> expiredLobbyKeys() {
        long now = System.currentTimeMillis();
        Set<String> out = new HashSet<>();
        for (var e : lobbies.entrySet()) if (now > e.getValue().expiresAtMs) out.add(e.getKey());
        return out;
    }

    public QueueLobby firstOpenLobby() {
        long now = System.currentTimeMillis();
        for (QueueLobby q : lobbies.values()) if (q.expiresAtMs > now) return q;
        return null;
    }

    public ActiveSession getByPlayer(UUID id) {
        return sessions.values().stream().filter(s -> !s.finished && s.participants.contains(id)).findFirst().orElse(null);
    }

    public void tick(MinecraftServer server) {
        for (ActiveSession s : sessions.values()) {
            if (s.finished || !s.started) continue;
            ServerWorld world = server.getWorld(s.arena.worldKey());
            if (world == null) continue;

            for (UUID pid : s.participants) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(pid);
                if (p == null) continue;
                if (!s.arena.isInside(world, p.getPos())) p.teleport(world, s.arena.center.x, s.arena.center.y, s.arena.center.z, Set.of(), p.getYaw(), p.getPitch(), true);
            }

            s.currentWaveMobs.removeIf(id -> world.getEntity(id) == null || !world.getEntity(id).isAlive());
            if (s.bossId != null) {
                var boss = world.getEntity(s.bossId);
                if (boss == null || !boss.isAlive()) {
                    finishVictory(server, s);
                }
            } else if (s.currentWaveMobs.isEmpty()) {
                nextWave(world, s);
            }

            for (UUID id : s.currentWaveMobs) {
                var ent = world.getEntity(id);
                if (ent != null) ent.setOnFire(false);
            }
        }
    }

    public void start(MinecraftServer server, ActiveSession s) {
        ServerWorld world = server.getWorld(s.arena.worldKey());
        if (world == null) return;
        s.started = true;
        for (UUID id : s.participants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            p.teleport(world, s.arena.entry.x, s.arena.entry.y, s.arena.entry.z, Set.of(), p.getYaw(), p.getPitch(), true);
            if (s.mode == Mode.ELITE) {
                s.eliteSnapshots.put(id, EliteInventorySnapshot.capture(p));
                giveEliteKit(p);
            }
            p.sendMessage(Text.literal("§eСессия началась: " + s.difficulty + " / " + s.mode), false);
        }
        nextWave(world, s);
    }

    private void nextWave(ServerWorld world, ActiveSession s) {
        s.wave++;
        if (s.wave > s.difficulty.waves()) return;
        if (s.wave == s.difficulty.waves()) {
            LivingEntity boss = spawnBoss(world, s);
            s.bossId = boss.getUuid();
            s.currentWaveMobs.add(boss.getUuid());
            return;
        }

        int players = s.participants.size();
        int count = Math.max(3, (int)Math.round((4 + s.wave * 0.35) * (1 + 0.35 * (players - 1))));
        for (int i = 0; i < count; i++) {
            Vec3d pos = s.arena.spawns.get(i % s.arena.spawns.size());
            ZombieEntity z = new ZombieEntity(EntityType.ZOMBIE, world);
            z.refreshPositionAndAngles(pos.x, pos.y, pos.z, world.random.nextFloat() * 360f, 0);
            int babyPct = (s.difficulty == Difficulty.HARD || s.difficulty == Difficulty.HARDCORE) ? 40 : 30;
            z.setBaby(world.random.nextInt(100) < babyPct);
            applyScaling(z, players, s.difficulty);
            world.spawnEntity(z);
            s.currentWaveMobs.add(z.getUuid());
        }
    }

    private LivingEntity spawnBoss(ServerWorld world, ActiveSession s) {
        var boss = new ZombieEntity(EntityType.ZOMBIE, world);
        Vec3d pos = s.arena.center;
        boss.refreshPositionAndAngles(pos.x, pos.y + 1, pos.z, 0, 0);
        boss.setCustomName(Text.literal("§4" + s.difficulty.finalBossName()));
        boss.setCustomNameVisible(true);
        applyScaling(boss, s.participants.size() + 2, s.difficulty);
        boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
        world.spawnEntity(boss);
        broadcast(world.getServer(), s.participants, "§cФинальный босс: " + s.difficulty.finalBossName());
        return boss;
    }

    private void applyScaling(HostileEntity mob, int players, Difficulty d) {
        applyScaling((LivingEntity) mob, players, d);
    }

    private void applyScaling(LivingEntity mob, int players, Difficulty d) {
        double hpMul = (1 + 0.12 * (players - 1));
        double dmgMul = (1 + 0.08 * (players - 1));
        if (d == Difficulty.HARDCORE) {
            hpMul *= 1.10;
            dmgMul *= 1.15;
        }
        double hp = mob.getMaxHealth() * hpMul;
        mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(hp);
        mob.setHealth((float) hp);
        var attack = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(attack.getBaseValue() * dmgMul);
    }

    public void playerQuitBattle(MinecraftServer server, UUID playerId, String name) {
        ActiveSession s = getByPlayer(playerId);
        if (s == null || s.finished) return;
        s.eligible.remove(playerId);
        finishDefeat(server, s, "Игрок вышел во время боя: " + name);
    }

    public void finishDefeat(MinecraftServer server, ActiveSession s, String reason) {
        if (s.finished) return;
        s.finished = true;
        for (UUID id : s.participants) cooldownService.applyLoseCooldown(id, s.difficulty);
        restorePlayers(server, s, false, reason);
    }

    public void finishVictory(MinecraftServer server, ActiveSession s) {
        if (s.finished) return;
        s.finished = true;
        for (UUID id : s.participants) cooldownService.applyWinCooldown(id, s.difficulty);
        for (UUID id : s.eligible) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) rewardService.giveVictoryRewards(p, s.difficulty);
        }
        restorePlayers(server, s, true, "Победа!");
    }

    private void restorePlayers(MinecraftServer server, ActiveSession s, boolean win, String reason) {
        ServerWorld world = server.getWorld(s.arena.worldKey());
        for (UUID id : s.participants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || world == null) continue;
            if (s.mode == Mode.ELITE && s.eliteSnapshots.containsKey(id)) s.eliteSnapshots.get(id).restore(p);
            p.teleport(world, s.arena.exit.x, s.arena.exit.y, s.arena.exit.z, Set.of(), p.getYaw(), p.getPitch(), true);
            if (win) p.sendMessage(Text.literal("§a" + reason), false);
            else p.sendMessage(Text.literal("§c" + reason + ". Наград нет."), false);
        }
    }

    private void giveEliteKit(ServerPlayerEntity p) {
        p.getInventory().clear();
        p.getInventory().insertStack(new ItemStack(Items.NETHERITE_SWORD));
        p.getInventory().insertStack(new ItemStack(Items.BOW));
        p.getInventory().insertStack(new ItemStack(Items.ARROW, 64));
        p.getInventory().insertStack(new ItemStack(Items.GOLDEN_CARROT, 16));
        p.getInventory().armor.set(3, new ItemStack(Items.NETHERITE_HELMET));
        p.getInventory().armor.set(2, new ItemStack(Items.NETHERITE_CHESTPLATE));
        p.getInventory().armor.set(1, new ItemStack(Items.NETHERITE_LEGGINGS));
        p.getInventory().armor.set(0, new ItemStack(Items.NETHERITE_BOOTS));
    }

    private void broadcast(MinecraftServer server, Set<UUID> ids, String msg) {
        for (UUID id : ids) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) p.sendMessage(Text.literal(msg), false);
        }
    }
}
