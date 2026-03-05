import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ProducerConsumerSimulation {

    private static int nProducers;
    private static int nConsumers;
    private static int bufferSize;
    private static int messageCount;

    private static final AtomicInteger totalProduced = new AtomicInteger(0);
    private static final AtomicInteger totalConsumed = new AtomicInteger(0);
    private static final AtomicInteger threadsAlive = new AtomicInteger();
    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition notFull = lock.newCondition();
    private static final Condition notEmpty = lock.newCondition();

    private static String[] ringBuffer;
    private static int head = 0;
    private static int tail = 0;
    private static int count = 0;
    private static int logStep;

    public static void main(String[] args) {
        if (!checkArguments(args)) {
            return;
        }
        logStep = (messageCount / 25) == 0 ? 1 : (messageCount / 25);

        threadsAlive.set(nProducers + nConsumers);
        ringBuffer = new String[bufferSize];
        Date startTime = new Date();
        System.out.println("Начало работы многопоточной программы");

        new Thread(() -> {
            while (true) {
                if (threadsAlive.get() <= 0) {
                    double seconds = (new Date().getTime() - startTime.getTime()) / 1000d;
                    System.out.printf("Время выполнения программы составило %.4f секунд\n", seconds);
                    break;
                }
            }
        }).start();

        for (int i = 0; i < nProducers; i++) {
            new Producer(i + 1).start();
        }
        for (int i = 0; i < nConsumers; i++) {
            new Consumer(i + 1).start();
        }
    }

    private static boolean checkArguments(String[] args) {
        try {
            nProducers = Integer.parseInt(args[0]);
            nConsumers = Integer.parseInt(args[1]);
            bufferSize = Integer.parseInt(args[2]);
            messageCount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.out.println("В качестве аргументов запуска можно передавать только строку");
            return false;
        }
        if (nProducers <= 0) {
            System.out.println("Количество производителей должно быть больше 0");
            return false;
        } else if (nConsumers <= 0) {
            System.out.println("Количество потребителей должно быть больше 0");
            return false;
        } else if (bufferSize <= 0) {
            System.out.println("Размер буфера должен быть больше 0");
            return false;
        } else if (messageCount <= 0) {
            System.out.println("Количество сообщений должен быть больше 0");
            return false;
        }
        return true;
    }

    private static class Producer extends Thread {
        private static final String name = "Производитель";

        private Producer(int number) {
            super(name + " №" + number);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int producedNum = totalProduced.incrementAndGet();
                    if (producedNum > messageCount) {
                        threadsAlive.decrementAndGet();
                        break;
                    }
                    String message = "Сообщение №" + producedNum;
                    put(message);
                }
            } catch (InterruptedException ignored) {}
        }

        private void put(String message) throws InterruptedException {
            lock.lock();
            try {
                while (count == ringBuffer.length) {
                    notFull.await();
                }
                int position = head;
                ringBuffer[head] = message;
                head = (head + 1) % ringBuffer.length;
                count++;

                int messageNumber = Integer.parseInt(message.substring(message.indexOf('№') + 1));
                if (messageNumber % logStep == 0 || messageNumber == 1 || messageNumber == messageCount) {
                    System.out.printf("%s добавил %s на позицию %d\n", getName(), message, position);
                }
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    private static class Consumer extends Thread {
        private static final String name = "Потребитель";

        private Consumer(int number) {
            super(name + " №" + number);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int consumedNum = totalConsumed.incrementAndGet();
                    if (consumedNum > messageCount) {
                        threadsAlive.decrementAndGet();
                        break;
                    }
                    take();
                }
            } catch (InterruptedException ignored) {}
        }

        private void take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) {
                    notEmpty.await();
                }
                int position = tail;
                String message = ringBuffer[tail];
                ringBuffer[tail] = null;
                tail = (tail + 1) % ringBuffer.length;
                count--;

                int messageNumber = Integer.parseInt(message.substring(message.indexOf('№') + 1));
                if (messageNumber % logStep == 0 || messageNumber == 1 || messageNumber == messageCount) {
                    System.out.printf("%s забрал %s на позиции %d\n", getName(), message, position);
                }
                notFull.signal();
            } finally {
                lock.unlock();
            }
        }
    }
}