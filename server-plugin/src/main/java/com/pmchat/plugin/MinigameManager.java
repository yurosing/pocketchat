package com.pmchat.plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory state for the two Vault-wagered minigames (coin flip, rock-paper-
 * scissors). Purely transient bookkeeping — no economy calls here, no disk
 * persistence; {@link MediaChannel} does the actual Vault withdraw/deposit and
 * calls into this class only to track who's playing whom. A disconnecting
 * player's open bets/challenges/matches are dropped by {@link MediaChannel#forget}.
 */
final class MinigameManager {

    // ---------- coin flip ----------

    /** One open bet, waiting for someone to take the other side. */
    record CoinBet(UUID opener, String openerName, double amount, int side) {
    }

    /** One open bet per player — opening a new one replaces the old (caller refunds it). */
    private final Map<UUID, CoinBet> openBets = new LinkedHashMap<>();

    synchronized CoinBet coinOpen(UUID uuid, String name, double amount, int side) {
        return openBets.put(uuid, new CoinBet(uuid, name, amount, side));
    }

    synchronized CoinBet coinCancel(UUID uuid) {
        return openBets.remove(uuid);
    }

    /** Removes and returns the opener's bet (used when someone accepts it). */
    synchronized CoinBet coinTake(UUID opener) {
        return openBets.remove(opener);
    }

    synchronized List<CoinBet> coinList() {
        return List.copyOf(openBets.values());
    }

    // ---------- rock-paper-scissors ----------

    /** A challenge waiting on the target's response. */
    record RpsChallenge(UUID challenger, String challengerName, double amount) {
    }

    /** One pending challenge per target — a new one replaces the old (caller refunds it). */
    private final Map<UUID, RpsChallenge> pendingChallenges = new LinkedHashMap<>();

    synchronized RpsChallenge rpsChallenge(UUID target, UUID challenger, String challengerName, double amount) {
        return pendingChallenges.put(target, new RpsChallenge(challenger, challengerName, amount));
    }

    /** Removes and returns the pending challenge aimed at this player (accept/decline/disconnect). */
    synchronized RpsChallenge rpsTakeChallenge(UUID target) {
        return pendingChallenges.remove(target);
    }

    /** A confirmed match — both wagers held, waiting on one or both choices (0 rock/1 paper/2 scissors). */
    static final class RpsMatch {
        final UUID a, b;
        final String aName, bName;
        final double amount;
        Integer aChoice, bChoice;

        RpsMatch(UUID a, String aName, UUID b, String bName, double amount) {
            this.a = a;
            this.aName = aName;
            this.b = b;
            this.bName = bName;
            this.amount = amount;
        }
    }

    private final Map<String, RpsMatch> matches = new LinkedHashMap<>();

    private static String key(UUID x, UUID y) {
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    synchronized RpsMatch rpsStart(UUID a, String aName, UUID b, String bName, double amount) {
        RpsMatch m = new RpsMatch(a, aName, b, bName, amount);
        matches.put(key(a, b), m);
        return m;
    }

    synchronized RpsMatch rpsMatchOf(UUID uuid, UUID opponent) {
        return matches.get(key(uuid, opponent));
    }

    synchronized RpsMatch rpsEnd(UUID uuid, UUID opponent) {
        return matches.remove(key(uuid, opponent));
    }

    /** All matches (and bets/challenges) touching this player — used to refund/clean up on disconnect. */
    synchronized List<RpsMatch> rpsMatchesOf(UUID uuid) {
        return matches.values().stream().filter(m -> m.a.equals(uuid) || m.b.equals(uuid)).toList();
    }

    synchronized void rpsRemove(RpsMatch m) {
        matches.remove(key(m.a, m.b));
    }
}
