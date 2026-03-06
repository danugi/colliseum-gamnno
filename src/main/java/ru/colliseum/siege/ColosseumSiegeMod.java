package ru.colliseum.siege;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

public final class ColosseumSiegeMod implements ModInitializer {

    private final ArenaRepository arenaRepo = new ArenaRepository();
    private final Map<UUID, ArenaData> editingArena = new HashMap<>();

    private CooldownService cooldownService;
    private SessionService sessionService;

    @Override
    public void onInitialize() {
        arenaRepo.load();
        Map<Difficulty, Duration> winCd = new EnumMap<>(Difficulty.class);
        Map<Difficulty, Duration> loseCd = new EnumMap<>(Difficulty.class);
        winCd.put(Difficulty.EASY, Duration.ofMinutes(5)); loseCd.put(Difficulty.EASY, Duration.ofMinutes(3));
        winCd.put(Difficulty.MEDIUM, Duration.ofMinutes(10)); loseCd.put(Difficulty.MEDIUM, Duration.ofMinutes(5));
        winCd.put(Difficulty.HARD, Duration.ofMinutes(20)); loseCd.put(Difficulty.HARD, Duration.ofMinutes(10));
        winCd.put(Difficulty.HARDCORE, Duration.ofMinutes(60)); loseCd.put(Difficulty.HARDCORE, Duration.ofMinutes(30));

        cooldownService = new CooldownService(winCd, loseCd);
        sessionService = new SessionService(cooldownService, new RewardService());

        registerCommands();
        registerEvents();
    }

