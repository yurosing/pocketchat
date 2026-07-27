package com.pmchat.api.client;

/**
 * Static entry point to the PocketChat client API.
 *
 * <p>PocketChat installs its implementation while the mod initialises, so any code that
 * runs after mod init can reach it. Declare PocketChat as a suggestion (not a hard
 * dependency) in your {@code fabric.mod.json} and guard with {@link #isLoaded()} so your
 * mod still works when PocketChat is not installed:
 *
 * <pre>{@code
 * "suggests": { "pmchat": "*" }
 * }</pre>
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * if (PocketChatClient.isLoaded()) {
 *     PocketChatClient.get().addListener(new PocketChatListener() {
 *         @Override
 *         public void onMessageReceived(PmChatMessage message) {
 *             System.out.println(message.sender() + ": " + message.text());
 *         }
 *     });
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class PocketChatClient {

    private static volatile PocketChatClientApi instance;

    private PocketChatClient() {
    }

    /**
     * @return {@code true} when the PocketChat mod is present and initialised
     */
    public static boolean isLoaded() {
        return instance != null;
    }

    /**
     * The client API, or {@code null} when PocketChat is not installed.
     *
     * @return the API, or {@code null}
     */
    public static PocketChatClientApi getOrNull() {
        return instance;
    }

    /**
     * The client API.
     *
     * @return the API, never {@code null}
     * @throws IllegalStateException when PocketChat is not installed — guard with
     *                               {@link #isLoaded()}, or use {@link #getOrNull()}
     */
    public static PocketChatClientApi get() {
        PocketChatClientApi api = instance;
        if (api == null) {
            throw new IllegalStateException(
                    "PocketChat is not installed. Check PocketChatClient.isLoaded() first.");
        }
        return api;
    }

    /**
     * Installs the implementation. <b>Called by PocketChat itself</b> — other mods must
     * not call this.
     *
     * @param api the implementation
     * @throws IllegalStateException when an implementation is already installed
     */
    public static void install(PocketChatClientApi api) {
        if (instance != null && api != null) {
            throw new IllegalStateException("A PocketChat client API is already installed");
        }
        instance = api;
    }
}
