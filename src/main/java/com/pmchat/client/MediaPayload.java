package com.pmchat.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom payload for the {@code pmchat:media} plugin-messaging channel — the
 * transport for routing photos/voice/video through a server that runs the
 * PocketChatMedia plugin. The payload is just an opaque byte array; the framing
 * (opcodes, chunking) lives in {@link PmServerMedia}.
 */
public record MediaPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Id<MediaPayload> ID =
            new CustomPacketPayload.Id<>(Identifier.of("pmchat", "media"));

    public static final StreamCodec<FriendlyByteBuf, MediaPayload> CODEC = new StreamCodec<>() {
        @Override
        public MediaPayload decode(FriendlyByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new MediaPayload(b);
        }

        @Override
        public void encode(FriendlyByteBuf buf, MediaPayload value) {
            buf.writeBytes(value.data());
        }
    };

    @Override
    public CustomPacketPayload.Id<MediaPayload> getId() {
        return ID;
    }
}
