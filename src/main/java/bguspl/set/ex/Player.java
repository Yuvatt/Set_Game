package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    protected Thread playerThread; // I changed it to protected

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate = false;;

    /**
     * The current score of the player.
     */
    private int score;

    private final Dealer dealer; // we added

    public int[] tokens; // we added

    public boolean checkMe = false; // we added

    boolean isFull = false;

    int tokenCounter = 0;

    public long freezeTime = -1;

    public LinkedBlockingQueue<Integer> actions;

    public Object playerKey;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer; // we added
        this.actions = new LinkedBlockingQueue<>(env.config.featureSize); // we added
        playerKey = new Object();
        tokens = new int[env.config.featureSize];
        for (int i = 0; i < tokens.length; i++)
            tokens[i] = -1;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();

        synchronized (this) {
            notifyAll();
        }
        // For starting all the threads gracefully.

        while (!terminate) {

            while (!actions.isEmpty() && freezeTime == -1 && !dealer.isWorking) {
                try {
                    synchronized (this) {
                        int currentSlot = actions.take();
                        if (table.slotToCard[currentSlot] != null) {
                            if (hasToken(currentSlot) >= 0) {
                                tokens[hasToken(currentSlot)] = -1; // update the tokens array
                                table.removeToken(id, currentSlot);
                                tokenCounter--;

                            } else if (tokenCounter < env.config.featureSize) {
                                table.placeToken(id, currentSlot);
                                tokenCounter++;

                                // update the tokens array
                                boolean bool = false;
                                for (int i = 0; i < tokens.length && !bool; i++) {
                                    if (tokens[i] == -1) {
                                        tokens[i] = currentSlot;
                                        bool = true;
                                    }
                                }

                                // checkMe
                                if (tokenCounter == env.config.featureSize) {
                                    synchronized (playerKey){
                                        checkMe = true;
                                        try {
                                            while (checkMe)
                                                playerKey.wait();
                                        }
                                        catch (InterruptedException e) {}
                                        synchronized (dealer) {
                                            dealer.notifyAll();
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                }
            }
        }

        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                synchronized (this) {
                    try {
                        while (actions.size() == env.config.featureSize) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                    }
                    Random key = new Random();
                    int slot = key.nextInt(env.config.tableSize);
                    keyPressed(slot);
                    try {
                        wait(20);
                    } catch (InterruptedException e) {
                    }
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (freezeTime == -1 && table.slotToCard[slot] != null) {
            synchronized (playerKey) {
                try {
                    while (dealer.isWorking)
                        playerKey.wait();
                    actions.put(slot);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        score++;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

        env.ui.setScore(id, score);
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        freezeTime = env.config.pointFreezeMillis;
        setFreezeTime();
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        freezeTime = env.config.penaltyFreezeMillis;
        setFreezeTime();

    }

    // get Score
    public int score() {
        return score;
    }

    /**
     * return the index in tokensArray if there is a token on slot
     */
    private int hasToken(int slot) {
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    public void setFreezeTime() {
            if (freezeTime != -1) {
                for (long t = freezeTime; t > 0 && !terminate; t -= 1000) {
                    try {
                        env.ui.setFreeze(this.id, t);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        env.logger.info("thread " + Thread.currentThread().getName() + " interrupted");
                        this.terminate();
                    }
                }
                if (!terminate) {
                    freezeTime = 0;
                    env.ui.setFreeze(id, freezeTime);
                }
                freezeTime = -1;
            }
    }

    public boolean isHuman() {
        return human;
    }
}
