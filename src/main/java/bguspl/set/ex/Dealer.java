package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private Long[] announcements;
    private Integer[] set;  
    public boolean waitForTheDealerToReshuffle;
    private boolean warn;
    private long timer;

    public final int sizeOfASet = 3;
    public final long minimumTimeInterval = 990;
    public final long oneSecond = 950; 
    public final long tenMilliseconds = 5; 
    public final long resetTime = 0;

    public Dealer(Env env, Table table, Player[] players) 
    {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        announcements = new Long[players.length];
        set = new Integer[3];
        waitForTheDealerToReshuffle = true;
        warn = false;
        timer = System.currentTimeMillis();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() 
    {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");

        for (Player p : players)
        {
            Thread playerThread = new Thread(p);
            playerThread.start();
        }

        while (!shouldFinish()) 
        {
            waitForTheDealerToReshuffle = true;
            placeCardsOnTable();
            updateTimerDisplay(true);
            wakeUpThePlayers();     
            timerLoop();
            waitForTheDealerToReshuffle = true;
            removeAllTokensFromTable();
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() 
    {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) 
        {
            if (env.config.turnTimeoutMillis <= 0)
            {
                List<Integer> list = convertArrayToList(table.slotToCard);

                boolean notify = false;
                while (env.util.findSets(list, 1).size() == 0)
                {
                    waitForTheDealerToReshuffle = true; 
                    reshuffleTheDeck();
                    list = convertArrayToList(table.slotToCard);
                    notify = true;
                }
                if (notify) {wakeUpThePlayers();}
            }
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() 
    {
        for (Player player : players)
        {
            player.terminate();
        }
        terminate = true;
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
        if (set[0] != null & set[1] != null & set[2] != null)
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
        Collections.shuffle(deck); 
        int slot = 0;

        boolean hint = false;

        while (slot < table.slotToCard.length && !deck.isEmpty())
        {
            if (table.slotToCard[slot] == null)
            {
                table.placeCard(deck.get(0), slot);
                deck.remove(deck.get(0));
                hint = true;
            }
            slot++;
        }   
        if (!isThereASetInTheRemainingCards()) {terminate = true;}
        if (hint & env.config.hints) {table.hints();} 
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() 
    {
        if (isTheArrayEmpty(announcements))
        {
            try 
            {
                synchronized (this)
                {
                    if (warn) {wait(tenMilliseconds);}
                    else {wait(oneSecond);}
                }
            }
            catch (InterruptedException ignore) {CheckingPlayerSet(ThePlayerWhoAnnouncedFirst());}
        }
        else {CheckingPlayerSet(ThePlayerWhoAnnouncedFirst());}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) 
    {
        if (env.config.turnTimeoutMillis > 0)
        {
            if (reset) {reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; warn = false;}        
            long countdown = reshuffleTime-System.currentTimeMillis();
            if (countdown < env.config.turnTimeoutWarningMillis) {warn = true;}
            if (countdown < 0) {countdown = 0;}
            env.ui.setCountdown(countdown,warn);
        }
        else if (env.config.turnTimeoutMillis == 0)
        {
            if (reset) {timer = System.currentTimeMillis();}
            env.ui.setElapsed(System.currentTimeMillis() - timer);
        }
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
        int m = 0;
        int s = 0;

        for (Player player : players)
        {
            if (player.score() == m) {s++;}
            else if (player.score() > m) {m = player.score(); s = 1;}
        }

        int i = 0;
        int[] playersId = new int[s];
        for (Player player : players)
        {
            if (player.score() == m) {playersId[i] = player.id; i++;}
        }

        env.ui.announceWinner(playersId);
    }

    private void wakeUpThePlayers()
    {
        waitForTheDealerToReshuffle = false;

        for (Player player : players)
        {
            synchronized (player) {player.notifyAll();}
        }
    }

    public void wakeUpTheDealer()
    {
        synchronized (this) {notify();}
    }

    public void removeAllTokensFromTable()
    {
        for (Player player : players)
        {
            player.removeAllTokensFromTable();
        }
    }

    private List<Integer> convertArrayToList(Integer[] array)
    {
        List<Integer> list = new LinkedList<>();

        for (int i = 0; i < array.length; i++)
        {
            if (array[i] != null) {list.add(array[i]);}
        }

        return list;
    }

    private void reshuffleTheDeck()
    {
        removeAllTokensFromTable();
        removeAllCardsFromTable();
        placeCardsOnTable();
        updateTimerDisplay(true);
    }

    private boolean isThereASetInTheRemainingCards()
    {
        List<Integer> cards = new LinkedList<>();

        for (int i = 0; i < table.slotToCard.length; i++)
        {
            if (table.slotToCard[i] != null) {cards.add(table.slotToCard[i]);}
        }

        for (int j = 0; j < deck.size(); j++)
        {
            cards.add(deck.get(j));
        }

        return env.util.findSets(cards, 1).size() > 0;
    }

    private boolean isTheArrayEmpty(Long[] array)
    {
        for (int i = 0; i < array.length; i++)
        {
            if (array[i] != null) {return false;}
        }
        return true;
    }

    private int ThePlayerWhoAnnouncedFirst()
    {
        Long m = Long.MAX_VALUE;
        int id = -1;

        for (int i = 0; i < announcements.length; i++)
        {
            if (announcements[i] != null && announcements[i] < m) {m = announcements[i]; id = i;}
        }
        
        announcements[id] = null;
        return id;     
    }

    public void CheckingPlayerSet(int id)
    {      

        env.logger.info("dealer checking player "+(id+1)); 
        if (players[id].getTokensToSlots().size() == sizeOfASet & areTheCardsAvailable(players[id]))
        {
            int[] cards = new int[sizeOfASet];

            for (int i = 0; i < cards.length; i++)
            {
                cards[i] = table.slotToCard[players[id].getTokensToSlots().get(i)];
            }

            if (env.util.testSet(cards))
            {
                for (int i = 0; i < cards.length; i++)
                {
                    set[i]= players[id].getTokensToSlots().get(i);
                }
                
                env.logger.info("Player "+(id+1)+" get set :"+ set.toString());  

                removeTokensFromTable(players[id]);
                removeCardsFromTable();
                placeCardsOnTable();
                updateTimerDisplay(true);        
                players[id].setState(State.Point);       
            }
            else {players[id].setState(State.Penalty);}
        }
        else {players[id].setState(State.Continue);}
        env.logger.info("dealer done checking player "+(id+1)); 
    }

    public boolean areTheCardsAvailable(Player player)
    {
        for (int i = 0; i < player.getTokensToSlots().size(); i++)
        {
           if (table.slotToCard[player.getTokensToSlots().get(i)] == null) {return false;}
        }
        return true;
    }

    public void removeTokensFromTable(Player player)
    {
        for (int i = 0; i < player.getTokensToSlots().size(); i++)
        {
            table.slotToCard[player.getTokensToSlots().get(i)] = null;
        }

        for (Player p : players)
        {
            p.removeTokensFromTable(set);
        }
    }

    public void updateTheArray(Long time, int id)
    {
        announcements[id] = time;      
    }
}
