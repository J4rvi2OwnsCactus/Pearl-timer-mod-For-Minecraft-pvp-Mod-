package com.pearltimer;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws each active pearl timer on screen at the position of the player's head.
 *
 * <p>This projects the head position onto the screen itself (instead of hooking the entity renderer), so it is
 * drawn as a HUD overlay. That means the timer is visible through walls, and it can sit slightly off when your
 * FOV is temporarily changed by sprinting or potions.
 */
final class PearlHud {

	private static final int BACKGROUND = 0x90000000;

	private PearlHud() {
	}

	static void extract(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (!PearlTimerClient.hudEnabled || level == null || mc.player == null || mc.options.hideGui || PearlTimerClient.expiry.isEmpty()) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Camera camera = mc.gameRenderer.getMainCamera();
		Font font = mc.font;
		long now = level.getGameTime();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();

		for (Map.Entry<UUID, Long> entry : PearlTimerClient.expiry.entrySet()) {
			Player player = level.getPlayerByUUID(entry.getKey());
			if (player == null) {
				continue;
			}
			// You can't see your own head in first person.
			if (player == mc.player && firstPerson) {
				continue;
			}

			float secondsLeft = Math.max(0f, (entry.getValue() - now - partialTick) / 20f);

			Vec3 head = player.getPosition(partialTick).add(0, player.getBbHeight() + 0.65, 0);
			double[] screen = project(mc, camera, head);
			if (screen == null) {
				continue;
			}

			String text = String.format("%.1fs", secondsLeft);
			int color = colorFor(secondsLeft / (PearlTimerClient.TIMER_TICKS / 20f));
			int textWidth = font.width(text);
			int x = (int) Math.round(screen[0]) - textWidth / 2;
			int y = (int) Math.round(screen[1]) - font.lineHeight;

			graphics.fill(x - 2, y - 1, x + textWidth + 2, y + font.lineHeight, BACKGROUND);
			graphics.drawString(font, text, x, y, color, true);
		}
	}

	/** Green when the timer is fresh, through yellow, to red as it runs out. */
	private static int colorFor(float fractionLeft) {
		float f = Mth.clamp(fractionLeft, 0f, 1f);
		int red = f > 0.5f ? (int) (255 * (1f - f) * 2f) : 255;
		int green = f > 0.5f ? 255 : (int) (255 * f * 2f);
		return 0xFF000000 | (red << 16) | (green << 8) | 0x40;
	}

	/**
	 * Converts a world position to GUI-scaled screen coordinates, or null if it's behind the camera.
	 */
	private static double[] project(Minecraft mc, Camera camera, Vec3 worldPos) {
		Vec3 camPos = camera.getPosition();
		double dx = worldPos.x - camPos.x;
		double dy = worldPos.y - camPos.y;
		double dz = worldPos.z - camPos.z;

		double yaw = Math.toRadians(camera.getYRot());
		double pitch = Math.toRadians(camera.getXRot());

		// Camera basis vectors. Minecraft yaw 0 faces +Z; positive pitch looks down.
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);

		double rx = -Math.cos(yaw);
		double ry = 0;
		double rz = -Math.sin(yaw);

		// up = right x forward
		double ux = ry * fz - rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy - ry * fx;

		double forward = dx * fx + dy * fy + dz * fz;
		if (forward <= 0.05) {
			return null;
		}
		double right = dx * rx + dy * ry + dz * rz;
		double up = dx * ux + dy * uy + dz * uz;

		int width = mc.getWindow().getGuiScaledWidth();
		int height = mc.getWindow().getGuiScaledHeight();
		double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double aspect = (double) width / height;

		double screenX = width / 2.0 + (right / forward) / (tanHalfFov * aspect) * (width / 2.0);
		double screenY = height / 2.0 - (up / forward) / tanHalfFov * (height / 2.0);

		if (screenX < -50 || screenX > width + 50 || screenY < -50 || screenY > height + 50) {
			return null;
		}
		return new double[] {screenX, screenY};
	}
}
