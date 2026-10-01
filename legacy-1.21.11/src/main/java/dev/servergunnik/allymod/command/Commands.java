package dev.servergunnik.allymod.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.data.Relation;
import dev.servergunnik.allymod.data.RelationKind;
import dev.servergunnik.allymod.data.RelationStore;
import dev.servergunnik.allymod.gui.ManageScreen;
import dev.servergunnik.allymod.render.RenderProfiler;

public final class Commands {
	private Commands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			registerRelationTree(dispatcher, "ally", RelationKind.ALLY, Formatting.GREEN);
			registerRelationTree(dispatcher, "enemy", RelationKind.ENEMY, Formatting.RED);
			registerAllymodTree(dispatcher);
		});
	}

	private static void registerRelationTree(CommandDispatcher<FabricClientCommandSource> dispatcher,
	                                          String name, RelationKind kind, Formatting color) {
		String usageKey = "allymod.command.usage." + name;
		dispatcher.register(literal(name)
				.then(literal("add")
						.then(argument("player", StringArgumentType.word())
								.suggests(Commands::suggestOnlinePlayers)
								.executes(ctx -> add(ctx, kind, color))))
				.then(literal("remove")
						.then(argument("player", StringArgumentType.word())
								.suggests((ctx, b) -> suggestRelationsOfKind(b, kind))
								.executes(ctx -> remove(ctx, kind, color))))
				.then(literal("list")
						.executes(ctx -> list(ctx, kind, color)))
				.executes(ctx -> {
					ctx.getSource().sendFeedback(Text.translatable(usageKey).formatted(Formatting.GRAY));
					return 1;
				}));
	}

	private static void registerAllymodTree(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("allymod")
				.then(literal("reload").executes(Commands::reload))
				.then(literal("info").executes(Commands::info))
				.then(literal("gui").executes(Commands::openGui))
				.then(literal("debug")
						.then(literal("fps").executes(Commands::toggleFpsDebug)))
				.executes(ctx -> {
					ctx.getSource().sendFeedback(Text.translatable("allymod.command.usage.allymod")
							.formatted(Formatting.GRAY));
					return 1;
				}));
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx, RelationKind kind, Formatting color) {
		String targetName = StringArgumentType.getString(ctx, "player");
		ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
		if (conn == null) {
			ctx.getSource().sendError(Text.translatable("allymod.command.error.not_on_server"));
			return 0;
		}
		PlayerListEntry info = conn.getCaseInsensitivePlayerInfo(targetName);
		if (info == null) {
			ctx.getSource().sendError(Text.translatable("allymod.command.error.player_not_online", targetName));
			return 0;
		}
		GameProfile profile = info.getProfile();
		UUID uuid = profile.id();
		String realName = profile.name();

		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		store.put(uuid, realName, kind);
		cache.refresh(uuid, kind);
		try {
			store.save();
		} catch (IOException e) {
			Allymod.LOGGER.error("Zapis relations.json nieudany", e);
			ctx.getSource().sendError(Text.translatable("allymod.command.error.save_failed", e.getMessage()));
			return 0;
		}

		String key = kind == RelationKind.ALLY ? "allymod.command.add.ally" : "allymod.command.add.enemy";
		ctx.getSource().sendFeedback(Text.translatable(key, realName).formatted(color));
		return 1;
	}

	private static int remove(CommandContext<FabricClientCommandSource> ctx, RelationKind kind, Formatting color) {
		String targetName = StringArgumentType.getString(ctx, "player");
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();

		Relation match = null;
		for (Relation r : store.all()) {
			if (r.kind() == kind && r.lastKnownName().equalsIgnoreCase(targetName)) {
				match = r;
				break;
			}
		}
		if (match == null) {
			String key = kind == RelationKind.ALLY
					? "allymod.command.error.not_in_list.ally"
					: "allymod.command.error.not_in_list.enemy";
			ctx.getSource().sendError(Text.translatable(key, targetName));
			return 0;
		}

		store.remove(match.uuid());
		cache.refresh(match.uuid(), null);
		try {
			store.save();
		} catch (IOException e) {
			Allymod.LOGGER.error("Zapis relations.json nieudany", e);
			ctx.getSource().sendError(Text.translatable("allymod.command.error.save_failed", e.getMessage()));
			return 0;
		}

		String key = kind == RelationKind.ALLY ? "allymod.command.remove.ally" : "allymod.command.remove.enemy";
		ctx.getSource().sendFeedback(Text.translatable(key, match.lastKnownName()).formatted(color));
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx, RelationKind kind, Formatting color) {
		List<Relation> rows = Allymod.relations().byKind(kind);
		FabricClientCommandSource src = ctx.getSource();

		if (rows.isEmpty()) {
			String emptyKey = kind == RelationKind.ALLY
					? "allymod.command.list.empty.ally"
					: "allymod.command.list.empty.enemy";
			src.sendFeedback(Text.translatable(emptyKey).formatted(Formatting.GRAY));
			return 1;
		}

		String titleKey = kind == RelationKind.ALLY
				? "allymod.command.list.title.ally"
				: "allymod.command.list.title.enemy";
		src.sendFeedback(Text.translatable(titleKey, rows.size()).formatted(color));

		for (Relation r : rows) {
			src.sendFeedback(Text.literal(" - " + r.lastKnownName() + "  ")
					.formatted(Formatting.WHITE)
					.append(Text.literal(r.uuid().toString()).formatted(Formatting.DARK_GRAY)));
		}
		return rows.size();
	}

	private static int reload(CommandContext<FabricClientCommandSource> ctx) {
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		try {
			store.load();
		} catch (IOException e) {
			ctx.getSource().sendError(Text.translatable("allymod.command.error.load_failed", e.getMessage()));
			return 0;
		}
		cache.rebuild();
		ctx.getSource().sendFeedback(Text.translatable("allymod.command.reload.done", store.size())
				.formatted(Formatting.AQUA));
		return 1;
	}

	private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
		MinecraftClient.getInstance().execute(() ->
				MinecraftClient.getInstance().setScreen(new ManageScreen()));
		return 1;
	}

	private static int toggleFpsDebug(CommandContext<FabricClientCommandSource> ctx) {
		boolean nowOn = !RenderProfiler.isEnabled();
		RenderProfiler.setEnabled(nowOn);
		Formatting color = nowOn ? Formatting.YELLOW : Formatting.GRAY;
		String key = nowOn ? "allymod.command.debug.on" : "allymod.command.debug.off";
		ctx.getSource().sendFeedback(Text.translatable(key).formatted(color));
		return 1;
	}

	private static int info(CommandContext<FabricClientCommandSource> ctx) {
		RelationStore store = Allymod.relations();
		int allies = store.byKind(RelationKind.ALLY).size();
		int enemies = store.byKind(RelationKind.ENEMY).size();
		FabricClientCommandSource src = ctx.getSource();
		src.sendFeedback(Text.translatable("allymod.command.info.title", Allymod.MOD_ID).formatted(Formatting.AQUA));
		src.sendFeedback(Text.translatable("allymod.command.info.allies", allies).formatted(Formatting.GREEN));
		src.sendFeedback(Text.translatable("allymod.command.info.enemies", enemies).formatted(Formatting.RED));
		src.sendFeedback(Text.translatable("allymod.command.info.file", store.file().toString()).formatted(Formatting.GRAY));
		return 1;
	}

	private static CompletableFuture<Suggestions> suggestOnlinePlayers(
			CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
		if (conn == null) return builder.buildFuture();
		String prefix = builder.getRemaining().toLowerCase();
		for (PlayerListEntry info : conn.getPlayerList()) {
			String name = info.getProfile().name();
			if (name != null && name.toLowerCase().startsWith(prefix)) {
				builder.suggest(name);
			}
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestRelationsOfKind(
			SuggestionsBuilder builder, RelationKind kind) {
		String prefix = builder.getRemaining().toLowerCase();
		for (Relation r : Allymod.relations().byKind(kind)) {
			String name = r.lastKnownName();
			if (name != null && name.toLowerCase().startsWith(prefix)) {
				builder.suggest(name);
			}
		}
		return builder.buildFuture();
	}
}
