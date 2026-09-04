package com.usujiotarako;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.usujiotarako.network.CapabilityPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

public class BetterHotbars implements ModInitializer {
	public static final String MOD_ID = "better-hotbars";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PickupPolicy.load();
		PayloadTypeRegistry.clientboundConfiguration().register(CapabilityPayload.TYPE, CapabilityPayload.CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
			BehaviorPolicy.beginServerConnection();
			if (ServerConfigurationNetworking.canSend(listener, CapabilityPayload.TYPE)) {
				ServerConfigurationNetworking.send(listener, new CapabilityPayload(BehaviorPolicy.getServerMode().name()));
			}
		});
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Layered Hotbars initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
