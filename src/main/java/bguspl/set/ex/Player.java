package bguspl.set.ex;

import bguspl.set.Env;

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
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private final Integer[] tokenToSlot;
    private long announceSetTime;
    private int tokens;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human)
    {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        tokenToSlot = new Integer[3];
        announceSetTime = Long.MAX_VALUE;
        tokens = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run()
     {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();

        while (!terminate)
        {
            if (tokens == 3) {announceSetTime = System.currentTimeMillis();}
        }

        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence()
     {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() ->
        {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) 
            {
                // TODO implement player key press simulator

                try {synchronized (this) { wait(); }}
                catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate()
    {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot)
    {
        boolean toPlaceToken = true;

        for (int i = 0; i < tokenToSlot.length; i++)
        {
            if (tokenToSlot[i] != null && tokenToSlot[i] == slot)
            {
                table.removeToken(id, slot);
                tokenToSlot[i] = null;
                toPlaceToken = false;
                tokens--;
                break;
            }
        }

        if (toPlaceToken)
        {
            for (int i = 0; i < tokenToSlot.length; i++)
            {
                if (tokenToSlot[i] == null)
                {
                    table.placeToken(id, slot);
                    tokenToSlot[i] = slot;
                    tokens++;               
                    break;
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
        // TODO implement

        int ignoreNum = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        announceSetTime = Long.MAX_VALUE;
        tokens = 0;
        resetTokenToSlot();
        
        env.ui.setFreeze(id, 1000);
        if (human) try { playerThread.sleep(1000); } catch (InterruptedException ignored) {};
        if (!human) try { aiThread.sleep(1000); } catch (InterruptedException ignored) {};
        env.ui.setFreeze(id, -1);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty()
    {
        // TODO implement
        announceSetTime = Long.MAX_VALUE;
        tokens = 0;
        resetTokenToSlot();

        //try { Thread.currentThread().sleep(3000); } catch (InterruptedException ignored) {};
        
        // its not ok because it stops all the game
        
    }

    public void resetTokenToSlot()
    {
        for(int i = 0; i < tokenToSlot.length; i++)
        {
            tokenToSlot[i] = null;
        }
    }

    public int getScore() 
    {
        return score;
    }

    public int getTokens() 
    {
        return tokens;
    }

    public long getAnnounceSetTime()
    {
        return announceSetTime;
    }

    public int getTokenToSlot(int i)
    {
        return tokenToSlot[i];
    }

    public Thread getThread()
    {
        return playerThread;
    }
}
