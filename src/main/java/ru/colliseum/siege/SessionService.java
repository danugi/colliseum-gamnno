package ru.colliseum.siege;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
        public int currentWaveTotalMobs = 0;
        public final Map<UUID, Long> boundaryPushCooldownMs = new HashMap<>();
        public ServerBossBar waveBossBar;
        public int boundaryVisualTick = 0;
        public int spiderAggroTick = 0;

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

    public boolean declineLobby(String arena, UUID playerId) {
        QueueLobby q = lobbies.get(arena.toLowerCase());
        if (q == null) return false;
        boolean removed = q.players.remove(playerId);
        if (q.players.isEmpty()) lobbies.remove(arena.toLowerCase());
        return removed;
    }

    public int declineAll(UUID playerId) {
        int removedFrom = 0;
        List<String> toDelete = new ArrayList<>();
        for (var e : lobbies.entrySet()) {
            if (e.getValue().players.remove(playerId)) removedFrom++;
            if (e.getValue().players.isEmpty()) toDelete.add(e.getKey());
        }
        toDelete.forEach(lobbies::remove);
        return removedFrom;
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
                if (!s.arena.isInside(world, new Vec3d(p.getX(), p.getY(), p.getZ()))) {
                    pushPlayerBackToArena(s, p);
                }
            }

            if (isPartyWiped(server, world, s)) {
                finishDefeat(server, s, "Вся группа повержена");
                continue;
            }

            s.currentWaveMobs.removeIf(id -> {
                var ent = world.getEntity(id);
                return ent == null || !ent.isAlive();
            });
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
                if (ent != null) { ent.setOnFire(false); ent.setFireTicks(0); }
            }

            updateWaveBossBar(s);
            showBoundaryVisual(world, s);
            updateSpiderAggro(world, s);
        }
    }

    public void start(MinecraftServer server, ActiveSession s) {
        ServerWorld world = server.getWorld(s.arena.worldKey());
        if (world == null) return;
        s.started = true;
        s.waveBossBar = new ServerBossBar(Text.literal("§eВолна 0/" + s.difficulty.waves()), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        s.waveBossBar.setPercent(0.0f);
        for (UUID id : s.participants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            p.teleport(world, s.arena.entry.x, s.arena.entry.y, s.arena.entry.z, java.util.Set.of(), p.getYaw(), p.getPitch(), true);
            s.waveBossBar.addPlayer(p);
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
        int totalWaves = s.difficulty.waves();
        broadcast(world.getServer(), s.participants, "§6Волна " + s.wave + "/" + totalWaves);

        if (s.wave == s.difficulty.waves()) {
            LivingEntity boss = spawnBoss(world, s);
            s.bossId = boss.getUuid();
            s.currentWaveTotalMobs = 1;
            s.currentWaveMobs.add(boss.getUuid());
            if (s.waveBossBar != null) s.waveBossBar.setColor(BossBar.Color.RED);
            updateWaveBossBar(s);
            showBoundaryVisual(world, s);
            updateSpiderAggro(world, s);
            return;
        }

        int players = s.participants.size();
        int count = Math.max(4, (int)Math.round((5 + s.wave * 0.55) * (1 + 0.40 * (players - 1))));
        s.currentWaveTotalMobs = count;

        int spawned = 0;
        while (spawned < count) {
            Vec3d basePos = s.arena.spawns.get(spawned % s.arena.spawns.size());
            int burst = Math.min(1 + world.random.nextInt(2), count - spawned);
            for (int j = 0; j < burst; j++) {
                LivingEntity mob = createWaveMob(world, s);
                double ox = (world.random.nextDouble() - 0.5) * 1.8;
                double oz = (world.random.nextDouble() - 0.5) * 1.8;
                mob.refreshPositionAndAngles(basePos.x + ox, basePos.y, basePos.z + oz, world.random.nextFloat() * 360f, 0);
                applyScaling(mob, players, s.difficulty);
                applySunProtection(mob);
                configureMobBehavior(mob, world, s);
                applyDifficultyGear(mob, world, s.difficulty);
                world.spawnEntity(mob);
                s.currentWaveMobs.add(mob.getUuid());
                spawned++;
            }
        }
        if (s.waveBossBar != null) s.waveBossBar.setColor(BossBar.Color.YELLOW);
        updateWaveBossBar(s);
    }

    private LivingEntity spawnBoss(ServerWorld world, ActiveSession s) {
        var boss = new ZombieEntity(EntityType.ZOMBIE, world);
        Vec3d pos = s.arena.center;
        boss.refreshPositionAndAngles(pos.x, pos.y + 1, pos.z, 0, 0);
        boss.setCustomName(Text.literal("§4" + s.difficulty.finalBossName()));
        boss.setCustomNameVisible(true);
        applyScaling(boss, s.participants.size() + 2, s.difficulty);
        boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
        applyDifficultyGear(boss, world, s.difficulty);
        applySunProtection(boss);
        world.spawnEntity(boss);
        broadcast(world.getServer(), s.participants, "§cФинальный босс: " + s.difficulty.finalBossName());
        return boss;
    }


    private LivingEntity createWaveMob(ServerWorld world, ActiveSession s) {
        int roll = world.random.nextInt(100);
        return switch (s.difficulty) {
            case EASY -> roll < 70 ? new ZombieEntity(EntityType.ZOMBIE, world) : new SpiderEntity(EntityType.SPIDER, world);
            case MEDIUM -> {
                if (roll < 45) yield new ZombieEntity(EntityType.ZOMBIE, world);
                if (roll < 75) yield new SkeletonEntity(EntityType.SKELETON, world);
                yield new SpiderEntity(EntityType.SPIDER, world);
            }
            case HARD -> {
                if (roll < 35) yield new ZombieEntity(EntityType.ZOMBIE, world);
                if (roll < 60) yield new HuskEntity(EntityType.HUSK, world);
                if (roll < 82) yield new SkeletonEntity(EntityType.SKELETON, world);
                yield new SpiderEntity(EntityType.SPIDER, world);
            }
            case HARDCORE -> {
                if (roll < 28) yield new HuskEntity(EntityType.HUSK, world);
                if (roll < 52) yield new SkeletonEntity(EntityType.SKELETON, world);
                if (roll < 74) yield new StrayEntity(EntityType.STRAY, world);
                yield new SpiderEntity(EntityType.SPIDER, world);
            }
        };
    }


    private void configureMobBehavior(LivingEntity mob, ServerWorld world, ActiveSession s) {
        if (mob instanceof SkeletonEntity skeleton) {
            skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            skeleton.setCanPickUpLoot(false);
        }
        if (mob instanceof StrayEntity stray) {
            stray.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            stray.setCanPickUpLoot(false);
        }
        if (mob instanceof SpiderEntity spider) {
            ServerPlayerEntity target = pickAggroTarget(world, s);
            if (target != null) spider.setTarget(target);
        }
    }

    private ServerPlayerEntity pickAggroTarget(ServerWorld world, ActiveSession s) {
        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID id : s.participants) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
            if (p != null && p.isAlive() && p.getEntityWorld() == world) online.add(p);
        }
        if (online.isEmpty()) return null;
        return online.get(world.random.nextInt(online.size()));
    }

    private void updateSpiderAggro(ServerWorld world, ActiveSession s) {
        s.spiderAggroTick++;
        if (s.spiderAggroTick % 10 != 0) return;

        for (UUID id : s.currentWaveMobs) {
            var ent = world.getEntity(id);
            if (!(ent instanceof SpiderEntity spider) || !spider.isAlive()) continue;
            if (spider.getTarget() == null || !spider.getTarget().isAlive()) {
                ServerPlayerEntity target = pickAggroTarget(world, s);
                if (target != null) spider.setTarget(target);
            }
        }
    }

    private boolean isPartyWiped(MinecraftServer server, ServerWorld world, ActiveSession s) {
        int alive = 0;
        for (UUID id : s.participants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            if (p.getEntityWorld() != world) continue;
            if (p.isSpectator()) continue;
            if (!p.isAlive()) continue;
            alive++;
        }
        return alive == 0;
    }

    private void applyDifficultyGear(LivingEntity mob, ServerWorld world, Difficulty difficulty) {
        if (difficulty == Difficulty.EASY || difficulty == Difficulty.MEDIUM) return;

        int roll = world.random.nextInt(100);
        if (mob instanceof ZombieEntity || mob instanceof HuskEntity) {
            if (roll < (difficulty == Difficulty.HARDCORE ? 55 : 40)) {
                mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            if (roll < (difficulty == Difficulty.HARDCORE ? 45 : 30)) {
                mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            }
            if (roll < (difficulty == Difficulty.HARDCORE ? 35 : 20)) {
                mob.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            }
        }

        if (difficulty == Difficulty.HARDCORE && mob instanceof SkeletonEntity) {
            if (roll < 30) mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            if (roll < 20) mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        }
    }

    private void applySunProtection(LivingEntity mob) {
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 60 * 30, 0, false, false));
        mob.setOnFire(false);
        mob.setFireTicks(0);
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
        clearWaveBossBar(s);
        cleanupSessionEntities(server, s);
        for (UUID id : s.participants) cooldownService.applyLoseCooldown(id, s.difficulty);
        restorePlayers(server, s, false, reason);
        sessions.remove(s.arena.name.toLowerCase());
    }

    public void finishVictory(MinecraftServer server, ActiveSession s) {
        if (s.finished) return;
        s.finished = true;
        clearWaveBossBar(s);
        cleanupSessionEntities(server, s);
        for (UUID id : s.participants) cooldownService.applyWinCooldown(id, s.difficulty);
        for (UUID id : s.eligible) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) rewardService.giveVictoryRewards(p, s.difficulty, server);
        }
        restorePlayers(server, s, true, "Победа!");
        sessions.remove(s.arena.name.toLowerCase());
    }


    private void cleanupSessionEntities(MinecraftServer server, ActiveSession s) {
        ServerWorld world = server.getWorld(s.arena.worldKey());
        if (world == null) return;

        for (UUID id : new HashSet<>(s.currentWaveMobs)) {
            var ent = world.getEntity(id);
            if (ent != null && ent.isAlive()) ent.discard();
        }
        s.currentWaveMobs.clear();

        if (s.bossId != null) {
            var boss = world.getEntity(s.bossId);
            if (boss != null && boss.isAlive()) boss.discard();
            s.bossId = null;
        }
    }

    private void restorePlayers(MinecraftServer server, ActiveSession s, boolean win, String reason) {
        ServerWorld world = server.getWorld(s.arena.worldKey());
        for (UUID id : s.participants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || world == null) continue;
            if (s.mode == Mode.ELITE && s.eliteSnapshots.containsKey(id)) s.eliteSnapshots.get(id).restore(p);
            p.teleport(world, s.arena.exit.x, s.arena.exit.y, s.arena.exit.z, java.util.Set.of(), p.getYaw(), p.getPitch(), true);
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
        p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        p.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        p.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        p.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    private void broadcast(MinecraftServer server, Set<UUID> ids, String msg) {
        for (UUID id : ids) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) p.sendMessage(Text.literal(msg), false);
        }
    }

    private void pushPlayerBackToArena(ActiveSession s, ServerPlayerEntity player) {
        Vec3d center = s.arena.center;
        double dx = player.getX() - center.x;
        double dz = player.getZ() - center.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.0001) return;

        double safeRadius = Math.max(1.0, s.arena.radius - 0.6);
        if (distance > safeRadius) {
            double nx = dx / distance;
            double nz = dz / distance;
            double clampedX = center.x + nx * safeRadius;
            double clampedZ = center.z + nz * safeRadius;
            player.teleport((ServerWorld) player.getEntityWorld(), clampedX, player.getY(), clampedZ, java.util.Set.of(), player.getYaw(), player.getPitch(), true);
        }

        Vec3d fromPlayerToCenter = new Vec3d(center.x - player.getX(), 0, center.z - player.getZ());
        if (fromPlayerToCenter.lengthSquared() > 0.0001) {
            Vec3d knockback = fromPlayerToCenter.normalize().multiply(1.35).add(0, 0.30, 0);
            player.setVelocity(knockback.x, knockback.y, knockback.z);
        }

        long now = System.currentTimeMillis();
        long lastWarn = s.boundaryPushCooldownMs.getOrDefault(player.getUuid(), 0L);
        if (now - lastWarn > 1200) {
            player.sendMessage(Text.literal("§cНельзя выходить за границы арены!"), true);
            s.boundaryPushCooldownMs.put(player.getUuid(), now);
        }
    }


    private void showBoundaryVisual(ServerWorld world, ActiveSession s) {
        s.boundaryVisualTick++;
        if (s.boundaryVisualTick % 20 != 0) return;

        float y = (float) (s.arena.center.y + 1.0);
        float radius = (float) s.arena.radius;
        if (radius <= 0.5f) return;

        int points = Math.max(24, Math.min(72, (int) (radius * 3.2)));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = s.arena.center.x + Math.cos(angle) * radius;
            double z = s.arena.center.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    private void updateWaveBossBar(ActiveSession s) {
        if (s.waveBossBar == null) return;
        int totalWaves = s.difficulty.waves();
        s.waveBossBar.setName(Text.literal("§eВолна " + s.wave + "/" + totalWaves));

        if (s.currentWaveTotalMobs <= 0) {
            s.waveBossBar.setPercent(0.0f);
            return;
        }

        float aliveRatio = Math.max(0.0f, Math.min(1.0f, (float) s.currentWaveMobs.size() / (float) s.currentWaveTotalMobs));
        float completedCurrentWave = 1.0f - aliveRatio;
        float progress = ((s.wave - 1) + completedCurrentWave) / (float) totalWaves;
        s.waveBossBar.setPercent(Math.max(0.0f, Math.min(1.0f, progress)));
    }

    private void clearWaveBossBar(ActiveSession s) {
        if (s.waveBossBar != null) {
            s.waveBossBar.clearPlayers();
            s.waveBossBar = null;
        }
    }
}
