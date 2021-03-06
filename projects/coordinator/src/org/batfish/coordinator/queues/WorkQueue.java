package org.batfish.coordinator.queues;

import java.util.UUID;

import org.batfish.coordinator.QueuedWork;

public interface WorkQueue extends Iterable<QueuedWork> {

   public enum Type {
      azure,
      memory
   }

   boolean delete(QueuedWork qWork);

   QueuedWork deque();

   boolean enque(QueuedWork qWork) throws Exception;

   long getLength();

   QueuedWork getWork(UUID workItemId);
}
