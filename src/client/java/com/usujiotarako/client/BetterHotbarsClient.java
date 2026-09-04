package com.usujiotarako.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.usujiotarako.BehaviorPolicy;
import com.usujiotarako.network.CapabilityPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class BetterHotbarsClient implements ClientModInitializer {
	private static KeyMapping profilePreviewKey;

	@Override
	public void onInitializeClient() {
		ClientConfigurationConnectionEvents.INIT.register((listener, client) -> BehaviorPolicy.resetAutomaticConnection());
		ClientConfigurationNetworking.registerGlobalReceiver(CapabilityPayload.TYPE,
				(payload, context) -> BehaviorPolicy.acceptServerMode(payload.mode()));
		VirtualHotbarManager.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("better-hotbars", "controls"));
		profilePreviewKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.better-hotbars.profile_preview",
				GLFW.GLFW_KEY_LEFT_CONTROL,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			VirtualHotbarManager.tick(client);
		});
	}

	public static boolean isProfilePreviewDown() {
		if (profilePreviewKey == null || profilePreviewKey.isUnbound()) return false;

		Minecraft minecraft = Minecraft.getInstance();
		InputConstants.Key boundKey;
		try {
			boundKey = InputConstants.getKey(profilePreviewKey.saveString());
		} catch (IllegalArgumentException ignored) {
			return profilePreviewKey.isDown();
		}

		// KeyMapping.isDown() is not reliable for hold-style controls while a GUI
		// owns keyboard input. Poll the rebound physical keyboard key instead so the
		// inventory preview reacts immediately while the screen is open.
		if (boundKey.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(minecraft.getWindow(), boundKey.getValue());
		}

		return profilePreviewKey.isDown();
	}

	public static boolean isProfileScrollModifierDown() {
		Minecraft minecraft = Minecraft.getInstance();
		return InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
				|| InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
	}
}
