//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.jmeter.plugin;

import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.IntegerProperty;
import org.apache.jmeter.testelement.property.LongProperty;
import org.apache.jmeter.testelement.schema.PropertiesAccessor;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.ListenerNotifier;

import org.apache.jmeter.threads.ThreadGroupSchema;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.ListedHashTree;
import org.apache.jorphan.util.JMeterStopTestException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CustomThreadGroup extends AbstractThreadGroup {
    private static final long serialVersionUID = 282L;
    private static final Logger log = LoggerFactory.getLogger(ThreadGroup.class);
    private static final long WAIT_TO_DIE;
    private static final int RAMPUP_GRANULARITY;
    public static final String RAMP_TIME = "ThreadGroup.ramp_time";
    public static final String DELAYED_START = "ThreadGroup.delayedStart";
    public static final String SCHEDULER = "ThreadGroup.scheduler";
    public static final String DURATION = "ThreadGroup.duration";
    public static final String DELAY = "ThreadGroup.delay";
    private transient Thread threadStarter;
    private final ConcurrentHashMap<JMeterThread, Thread> allThreads = new ConcurrentHashMap();
    private transient Object addThreadLock = new Object();
    private volatile boolean running = false;
    private int groupNumber;
    private boolean delayedStartup;
    private ListenerNotifier notifier;
    private ListedHashTree threadGroupTree;

    public CustomThreadGroup() {
    }

    public ThreadGroupSchema getSchema() {
        return ThreadGroupSchema.INSTANCE;
    }

    public @NotNull PropertiesAccessor<? extends CustomThreadGroup, ? extends ThreadGroupSchema> getProps() {
        return new PropertiesAccessor(this, this.getSchema());
    }

    public void setScheduler(boolean scheduler) {
        this.setProperty(new BooleanProperty("ThreadGroup.scheduler", scheduler));
    }

    public boolean getScheduler() {
        return this.getPropertyAsBoolean("ThreadGroup.scheduler");
    }

    public long getDuration() {
        return this.getPropertyAsLong("ThreadGroup.duration");
    }

    public void setDuration(long duration) {
        this.setProperty(new LongProperty("ThreadGroup.duration", duration));
    }

    public long getDelay() {
        return this.getPropertyAsLong("ThreadGroup.delay");
    }

    public void setDelay(long delay) {
        this.setProperty(new LongProperty("ThreadGroup.delay", delay));
    }

    public void setRampUp(int rampUp) {
        this.setProperty(new IntegerProperty("ThreadGroup.ramp_time", rampUp));
    }

    public int getRampUp() {
        return this.getPropertyAsInt("ThreadGroup.ramp_time");
    }

    private boolean isDelayedStartup() {
        return this.get(this.getSchema().getDelayedStart());
    }

    private void scheduleThread(JMeterThread thread, long now) {
        if (this.getScheduler()) {
            if (this.getDelay() >= 0L) {
                thread.setStartTime(this.getDelay() * 1000L + now);
                if (this.getDuration() > 0L) {
                    thread.setEndTime(this.getDuration() * 1000L + thread.getStartTime());
                    thread.setScheduled(true);
                } else {
                    throw new JMeterStopTestException("Invalid duration " + this.getDuration() + " set in Thread Group:" + this.getName());
                }
            } else {
                throw new JMeterStopTestException("Invalid delay " + this.getDelay() + " set in Thread Group:" + this.getName());
            }
        }
    }

    public void start(int groupNum, ListenerNotifier notifier, ListedHashTree threadGroupTree, StandardJMeterEngine engine) {
        this.running = true;
        this.groupNumber = groupNum;
        this.notifier = notifier;
        this.threadGroupTree = threadGroupTree;
        int numThreads = this.getNumThreads();
        int rampUpPeriodInSeconds = this.getRampUp();
        this.delayedStartup = this.isDelayedStartup();
        log.info("Starting thread group... number={} threads={} ramp-up={} delayedStart={}", new Object[]{this.groupNumber, numThreads, rampUpPeriodInSeconds, this.delayedStartup});
        if (this.delayedStartup) {
//            this.threadStarter = new Thread(new CustomThreadGroup.ThreadStarter(notifier, threadGroupTree, engine), this.getName() + "-ThreadStarter");
            this.threadStarter = Thread.ofVirtual().unstarted(new CustomThreadGroup.ThreadStarter(notifier, threadGroupTree, engine));
            this.threadStarter.setName(this.getName() + "-ThreadStarter");
            this.threadStarter.setDaemon(true);
            this.threadStarter.start();
        } else {
            JMeterVariables variables = JMeterContextService.getContext().getVariables();
            long lastThreadStartInMillis = 0L;
            int delayForNextThreadInMillis = 0;
            int perThreadDelayInMillis = Math.round((float)rampUpPeriodInSeconds * 1000.0F / (float)numThreads);

            for(int threadNum = 0; this.running && threadNum < numThreads; ++threadNum) {
                long nowInMillis = System.currentTimeMillis();
                if (threadNum > 0) {
                    long timeElapsedToStartLastThread = nowInMillis - lastThreadStartInMillis;
                    delayForNextThreadInMillis = (int)((long)delayForNextThreadInMillis + ((long)perThreadDelayInMillis - timeElapsedToStartLastThread));
                }

                if (log.isDebugEnabled()) {
                    log.debug("Computed delayForNextThreadInMillis:{} for thread:{}", delayForNextThreadInMillis, Thread.currentThread().getId());
                }

                lastThreadStartInMillis = nowInMillis;
                this.startNewThread(notifier, threadGroupTree, engine, threadNum, variables, nowInMillis, Math.max(0, delayForNextThreadInMillis));
            }
        }

        log.info("Started thread group number {}", this.groupNumber);
    }

    private JMeterThread startNewThread(ListenerNotifier notifier, ListedHashTree threadGroupTree, StandardJMeterEngine engine, int threadNum, JMeterVariables variables, long now, int delay) {
        JMeterThread jmThread = this.makeThread(engine, this, notifier, this.groupNumber, threadNum, cloneTree(threadGroupTree), variables);
        this.scheduleThread(jmThread, now);
        jmThread.setInitialDelay(delay);
//        Thread newThread = new Thread(jmThread, jmThread.getThreadName());
        Thread newThread = Thread.ofVirtual().unstarted(jmThread);
        newThread.setName(jmThread.getThreadName());
        this.registerStartedThread(jmThread, newThread);
        newThread.start();
        return jmThread;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.addThreadLock = new Object();
    }

    private void registerStartedThread(JMeterThread jMeterThread, Thread newThread) {
        this.allThreads.put(jMeterThread, newThread);
    }

    public JMeterThread addNewThread(int delay, StandardJMeterEngine engine) {
        long now = System.currentTimeMillis();
        JMeterContext context = JMeterContextService.getContext();
        int numThreads;
        synchronized(this.addThreadLock) {
            numThreads = this.getNumThreads();
            this.setNumThreads(numThreads + 1);
        }

        JMeterThread newJmThread = this.startNewThread(this.notifier, this.threadGroupTree, engine, numThreads, context.getVariables(), now, delay);
        JMeterContextService.addTotalThreads(1);
        log.info("Started new thread in group {}", this.groupNumber);
        return newJmThread;
    }

    public boolean stopThread(String threadName, boolean now) {
        Iterator var3 = this.allThreads.entrySet().iterator();

        Map.Entry threadEntry;
        JMeterThread jMeterThread;
        do {
            if (!var3.hasNext()) {
                return false;
            }

            threadEntry = (Map.Entry)var3.next();
            jMeterThread = (JMeterThread)threadEntry.getKey();
        } while(!jMeterThread.getThreadName().equals(threadName));

        stopThread(jMeterThread, (Thread)threadEntry.getValue(), now);
        return true;
    }

    private static void stopThread(JMeterThread jmeterThread, Thread jvmThread, boolean interrupt) {
        jmeterThread.stop();
        jmeterThread.interrupt();
        if (interrupt && jvmThread != null) {
            jvmThread.interrupt();
        }

    }

    public void threadFinished(JMeterThread thread) {
        if (log.isDebugEnabled()) {
            log.debug("Ending thread {}", thread.getThreadName());
        }

        this.allThreads.remove(thread);
    }

    public void tellThreadsToStop(boolean now) {
        this.running = false;
        if (this.delayedStartup) {
            try {
                this.threadStarter.interrupt();
            } catch (Exception var3) {
                log.warn("Exception occurred interrupting ThreadStarter", var3);
            }
        }

        this.allThreads.forEach((key, value) -> {
            stopThread(key, value, now);
        });
    }

    public void tellThreadsToStop() {
        this.tellThreadsToStop(true);
    }

    public void stop() {
        this.running = false;
        if (this.delayedStartup) {
            try {
                this.threadStarter.interrupt();
            } catch (Exception var2) {
                log.warn("Exception occurred interrupting ThreadStarter", var2);
            }
        }

        this.allThreads.keySet().forEach(JMeterThread::stop);
    }

    public int numberOfActiveThreads() {
        return this.allThreads.size();
    }

    public boolean verifyThreadsStopped() {
        boolean stoppedAll = true;
        if (this.delayedStartup) {
            stoppedAll = verifyThreadStopped(this.threadStarter);
        }

        if (stoppedAll) {
            Iterator var2 = this.allThreads.values().iterator();

            Thread t;
            do {
                if (!var2.hasNext()) {
                    return true;
                }

                t = (Thread)var2.next();
            } while(verifyThreadStopped(t));

            return false;
        } else {
            return false;
        }
    }

    private static boolean verifyThreadStopped(Thread thread) {
        boolean stopped = true;
        if (thread != null && thread.isAlive()) {
            try {
                thread.join(WAIT_TO_DIE);
            } catch (InterruptedException var3) {
                Thread.currentThread().interrupt();
            }

            if (thread.isAlive()) {
                stopped = false;
                if (log.isWarnEnabled()) {
                    log.warn("Thread won't exit: {}", thread.getName());
                }
            }
        }

        return stopped;
    }

    public void waitThreadsStopped() {
        if (this.delayedStartup) {
            waitThreadStopped(this.threadStarter);
        }

        while(!this.allThreads.isEmpty()) {
            this.allThreads.values().forEach(CustomThreadGroup::waitThreadStopped);
        }

    }

    private static void waitThreadStopped(Thread thread) {
        if (thread != null) {
            while(thread.isAlive()) {
                try {
                    thread.join(WAIT_TO_DIE);
                } catch (InterruptedException var2) {
                    Thread.currentThread().interrupt();
                }
            }

        }
    }

    static {
        WAIT_TO_DIE = DEFAULT_THREAD_STOP_TIMEOUT.toMillis();
        RAMPUP_GRANULARITY = JMeterUtils.getPropDefault("jmeterthread.rampup.granularity", 1000);
    }

    class ThreadStarter implements Runnable {
        private final ListenerNotifier notifier;
        private final ListedHashTree threadGroupTree;
        private final StandardJMeterEngine engine;
        private final JMeterVariables variables;

        public ThreadStarter(ListenerNotifier notifier, ListedHashTree threadGroupTree, StandardJMeterEngine engine) {
            this.notifier = notifier;
            this.threadGroupTree = threadGroupTree;
            this.engine = engine;
            this.variables = JMeterContextService.getContext().getVariables();
        }

        private void pause(long ms) {
            try {
                TimeUnit.MILLISECONDS.sleep(ms);
            } catch (InterruptedException var4) {
                Thread.currentThread().interrupt();
            }

        }

        private void delayBy(long delay) {
            if (delay > 0L) {
                long start = System.currentTimeMillis();
                long end = start + delay;

                long now;
                for(long pause = (long)CustomThreadGroup.RAMPUP_GRANULARITY; CustomThreadGroup.this.running && (now = System.currentTimeMillis()) < end; this.pause(pause)) {
                    long togo = end - now;
                    if (togo < pause) {
                        pause = togo;
                    }
                }
            }

        }

        public void run() {
            try {
                JMeterContextService.getContext().setVariables(this.variables);
                long endtime = 0L;
                boolean usingScheduler = CustomThreadGroup.this.getScheduler();
                if (usingScheduler) {
                    if (CustomThreadGroup.this.getDelay() > 0L) {
                        this.delayBy(CustomThreadGroup.this.getDelay() * 1000L);
                    }

                    endtime = CustomThreadGroup.this.getDuration();
                    if (endtime > 0L) {
                        endtime = endtime * 1000L + System.currentTimeMillis();
                    }
                }

                int numThreads = CustomThreadGroup.this.getNumThreads();
                float rampUpOriginInMillis = (float)CustomThreadGroup.this.getRampUp() * 1000.0F;
                long startTimeInMillis = System.currentTimeMillis();

                for(int threadNumber = 0; CustomThreadGroup.this.running && threadNumber < numThreads; ++threadNumber) {
                    if (threadNumber > 0) {
                        long elapsedInMillis = System.currentTimeMillis() - startTimeInMillis;
                        int perThreadDelayInMillis = Math.round((rampUpOriginInMillis - (float)elapsedInMillis) / (float)(numThreads - threadNumber));
                        this.pause((long)Math.max(0, perThreadDelayInMillis));
                    }

                    if (usingScheduler && System.currentTimeMillis() > endtime) {
                        break;
                    }

                    JMeterThread jmThread = CustomThreadGroup.this.makeThread(this.engine, CustomThreadGroup.this, this.notifier, CustomThreadGroup.this.groupNumber, threadNumber, AbstractThreadGroup.cloneTree(this.threadGroupTree), this.variables);
                    jmThread.setInitialDelay(0);
                    if (usingScheduler) {
                        jmThread.setScheduled(true);
                        jmThread.setEndTime(endtime);
                    }

                    Thread newThread = new Thread(jmThread, jmThread.getThreadName());
                    newThread.setDaemon(false);
                    CustomThreadGroup.this.registerStartedThread(jmThread, newThread);
                    newThread.start();
                }
            } catch (Exception var12) {
                CustomThreadGroup.log.error("An error occurred scheduling delay start of threads for Thread Group: {}", CustomThreadGroup.this.getName(), var12);
            }

        }
    }
}
