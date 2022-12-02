package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.Main;

import java.util.List;
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
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

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
            removeAllCardsFromTable();
        }

        announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop()
    {
        reshuffleTime = System.currentTimeMillis() + (60*1000);

        while (!terminate && System.currentTimeMillis() < reshuffleTime) 
        {
            sleepUntilWokenOrTimeout(); 
            updateTimerDisplay(false);
            removeCardsFromTable(); // set
            placeCardsOnTable(); // set
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate()
    {

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
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable()
    {
        
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable()
    {
        Collections.shuffle(deck); // shuffle deck

        int slot = 0;
        int card = 0; 

        while (slot < table.slotToCard.length) 
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
        long m = Long.MAX_VALUE;
        Player p = null;

        for (Player player : players)
        {
            if (player.getAnnounceSetTime() < m) 
            {
                m = player.getAnnounceSetTime();
                p = player;
            }
        }

        if (p != null) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset)
    {
        if (reset) {reshuffleTime = System.currentTimeMillis() + (60*1000);}       
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
}
