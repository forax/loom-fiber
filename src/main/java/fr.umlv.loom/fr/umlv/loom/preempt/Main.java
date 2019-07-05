package fr.umlv.loom.preempt;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
	private static final ContinuationScope SCOPE = new ContinuationScope("scope");
	
	static class Task {
		private final int id;
		private final Continuation continuation;
		private long sum = 0;
		
		Task(int id) {
			this.id = id;
			this.continuation = new Continuation(SCOPE, this::loop);
		}

		void loop() {
			for(long i = 0; i < 5_000_000_000L; i++) {
				sum += i;
			}
		}
		
		@Override
		public String toString() {
			return "task " + id + " " + sum;
		}
	}
	
  public static void main(String[] args) {
  	var tasks = List.of(new Task(0), new Task(1));
  	
  	var queue = new ArrayDeque<>(tasks);
  	
  	var executor = new ScheduledThreadPoolExecutor(1);
  	
  	Task task;
  	while((task = queue.poll()) != null) {
  		var continuation = task.continuation;
  		var id = task.id;
  		var thread = Thread.currentThread();
  		var future = executor.schedule(() -> {
  			var status = continuation.tryPreempt(thread);
  			System.err.println("preempt " + status + " task " + id);
  		}, 300, TimeUnit.MILLISECONDS);
  		
  		var startTime = System.nanoTime();
  		continuation.run();
  		var endTime = System.nanoTime();
  		
  		System.err.println(task + " in " + (endTime - startTime));
  		
  		future.cancel(false);
  		if (!continuation.isDone()) {
  		  queue.offer(task);
  		}
  	}
  	executor.shutdown();
	}
}
