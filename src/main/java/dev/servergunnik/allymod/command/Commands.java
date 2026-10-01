package dev.servergunnik.allymod.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

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

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.data.AllianceStore;
import dev.servergunnik.allymod.data.Relation;
import dev.servergunnik.allymod.data.RelationKind;
import dev.servergunnik.allymod.data.RelationStore;
import dev.servergunnik.allymod.gui.ManageScreen;
import dev.servergunnik.allymod.render.RenderProfiler;

public final class Commands {
	private Commands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			registerRelationTree(dispatcher, "ally", RelationKind.ALLY, ChatFormatting.GREEN);
			registerRelationTree(dispatcher, "enemy", RelationKind.ENEMY, ChatFormatting.RED);
			registerAllymodTree(dispatcher);
			registerSojuszTree(dispatcher);
		});
	}

	private static void registerRelationTree(CommandDispatcher<FabricClientCommandSource> dispatcher,
	                                          String name, RelationKind kind, ChatFormatting color) {
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
					ctx.getSource().sendFeedback(Component.translatable(usageKey).withStyle(ChatFormatting.GRAY));
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
					ctx.getSource().sendFeedback(Component.translatable("allymod.command.usage.allymod")
							.withStyle(ChatFormatting.GRAY));
					return 1;
				}));
	}

	private static void registerSojuszTree(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("snzsojusz")
				.then(literal("reload").executes(Commands::reloadSojusz))
				.executes(ctx -> {
					ctx.getSource().sendFeedback(Component.translatable("allymod.command.usage.snzsojusz",
							Allymod.alliance().size()).withStyle(ChatFormatting.GRAY));
					return 1;
				}));
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx, RelationKind kind, ChatFormatting color) {
		String targetName = StringArgumentType.getString(ctx, "player");
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) {
			ctx.getSource().sendError(Component.translatable("allymod.command.error.not_on_server"));
			return 0;
		}
		PlayerInfo info = conn.getPlayerInfoIgnoreCase(targetName);
		if (info == null) {
			ctx.getSource().sendError(Component.translatable("allymod.command.error.player_not_online", targetName));
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
			ctx.getSource().sendError(Component.translatable("allymod.command.error.save_failed", e.getMessage()));
			return 0;
		}

		String key = kind == RelationKind.ALLY ? "allymod.command.add.ally" : "allymod.command.add.enemy";
		ctx.getSource().sendFeedback(Component.translatable(key, realName).withStyle(color));
		return 1;
	}

	private static int remove(CommandContext<FabricClientCommandSource> ctx, RelationKind kind, ChatFormatting color) {
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
			ctx.getSource().sendError(Component.translatable(key, targetName));
			return 0;
		}

		store.remove(match.uuid());
		cache.refresh(match.uuid(), null);
		try {
			store.save();
		} catch (IOException e) {
			Allymod.LOGGER.error("Zapis relations.json nieudany", e);
			ctx.getSource().sendError(Component.translatable("allymod.command.error.save_failed", e.getMessage()));
			return 0;
		}

		String key = kind == RelationKind.ALLY ? "allymod.command.remove.ally" : "allymod.command.remove.enemy";
		ctx.getSource().sendFeedback(Component.translatable(key, match.lastKnownName()).withStyle(color));
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx, RelationKind kind, ChatFormatting color) {
		List<Relation> rows = Allymod.relations().byKind(kind);
		FabricClientCommandSource src = ctx.getSource();

		if (rows.isEmpty()) {
			String emptyKey = kind == RelationKind.ALLY
					? "allymod.command.list.empty.ally"
					: "allymod.command.list.empty.enemy";
			src.sendFeedback(Component.translatable(emptyKey).withStyle(ChatFormatting.GRAY));
			return 1;
		}

		String titleKey = kind == RelationKind.ALLY
				? "allymod.command.list.title.ally"
				: "allymod.command.list.title.enemy";
		src.sendFeedback(Component.translatable(titleKey, rows.size()).withStyle(color));

		for (Relation r : rows) {
			src.sendFeedback(Component.literal(" - " + r.lastKnownName() + "  ")
					.withStyle(ChatFormatting.WHITE)
					.append(Component.literal(r.uuid().toString()).withStyle(ChatFormatting.DARK_GRAY)));
		}
		return rows.size();
	}

	private static int reload(CommandContext<FabricClientCommandSource> ctx) {
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		try {
			store.load();
		} catch (IOException e) {
			ctx.getSource().sendError(Component.translatable("allymod.command.error.load_failed", e.getMessage()));
			return 0;
		}
		cache.rebuild();
		ctx.getSource().sendFeedback(Component.translatable("allymod.command.reload.done", store.size())
				.withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int reloadSojusz(CommandContext<FabricClientCommandSource> ctx) {
		AllianceStore alliance = Allymod.alliance();
		AllianceStore.LoadResult result;
		try {
			result = Allymod.reloadAlliance();
		} catch (IOException e) {
			ctx.getSource().sendError(Component.translatable("allymod.command.sojusz.load_failed",
					e.getMessage(), alliance.size()));
			return 0;
		}
		ctx.getSource().sendFeedback(Component.translatable("allymod.command.sojusz.reload.done", result.loaded())
				.withStyle(ChatFormatting.AQUA));
		if (!result.warnings().isEmpty()) {
			ctx.getSource().sendFeedback(Component.translatable("allymod.command.sojusz.reload.skipped",
					result.warnings().size()).withStyle(ChatFormatting.YELLOW));
		}
		return 1;
	}

	private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
		// setScreenAndShow z pakietu klienta — nie mozna zawolac w trakcie dispatch commanda,
		// odloz na kolejny tick.
		Minecraft.getInstance().execute(() ->
				Minecraft.getInstance().setScreenAndShow(new ManageScreen()));
		return 1;
	}

	private static int toggleFpsDebug(CommandContext<FabricClientCommandSource> ctx) {
		boolean nowOn = !RenderProfiler.isEnabled();
		RenderProfiler.setEnabled(nowOn);
		ChatFormatting color = nowOn ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
		String key = nowOn ? "allymod.command.debug.on" : "allymod.command.debug.off";
		ctx.getSource().sendFeedback(Component.translatable(key).withStyle(color));
		return 1;
	}

	private static int info(CommandContext<FabricClientCommandSource> ctx) {
		RelationStore store = Allymod.relations();
		int allies = store.byKind(RelationKind.ALLY).size();
		int enemies = store.byKind(RelationKind.ENEMY).size();
		FabricClientCommandSource src = ctx.getSource();
		src.sendFeedback(Component.translatable("allymod.command.info.title", Allymod.MOD_ID).withStyle(ChatFormatting.AQUA));
		src.sendFeedback(Component.translatable("allymod.command.info.allies", allies).withStyle(ChatFormatting.GREEN));
		src.sendFeedback(Component.translatable("allymod.command.info.enemies", enemies).withStyle(ChatFormatting.RED));
		src.sendFeedback(Component.translatable("allymod.command.info.file", store.file().toString()).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static CompletableFuture<Suggestions> suggestOnlinePlayers(
			CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) return builder.buildFuture();
		String prefix = builder.getRemaining().toLowerCase();
		for (PlayerInfo info : conn.getOnlinePlayers()) {
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
