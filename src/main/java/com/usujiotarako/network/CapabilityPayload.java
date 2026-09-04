package com.usujiotarako.network;

import com.usujiotarako.BetterHotbars;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent during configuration, before the client-side Player and inventory menu exist. */
public record CapabilityPayload(String mode) implements CustomPacketPayload {
	public static final Type<CapabilityPayload> TYPE = new Type<>(BetterHotbars.id("capabilities"));
	public static final StreamCodec<FriendlyByteBuf, CapabilityPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			CapabilityPayload::mode,
			CapabilityPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