    private void registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                sessionService.playerQuitBattle(server, handler.player.getUuid(), handler.player.getName().getString()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (String key : sessionService.expiredLobbyKeys()) {
                ArenaData arena = arenaRepo.get(key);
                if (arena != null) sessionService.startFromLobby(server, arena);
            }
            sessionService.tick(server);
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                sessionService.getByPlayer(player.getUuid()) == null);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            var s = sessionService.getByPlayer(sp.getUuid());
            if (s == null) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BlockItem) return ActionResult.FAIL;
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack weapon = sp.getMainHandStack();
            if (RewardService.hasSeal(weapon) && entity instanceof net.minecraft.entity.mob.MobEntity) {
                if (world instanceof ServerWorld sw) entity.damage(sw, world.getDamageSources().playerAttack(player), 1.0f);
            }
            return ActionResult.PASS;
        });


    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
                CommandManager.literal("colosseum")
                        .requires(src -> src.getEntity() instanceof ServerPlayerEntity && ModPermissions.hasUse(src))
                        .then(CommandManager.literal("queue")
                                .then(CommandManager.argument("arena", StringArgumentType.word())
                                        .executes(ctx -> queue(ctx, "easy", "classic"))
                                        .then(CommandManager.argument("difficulty", StringArgumentType.word())
                                                .executes(ctx -> queue(ctx, StringArgumentType.getString(ctx, "difficulty"), "classic"))
                                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                                        .executes(ctx -> queue(ctx,
                                                                StringArgumentType.getString(ctx, "difficulty"),
                                                                StringArgumentType.getString(ctx, "mode")))))))
                        .then(CommandManager.literal("join")
                                .executes(this::joinAnyQueue)
                                .then(CommandManager.argument("arena", StringArgumentType.word())
                                        .executes(this::joinQueue)))
                        .then(CommandManager.literal("decline")
                                .executes(this::declineAnyQueue)
                                .then(CommandManager.argument("arena", StringArgumentType.word())
                                        .executes(this::declineQueue)))
                        .then(CommandManager.literal("arenas").executes(this::listArenas))
                        .then(CommandManager.literal("edit").requires(ModPermissions::hasAdmin)
                                .then(CommandManager.argument("arena", StringArgumentType.word()).executes(this::adminEditArena)))
                        .then(CommandManager.literal("setcenter").requires(ModPermissions::hasAdmin).executes(this::setCenter))
                        .then(CommandManager.literal("setradius").requires(ModPermissions::hasAdmin)
                                .then(CommandManager.argument("r", IntegerArgumentType.integer(5, 256)).executes(this::setRadius)))
                        .then(CommandManager.literal("addspawn").requires(ModPermissions::hasAdmin).executes(this::addSpawn))
                        .then(CommandManager.literal("setentry").requires(ModPermissions::hasAdmin).executes(this::setEntry))
                        .then(CommandManager.literal("setexit").requires(ModPermissions::hasAdmin).executes(this::setExit))
                        .then(CommandManager.literal("setlobby").requires(ModPermissions::hasAdmin).executes(this::setLobby))
                        .then(CommandManager.literal("setmaxplayers").requires(ModPermissions::hasAdmin)
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(1, 5)).executes(this::setMaxPlayers)))
                        .then(CommandManager.literal("save").requires(ModPermissions::hasAdmin)
                                .then(CommandManager.argument("name", StringArgumentType.word()).executes(this::saveArena)))
                        .then(CommandManager.literal("win").requires(ModPermissions::hasAdmin).executes(this::win))
                        .then(CommandManager.literal("lose").requires(ModPermissions::hasAdmin).executes(this::lose))
                        .then(CommandManager.literal("clearcd").requires(ModPermissions::hasAdmin)
                                .executes(this::clearCooldownSelf)
                                .then(CommandManager.argument("player", StringArgumentType.word()).executes(this::clearCooldownForPlayer)))
        ));
    }

    private int queue(CommandContext<ServerCommandSource> ctx, String difficultyRaw, String modeRaw) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); if (p == null) return 0;
        String arenaName = StringArgumentType.getString(ctx, "arena").toLowerCase();
        ArenaData arena = arenaRepo.get(arenaName);
        if (arena == null || !arena.isReady()) { p.sendMessage(Text.literal("§cАрена не готова/не найдена."), false); return 1; }
        if (!cooldownService.getRemaining(p.getUuid()).isZero()) {
            p.sendMessage(Text.literal("§cКД: " + cooldownService.getRemaining(p.getUuid()).toSeconds() + " сек."), false);
            return 1;
        }
        Difficulty d = parseDifficulty(difficultyRaw);
        Mode m = parseMode(modeRaw);
        int recruit = 45;
        QueueLobby q = sessionService.openQueue(arenaName, d, m, p.getUuid(), recruit);
        p.sendMessage(Text.literal("§aНабор открыт на " + recruit + " сек. " + d + " / " + m), false);
        ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal("§eКолизей: набор на арену " + arenaName + " (/colosseum join " + arenaName + ")"), false);
        return 1;
    }


    private int joinAnyQueue(CommandContext<ServerCommandSource> ctx) {
        QueueLobby q = sessionService.firstOpenLobby();
        if (q == null) {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            if (p != null) p.sendMessage(Text.literal("§cНет активного набора."), false);
            return 1;
        }
        return joinQueueWithArena(ctx, q.arena);
    }

    private int joinQueue(CommandContext<ServerCommandSource> ctx) {
        String arenaName = StringArgumentType.getString(ctx, "arena").toLowerCase();
        return joinQueueWithArena(ctx, arenaName);
    }

    private int joinQueueWithArena(CommandContext<ServerCommandSource> ctx, String arenaName) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); if (p == null) return 0;
        ArenaData arena = arenaRepo.get(arenaName);
        if (arena == null) { p.sendMessage(Text.literal("§cАрена не найдена."), false); return 1; }
        if (!cooldownService.getRemaining(p.getUuid()).isZero()) { p.sendMessage(Text.literal("§cКД активен."), false); return 1; }
        boolean ok = sessionService.joinLobby(arenaName, p.getUuid(), arena.maxPlayers);
        p.sendMessage(Text.literal(ok ? "§aВы в наборе." : "§cНе удалось войти в набор."), false);
        if (ok) {
            QueueLobby q = sessionService.getLobby(arenaName);
            if (q != null && q.players.size() >= arena.maxPlayers) {
                sessionService.startFromLobby(ctx.getSource().getServer(), arena);
            }
        }
        return 1;
    }


    private int declineAnyQueue(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        int removed = sessionService.declineAll(p.getUuid());
        p.sendMessage(Text.literal(removed > 0 ? "§eВы вышли из " + removed + " набора(ов)." : "§cВы не состоите в наборах."), false);
        return 1;
    }

    private int declineQueue(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        String arenaName = StringArgumentType.getString(ctx, "arena").toLowerCase();
        boolean ok = sessionService.declineLobby(arenaName, p.getUuid());
        p.sendMessage(Text.literal(ok ? "§eВы покинули набор: " + arenaName : "§cВы не были в наборе: " + arenaName), false);
        return 1;
    }

    private int listArenas(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        List<String> names = new ArrayList<>(arenaRepo.names());
        Collections.sort(names);
        if (names.isEmpty()) return msg(p, "§cСохранённых арен нет.");
        p.sendMessage(Text.literal("§eАрены: §f" + String.join(", ", names)), false);
        return 1;
    }

    private int adminEditArena(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); if (p == null) return 0;
        String name = StringArgumentType.getString(ctx, "arena").toLowerCase();
        ArenaData a = arenaRepo.get(name);
        if (a == null) { a = new ArenaData(); a.name = name; a.world = p.getEntityWorld().getRegistryKey().getValue().toString(); arenaRepo.put(name, a); }
        editingArena.put(p.getUuid(), a);
        p.sendMessage(Text.literal("§aРедактирование арены: " + name), false);
        return 1;
    }

    private int setCenter(CommandContext<ServerCommandSource> ctx) { return setPos(ctx, "center"); }
    private int addSpawn(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); ArenaData a = editingArena.get(p.getUuid());
        if (a == null) return msg(p, "Сначала /colosseum admin edit <arena>");
        a.spawns.add(new Vec3d(p.getX(), p.getY(), p.getZ())); return msg(p, "§aSpawn добавлен.");
    }
    private int setEntry(CommandContext<ServerCommandSource> ctx) { return setPos(ctx, "entry"); }
    private int setExit(CommandContext<ServerCommandSource> ctx) { return setPos(ctx, "exit"); }
    private int setLobby(CommandContext<ServerCommandSource> ctx) { return setPos(ctx, "lobby"); }

    private int setPos(CommandContext<ServerCommandSource> ctx, String key) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); ArenaData a = editingArena.get(p.getUuid());
        if (a == null) return msg(p, "Сначала /colosseum admin edit <arena>");
        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        switch (key) {
            case "center" -> a.center = pos;
            case "entry" -> a.entry = pos;
            case "exit" -> a.exit = pos;
            case "lobby" -> a.lobby = pos;
        }
        a.world = p.getEntityWorld().getRegistryKey().getValue().toString();
        return msg(p, "§aПозиция " + key + " сохранена.");
    }

    private int setRadius(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); ArenaData a = editingArena.get(p.getUuid());
        if (a == null) return msg(p, "Сначала /colosseum admin edit <arena>");
        a.radius = IntegerArgumentType.getInteger(ctx, "r");
        return msg(p, "§aРадиус: " + (int)a.radius);
    }

    private int setMaxPlayers(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); ArenaData a = editingArena.get(p.getUuid());
        if (a == null) return msg(p, "Сначала /colosseum admin edit <arena>");
        a.maxPlayers = IntegerArgumentType.getInteger(ctx, "n");
        return msg(p, "§aМакс игроков: " + a.maxPlayers);
    }

    private int saveArena(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); if (p == null) return 0;
        String name = StringArgumentType.getString(ctx, "name").toLowerCase();
        ArenaData a = editingArena.get(p.getUuid());
        if (a == null) return msg(p, "Сначала /colosseum admin edit <arena>");
        arenaRepo.put(name, a);
        try { arenaRepo.save(); } catch (IOException e) { return msg(p, "§cОшибка сохранения: " + e.getMessage()); }
        return msg(p, "§aАрена сохранена: " + name);
    }

    private int win(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); if (p == null) return 0;
        var s = sessionService.getByPlayer(p.getUuid());
        if (s == null) return msg(p, "§cВы не в сессии");
        sessionService.finishVictory(ctx.getSource().getServer(), s); return 1;
    }

    private int lose(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer(); if (p == null) return 0;
        var s = sessionService.getByPlayer(p.getUuid());
        if (s == null) return msg(p, "§cВы не в сессии");
        sessionService.finishDefeat(ctx.getSource().getServer(), s, "Сессия завершена админом"); return 1;
    }


    private int clearCooldownSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        cooldownService.clearCooldown(p.getUuid());
        p.sendMessage(Text.literal("§aКД очищен."), false);
        return 1;
    }

    private int clearCooldownForPlayer(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        var target = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("§cИгрок не найден/не в сети: " + playerName), false);
            return 1;
        }
        cooldownService.clearCooldown(target.getUuid());
        target.sendMessage(Text.literal("§aВаш КД был снят администратором."), false);
        ctx.getSource().sendFeedback(() -> Text.literal("§aКД очищен для: " + target.getName().getString()), false);
        return 1;
    }

    private int msg(ServerPlayerEntity p, String m) { p.sendMessage(Text.literal(m), false); return 1; }

    private Difficulty parseDifficulty(String v) {
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "medium" -> Difficulty.MEDIUM;
            case "hard" -> Difficulty.HARD;
            case "hardcore" -> Difficulty.HARDCORE;
            default -> Difficulty.EASY;
        };
    }

    private Mode parseMode(String v) {
        return "elite".equalsIgnoreCase(v) ? Mode.ELITE : Mode.CLASSIC;
    }
}
