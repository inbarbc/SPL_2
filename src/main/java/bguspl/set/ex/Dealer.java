package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.*;

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
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private Queue<Player> queue = new LinkedList<>();
    private Integer set[] = new Integer[3];

    /**
     * The thread representing the current dealer.
     */
    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players)
     {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());       
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() 
    {
        dealerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

        for (Player p : players)
        {
            Thread playerThread = new Thread(p);
            playerThread.start();
        }

        while (!shouldFinish()) 
        {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllTokensFromTable();
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() 
    {
        reshuffleTime = System.currentTimeMillis() + (61*1000);

        while (!terminate && System.currentTimeMillis() < reshuffleTime) 
        {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() 
    {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish()
    {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() 
    {
        if (set[0] != null)
        {
            for (int i = 0; i < set.length; i++)
            {
                table.removeCard(set[i]);
                set[i] = null;
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() 
    {
        Collections.shuffle(deck); // shuffle deck

        int slot = 0;
        int card = 0; 

        while (slot < table.slotToCard.length && !deck.isEmpty()) 
        {
            if (table.slotToCard[slot] == null)
            {
                table.placeCard(deck.get(card), slot);
                deck.remove(deck.get(card));
                card++;
            }
            slot++;
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() 
    {
        try 
        {
             if (queue.isEmpty()) {dealerThread.sleep(950);}
             else {CheckingPlayerSet(queue.remove());}
        }
        catch (InterruptedException e) {if (!queue.isEmpty()) {CheckingPlayerSet(queue.remove());}}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) 
    {
        if (reset) {reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;}       
        env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(),false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() 
    {
        for (int i = 0; i < table.slotToCard.length; i++)
        {
            if (table.slotToCard[i] != null)
            {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners()
    {

    }

    public void addToQueue(Player player)
    {
        queue.add(player);
    }

    public void removeAllTokensFromTable()
    {
        for (Player player : players)
        {
            player.removeAllTokensFromTable();
        }
    }

    public void interrupt()
    {
        dealerThread.interrupt();
    }

    public void removeTokensFromTable()
    {
        for (Player player : players)
        {
            player.removeTokensFromTable(set);
        }
    }

    public void CheckingPlayerSet(Player player)
    {
        int[] cards = new int[3];

        for (int i = 0; i < cards.length; i++)
        {
            cards[i] = table.slotToCard[player.getTokenToSlot(i)];
        }

        if (env.util.testSet(cards))
        {
            for (int i = 0; i < cards.length; i++)
            {
                set[i]= player.getTokenToSlot(i);
            }

            player.setState(State.Point);
            removeTokensFromTable();
            updateTimerDisplay(true);
        }
        else 
        {
            player.setState(State.Penalty);
        }
    }
}
