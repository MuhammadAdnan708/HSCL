import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class TicketSellingSystem {
    private static final int TOTAL_TICKETS = 100;
    private int tickets = TOTAL_TICKETS;
    private final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) {
        TicketSellingSystem system = new TicketSellingSystem();
        system.startSelling();
    }

    public void startSelling() {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        for (int i = 1; i <= 3; i++) {
            final int windowId = i;
            executor.execute(() -> {
                while (true) {
                    int ticketNumber = sellTicket(windowId);
                    if (ticketNumber == -1) {
                        break;
                    }
                    try {
                        Thread.sleep(50); // simulate time taken to sell a ticket
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("All tickets have been sold.");
    }

    private int sellTicket(int windowId) {
        lock.lock();
        try {
            if (tickets > 0) {
                int soldTicket = tickets;
                tickets--;
                System.out.println("Window " + windowId + " sold ticket #" + soldTicket);
                return soldTicket;
            } else {
                return -1; // no tickets left
            }
        } finally {
            lock.unlock();
        }
    }
}
