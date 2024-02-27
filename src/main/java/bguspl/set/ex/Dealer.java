package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.random.*;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate = false;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private Thread dealer;

    public volatile boolean isWorking = false;

    // keys

    public static Object dealerKey;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerKey = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealer = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        // creating the players threads:
        for (int i = 0; i < players.length; i++) {
            Thread player = new Thread(players[i], "player" + i);

            synchronized (players[i]) {
                player.start();
                try {
                    players[i].wait();
                } catch (InterruptedException e) {
                }
            }
        }

        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        Thread.currentThread().interrupt(); 
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 1000;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();

        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        for (int i = players.length -1 ; i >=0; i--) {
            players[i].terminate();
            players[i].playerThread.interrupt();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
            
            for (int i = 0; i < players.length; i++) {
                if (players[i].checkMe && players[i].tokenCounter == env.config.featureSize) { 
                    int[] tokens = players[i].tokens;
                    int[] cards = slotToCard(players[i].tokens);
                    
                    if (env.util.testSet(cards)) {   
                        env.logger.info("PLAYER " + players[i].id + " HAS A SET");
                        for (int j = 0; j < tokens.length; j++) {
                            synchronized(table.slotLock[j]){
                                int cardToRemove = tokens[j];
                                removeOtherTokens(cardToRemove); // removing mine and other's tokens from the card
                                table.removeCard(cardToRemove);
                            }
                        }
                        // removeOtherTokens(tokens);
                        players[i].point();
                        updateTimerDisplay(true);

                    }
                     else {
                         players[i].penalty();
                    
                     }
                }
                players[i].checkMe = false;
                synchronized(players[i].playerKey){
                    players[i].playerKey.notify();
                }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

            isWorking = true;
            for (int slot = 0; slot < env.config.tableSize && deck.size() > 0; slot++) {
                if (table.slotToCard[slot] == null) {
                    int randomIndex = randomCard();
                    int card = deck.get(randomIndex);
                    table.placeCard(card, slot);
                    deck.remove(randomIndex);
                }
            }
            isWorking = false;
    }
    

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized (this) {
            try {
                wait(100);
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 1000;
        } else {
            long clockTime = reshuffleTime - System.currentTimeMillis();
            if(clockTime < 0)
                env.ui.setCountdown(0, true);
            else
                env.ui.setCountdown(clockTime, clockTime <= env.config.turnTimeoutWarningMillis);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() { 
        isWorking = true;
        env.ui.removeTokens();
        resetTokens();
        for (int slot = 0; slot < env.config.tableSize; slot++) {
            if (table.slotToCard[slot] != null) {
                    deck.add(table.slotToCard[slot]);
                    table.removeCard(slot);
            }
        }
        isWorking = false;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        int counter = 0;
        for (Player p : players) {
            if (p.score() > maxScore) {
                maxScore = p.score();
                counter = 1;
            } else if (p.score() == maxScore)
                counter++;
        }
        int[] winners = new int[counter];
        for (Player p : players) {
            if (p.score() == maxScore) {
                winners[counter - 1] = p.id;
                counter--;
            }
        }
        env.ui.announceWinner(winners);

    }

    /*-------------------------------Our Functions------------------------------------------*/

    // return a random number in the size of the deck
    private int randomCard() {
        Random rnd = new Random();
        int card = rnd.nextInt(deck.size());
        return card;
    }

    // reset all the players tokensArray
    private void resetTokens() {

        for (Player p : players) {
            p.actions.clear();
            p.tokenCounter = 0;
            for (int i = 0; i< p.tokens.length; i++) {
                p.tokens[i] = -1;  
            }
        }
    }

    private void removeOtherTokens(int slot) {
        synchronized(table.slotLock[slot]){
        for (Player p : players) {
            for (int i = 0; i < p.tokens.length; i++) {
                if (p.tokens[i] == slot) {
                    p.tokenCounter--;
                    p.tokens[i] = -1;
                    env.ui.removeToken(p.id, slot);
                }
            }
        }
        }
    }

    private int[] slotToCard(int[] slots) {
        int[] cards = new int[env.config.featureSize];
        for (int i = 0; i < slots.length; i++) {
            if(table.slotToCard[slots[i]] == null)
                Thread.currentThread().interrupt();
            else
                cards[i] = table.slotToCard[slots[i]];
        }
        return cards;
    }

    // private void removeOtherTokens (int[] slots){

    //     for (int i = 0; i < slots.length; i++) {
    //         for (int j = 0; j < players.length; j++) {
    //             if(players[j].hasToken(slots[i]) >= 0){
    //                 table.removeToken(players[j].id, slots[i]);
    //                 players[j].tokens[players[j].hasToken(slots[i])] = -1;
    //                 players[j].tokenCounter--;
    //             }
                
    //         }
    //     }
    // }

}
