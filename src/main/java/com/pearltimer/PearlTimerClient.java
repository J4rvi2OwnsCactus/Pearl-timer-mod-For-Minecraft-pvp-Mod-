package com.pearltimer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.api.ClientModInitializer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;

/**
 * Client-only. Watches for ender pearls appearing in the world, works out who threw each one,
 * and starts a countdown for that player. {@link PearlHud} draws the countdowns above their heads.
 */
public class PearlTimerClient implements ClientModInitializer {

	public static final String MOD_ID = "pearltimer";

	/** Length of the timer in ticks (20 ticks = 1 second). 300 = 15 seconds. */
	public static final int TIMER_TICKS = 15 * 20;

	/** Player UUID -> game time (in ticks) when their timer ends. */
	static final Map<UUID, Long> expiry = new HashMap<>();

	/** Pearl entity ids we've already counted, so one throw only starts one timer. */
	private static final Set<Integer> seenPearls = new HashSet<>();

	/** Whether the pearl timers are drawn. Toggled with the "Toggle HUD" keybind. */
	public static boolean hudEnabled = true;

	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		// Unbound by default: bind it yourself under Options > Controls > Key Binds > Pearl Timer.
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pearltimer.toggle_hud",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(PearlTimerClient::tick);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			expiry.clear();
			seenPearls.clear();
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "pearl_timers"),
				PearlHud::extract);
	}

	private static void tick(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			hudEnabled = !hudEnabled;
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable(
						hudEnabled ? "message.pearltimer.hud_on" : "message.pearltimer.hud_off"), true);
			}
		}

		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}

		long now = level.getGameTime();
		Set<Integer> presentNow = new HashSet<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ThrownEnderpearl pearl)) {
				continue;
			}
			presentNow.add(pearl.getId());

			if (seenPearls.contains(pearl.getId())) {
				continue;
			}
			// The owner can take a moment to resolve; only mark the pearl as seen once we know who threw it.
			if (pearl.getOwner() instanceof Player thrower) {
				seenPearls.add(pearl.getId());
				expiry.put(thrower.getUUID(), now + TIMER_TICKS);
			}
		}

		// Forget pearls that have landed or despawned (ids get reused).
		seenPearls.retainAll(presentNow);

		// Drop finished timers.
		expiry.values().removeIf(end -> end <= now);
	}
}
