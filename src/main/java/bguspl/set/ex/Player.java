package bguspl.set.ex;

import java.util.logging.Level;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Queue;
import java.util.List;
import java.util.Random;

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

    private int numberOfTokens;
    private final Dealer dealer;
    private List<Integer> tokenToSlot;
    private Queue<Integer> queue;
    private boolean notifyTheDealer;
    private boolean penalty;
    private boolean point;
    enum State {Penalty,Point}

    private boolean hint;
    private boolean random;

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
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;

        queue = new LinkedList<>();
        tokenToSlot = new LinkedList<>();
        numberOfTokens = 0;
        notifyTheDealer = false;
        penalty = false;
        point = false;

        hint = false;
        random = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() 
    {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate)
        {
            keyOperation();

            if (point) {point(); point = false;}
            else if (penalty) {penalty(); penalty = false;}
        }

        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
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
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

            while (!terminate) 
            {
                if (!hint & !random)
                {
                    Random r = new Random();
                    int bound = 10;

                    if (r.nextInt(bound) > 7) {random = true;}
                    else {hint = true;}
                }

                if (hint) hint();
                else {random();}

                try {Thread.sleep(150);}
                catch (InterruptedException ignore) {}

                keyOperation();
            
                if (point) {point(); point = false; removeAllTokensFromTable();}
                else if (penalty) {penalty(); penalty = false; removeAllTokensFromTable();}
            
                // try {synchronized (this) {wait();}}
                // catch (InterruptedException ignored) {}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() 
    {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) 
    {
        if (queue.size() < 3 & !dealer.waitForTheDealer() &
        !penalty & !point & table.slotToCard[slot] != null)
        {
            queue.add(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() 
    {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        removeAllTokensFromTable();

        long targetTime = System.currentTimeMillis() + env.config.pointFreezeMillis;

        while (System.currentTimeMillis() < targetTime)
        {
            env.ui.setFreeze(id, targetTime - System.currentTimeMillis());

            try {Thread.sleep(950);}
            catch (InterruptedException ignore) {}
        }
        env.ui.setFreeze(id, 0);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() 
    {
        long targetTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;

        while (System.currentTimeMillis() < targetTime)
        {
            env.ui.setFreeze(id, targetTime - System.currentTimeMillis());

            try {Thread.sleep(950);}
            catch (InterruptedException ignore) {}
        }
        env.ui.setFreeze(id, 0);
    }

    public int getScore() 
    {
        return score;
    }

    public int getNumberOfTokens() 
    {
        return numberOfTokens;
    }

    public List<Integer> getTokensToSlots()
    {
        return tokenToSlot;
    }

    public void removeAllTokensFromTable()
    {
        for (int i = 0; i < tokenToSlot.size(); i++)
        {
            table.removeToken(id, tokenToSlot.get(i));
        }
        tokenToSlot.clear();
        notifyTheDealer = false;
    }

    public void removeTokensFromTable(Integer set[])
    {
        for (int i = 0; i < set.length; i++)
        {
            if (tokenToSlot.contains(set[i]))
            {
                table.removeToken(id, set[i]);
                tokenToSlot.remove(set[i]);
            }
        }
    }

    public void setState(State state)
    {
        if (state == State.Penalty) {penalty = true;}
        else if (state == State.Point) {point = true;}
    }

    public void keyOperation()
    {
        if (!queue.isEmpty())
        {
            Integer slot = queue.remove();
            boolean toPlaceToken = true;

            if (tokenToSlot.contains(slot))
            {
                table.removeToken(id, slot);
                tokenToSlot.remove(slot);
                toPlaceToken = false;
                notifyTheDealer = false;
            }

            if (toPlaceToken & tokenToSlot.size() < 3)
            {
                table.placeToken(id, slot);
                tokenToSlot.add(slot);
                if (tokenToSlot.size() == 3) {notifyTheDealer = true;} 
            }
        
            if (notifyTheDealer) 
            {
                dealer.addToQueue(this); 
                dealer.interrupt();
                notifyTheDealer = false;
            }
        }       
    }

    public void hint()
    {
        if (tokenToSlot.size() == 0)
        {
            tokenToSlot = table.getSet();
            if (tokenToSlot == null) {hint = false; random = true; tokenToSlot = new LinkedList<>();}
            else {keyPressed(tokenToSlot.get(0));}
        }
        else if (tokenToSlot.size() > 0)
        {
            if (tokenToSlot.size() == 1) {hint = false;}
            keyPressed(tokenToSlot.get(0));
        }
    }

    public void random()
    {
        if (tokenToSlot.size() == 0)
        {
            Random r = new Random();
            int bound = 11;

            while(tokenToSlot.size() < 3)
            {
                int n = r.nextInt(bound);
                if (!tokenToSlot.contains(n)) {tokenToSlot.add(n);}
            }

            keyPressed(tokenToSlot.get(0));
        }
        else if (tokenToSlot.size() > 0)
        {
            if (tokenToSlot.size() == 1) {random = false;}
            keyPressed(tokenToSlot.get(0));
        }
    }
}
