package com.pearltimer;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
		Font font = mc.font;
		long now = level.getGameTime();
		CameraType cameraType = mc.options.getCameraType();
		boolean firstPerson = cameraType.isFirstPerson();
		Entity cameraEntity = mc.getCameraEntity();
		if (cameraEntity == null) {
			return;
		}

		// Work out where the camera is and where it looks, without using the Camera class.
		double yawDeg = cameraEntity.getViewYRot(partialTick);
		double pitchDeg = cameraEntity.getViewXRot(partialTick);
		Vec3 camPos = cameraEntity.getEyePosition(partialTick);
		if (!firstPerson) {
			if (cameraType == CameraType.THIRD_PERSON_FRONT) {
				yawDeg += 180.0;
				pitchDeg = -pitchDeg;
			}
			double yr = Math.toRadians(yawDeg);
			double pr = Math.toRadians(pitchDeg);
			Vec3 look = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
			camPos = camPos.subtract(look.scale(4.0)); // default third-person distance
		}

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
			double[] screen = project(mc, camPos, yawDeg, pitchDeg, head);
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
	private static double[] project(Minecraft mc, Vec3 camPos, double yawDeg, double pitchDeg, Vec3 worldPos) {
		double dx = worldPos.x - camPos.x;
		double dy = worldPos.y - camPos.y;
		double dz = worldPos.z - camPos.z;

		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);

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
