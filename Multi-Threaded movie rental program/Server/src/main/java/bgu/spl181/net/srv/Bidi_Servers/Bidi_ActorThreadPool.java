package bgu.spl181.net.srv.Bidi_Servers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Bidi_ActorThreadPool
{
    //------------------------------------------------------------------------------------------------------------------
    //                                                  Actor Thread Pool
    //------------------------------------------------------------------------------------------------------------------
    /**                                                      Fields                                                   */
    //------------------------------------------------------------------------------------------------------------------
    private final Map<Object, Queue<Runnable>> acts;
    private final ReadWriteLock actsRWLock;
    private final Set<Object> playingNow;
    private final ExecutorService threads;
    //------------------------------------------------------------------------------------------------------------------
    /**                                                      Methods                                                  */
    //------------------------------------------------------------------------------------------------------------------
    public Bidi_ActorThreadPool (int threads)
    {
        this.threads = Executors.newFixedThreadPool(threads);
        acts = new WeakHashMap<> ();
        playingNow = ConcurrentHashMap.newKeySet();
        actsRWLock = new ReentrantReadWriteLock ();
    }
    //------------------------------------------------------------------------------------------------------------------
    public void submit(Object act, Runnable r)
    {
        synchronized (act)
        {
            if (!playingNow.contains(act))
            {
                playingNow.add(act);
                execute(r, act);
            }
            else
                pendingRunnablesOf(act).add(r);
        }
    }
    //------------------------------------------------------------------------------------------------------------------
    public void shutdown()
    { threads.shutdownNow(); }
    //------------------------------------------------------------------------------------------------------------------
    private Queue<Runnable> pendingRunnablesOf(Object act)
    {
        actsRWLock.readLock().lock();
        Queue<Runnable> pendingRunnables = acts.get(act);
        actsRWLock.readLock().unlock();
        
        if (pendingRunnables == null)
        {
            actsRWLock.writeLock().lock();
            acts.put(act, pendingRunnables = new LinkedList<> ());
            actsRWLock.writeLock().unlock();
        }
        
        return pendingRunnables;
    }
    //------------------------------------------------------------------------------------------------------------------
    private void execute(Runnable r, Object act)
    {
        threads.execute( () ->
        {
            try { r.run(); }
            finally { complete(act); }
        });
    }
    //------------------------------------------------------------------------------------------------------------------
    private void complete(Object act)
    {
        synchronized (act)
        {
            Queue<Runnable> pending = pendingRunnablesOf(act);
            
            if (pending.isEmpty())
                playingNow.remove(act);
            
            else
                execute(pending.poll(), act);
        }
    }
    //------------------------------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------
}
