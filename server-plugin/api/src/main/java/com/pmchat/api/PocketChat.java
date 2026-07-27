package com.pmchat.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Static entry point to the PocketChat server API.
 *
 * <p>PocketChat ships in two editions whose <em>plugin names</em> differ
 * ({@code PocketChat} and {@code PocketChatPro}), so depending on the plugin by
 * name is fragile. Instead the plugin registers {@link PocketChatApi} with the
 * Bukkit {@link org.bukkit.plugin.ServicesManager}, and this class looks it up —
 * that works identically on both editions.
 *
 * <p>Declare PocketChat as a soft dependency in your {@code plugin.yml} so the
 * service is registered before your plugin enables:
 *
 * <pre>{@code
 * softdepend: [PocketChat, PocketChatPro]
 * }</pre>
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * if (PocketChat.isPresent()) {
 *     PocketChatApi api = PocketChat.api();
 *     getLogger().info("PocketChat " + api.version() + " (" + api.tier() + ")");
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class PocketChat {

    /**
     * The plugin-messaging channel PocketChat clients and the server talk over.
     * See {@link com.pmchat.api.protocol.PocketChatProtocol} for the message format.
     */
    public static final String CHANNEL = "pmchat:media";

    private PocketChat() {
    }

    /**
     * Whether the PocketChat plugin is installed, enabled, and has registered its API.
     *
     * @return {@code true} when {@link #api()} will succeed
     */
    public static boolean isPresent() {
        return apiOrNull() != null;
    }

    /**
     * The registered API, or {@code null} when PocketChat is absent or not yet enabled.
     *
     * @return the API, or {@code null}
     */
    public static PocketChatApi apiOrNull() {
        RegisteredServiceProvider<PocketChatApi> rsp =
                Bukkit.getServicesManager().getRegistration(PocketChatApi.class);
        return rsp == null ? null : rsp.getProvider();
    }

    /**
     * The registered API.
     *
     * @return the API, never {@code null}
     * @throws IllegalStateException when PocketChat is not installed or not enabled yet —
     *                               guard with {@link #isPresent()}, or use {@link #apiOrNull()}
     */
    public static PocketChatApi api() {
        PocketChatApi api = apiOrNull();
        if (api == null) {
            throw new IllegalStateException(
                    "PocketChat is not available. Add 'softdepend: [PocketChat, PocketChatPro]' to your "
                            + "plugin.yml and check PocketChat.isPresent() first.");
        }
        return api;
    }
}
