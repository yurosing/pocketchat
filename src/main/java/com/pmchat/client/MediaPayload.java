package com.pmchat.client;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Custom payload for the {@code pmchat:media} plugin-messaging channel — the
 * transport for routing photos/voice/video through a server that runs the
 * PocketChatMedia plugin. The payload is just an opaque byte array; the framing
 * (opcodes, chunking) lives in {@link PmServerMedia}.
 */
public record MediaPayload(byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<MediaPayload> ID =
            new CustomPayload.Id<>(Identifier.of("pmchat", "media"));

    public static final PacketCodec<PacketByteBuf, MediaPayload> CODEC = new PacketCodec<>() {
        @Override
        public MediaPayload decode(PacketByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new MediaPayload(b);
        }

        @Override
        public void encode(PacketByteBuf buf, MediaPayload value) {
            buf.writeBytes(value.data());
        }
    };

    @Override
    public CustomPayload.Id<MediaPayload> getId() {
        return ID;
    }
}
