package bguspl.set.ex;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

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
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private final Dealer dealer;
    private List<Integer> tokenToSlot;
    private Queue<Integer> actionsQueue;
    private Long announcementTime;
    private boolean waitForTheDealerToCheckTheSet;
    private boolean penalty;
    private boolean point;
    private boolean wait;
    enum State {Penalty,Point,Continue}

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

        this.dealer = dealer;
        tokenToSlot = new LinkedList<>();
        actionsQueue = new LinkedList<>();
        announcementTime = null;
        waitForTheDealerToCheckTheSet = false;
        penalty = false;
        point = false;
        wait = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() 
    {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) 
        {
            while (dealer.waitForTheDealerToReshuffle)
            {
                try {synchronized (this) {wait();}}
                catch (InterruptedException ignore) {}
            }

            // while (actionsQueue.isEmpty() & human)
            // {
            //     try {synchronized (this) {wait();}}
            //     catch (InterruptedException ignore) {}
            // }

            keyOperation();

            //synchronized (actionsQueue) {actionsQueue.notify();} 
        
            if (point) {removeAllTokensFromTable(); point();}
            else if (penalty) {penalty(); if (!human) {removeAllTokensFromTable();}}
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() 
    {
        aiThread = new Thread(() -> 
        {
            Random random = new Random();
            int bound = table.slotToCard.length;

            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) 
            {
                // while (actionsQueue.size() == dealer.sizeOfASet)
                // {
                //     try {synchronized (actionsQueue) {actionsQueue.wait();}}
                //     catch (InterruptedException ignore) {}
                // }

                keyPressed(random.nextInt(bound));
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
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
        if (actionsQueue.size() < dealer.sizeOfASet & !penalty & !point & table.slotToCard[slot] != null
        & !dealer.waitForTheDealerToReshuffle & !waitForTheDealerToCheckTheSet)
        {
            synchronized (actionsQueue) {actionsQueue.add(slot);}
            //synchronized (this) {notifyAll();}
        }
    }

    /**
     * Award a point to a player and perform other related actions.A
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() 
    {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        long targetTime = System.currentTimeMillis() + env.config.pointFreezeMillis;

        while (targetTime - System.currentTimeMillis() > dealer.minimumTimeInterval)
        {
            env.ui.setFreeze(id, targetTime - System.currentTimeMillis());
            try {Thread.sleep(dealer.oneSecond);}
            catch (InterruptedException ignore) {}
        }
        env.ui.setFreeze(id, dealer.resetTime);
        point = false;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() 
    {
        long targetTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;

        while (targetTime - System.currentTimeMillis() > dealer.minimumTimeInterval)
        {
            env.ui.setFreeze(id, targetTime - System.currentTimeMillis());
            try {Thread.sleep(dealer.oneSecond);}
            catch (InterruptedException ignore) {}
        }
        env.ui.setFreeze(id, dealer.resetTime);
        penalty = false;
    }

    public int score() 
    {
        return score;
    }

    public void keyOperation()
    {
        env.logger.info("player " + (id+1) + " actionsQueue size = " + actionsQueue.size());
        env.logger.info("player " + (id+1) + " actionsQueue last = " + actionsQueue.peek());
        env.logger.info("player " + (id+1) + " number of tokens = " + tokenToSlot.size());

        // while (!actionsQueue.isEmpty() && actionsQueue.peek() == null) 
        // {
        //     env.logger.info("player " + (id+1) + " ckeck actionsQueue.peek() " + actionsQueue.peek());    
        //     actionsQueue.poll();
        // }
        synchronized (actionsQueue)
        {
            if (!actionsQueue.isEmpty())
            {
                env.logger.info("player " + (id+1) + " keyOp");
                Integer slot = actionsQueue.poll();
                boolean toPlaceToken = true;

                if (tokenToSlot.contains(slot))
                {
                    table.removeToken(id, slot);
                    tokenToSlot.remove(slot);
                    toPlaceToken = false;
                }

                if (toPlaceToken & tokenToSlot.size() < dealer.sizeOfASet)
                {
                    table.placeToken(id, slot);
                    tokenToSlot.add(slot);
                    if (tokenToSlot.size() == dealer.sizeOfASet) 
                    {
                        waitForTheDealerToCheckTheSet = true;
                        announcementTime = System.currentTimeMillis();  
                        dealer.updateTheArray(announcementTime, id);
                        dealer.wakeUpTheDealer();

                        while (waitForTheDealerToCheckTheSet)
                        { 
                            env.logger.info("player "+(id+1) + " " + Arrays.toString(tokenToSlot.toArray()));
                            env.logger.info("player "+(id+1)+" waiting");  

                            try {synchronized (this) {wait(5);}}
                            catch (InterruptedException ignore) {}
                        }      
                        env.logger.info("player "+(id+1)+" done waiting");   
                    } 
                }    
            }
        } 
    }     

    public void removeAllTokensFromTable()
    {
        for (int i = 0; i < tokenToSlot.size(); i++)
        {
            table.removeToken(id, tokenToSlot.get(i));
        }
        tokenToSlot.clear();
        announcementTime = null;;
    }

    public void removeTokensFromTable(Integer[] array)
    {
        for (int i = 0; i < array.length; i++)
        {
            if (tokenToSlot.contains(array[i]))
            {
                table.removeToken(id, array[i]);
                tokenToSlot.remove(array[i]);
            }
        }
    }

    public List<Integer> getTokensToSlots()
    {
        return tokenToSlot;
    }

    public Queue getActionsQueue()
    {
        return actionsQueue;
    }

    public void setState(State state)
    {
        if (state == State.Penalty) {penalty = true;}
        else if (state == State.Point) {point = true;}
        else {removeAllTokensFromTable();}
        waitForTheDealerToCheckTheSet = false;
        announcementTime = null;      
        synchronized (this) {notifyAll();}
    }
}
