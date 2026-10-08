package com.avatarbending.client.fx;

import com.avatarbending.AvatarBending;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs multi-tick visual animations on the client (charge-ups, rising spirals, delayed rings).
 */
public final class ClientScheduler {
	@FunctionalInterface
	public interface Task {
		/** @return true when finished */
		boolean tick(int age);
	}

	private static final class Running {
		final Task task;
		int delay;
		int age;

		Running(Task task, int delay) {
			this.task = task;
			this.delay = delay;
		}
	}

	private static final List<Running> RUNNING = new ArrayList<>();
	private static final List<Running> PENDING = new ArrayList<>();

	private ClientScheduler() {
	}

	public static void schedule(Task task) {
		schedule(0, task);
	}

	public static void schedule(int delay, Task task) {
		PENDING.add(new Running(task, delay));
	}

	public static void later(int delay, Runnable action) {
		schedule(delay, age -> {
			action.run();
			return true;
		});
	}

	public static void tick() {
		RUNNING.addAll(PENDING);
		PENDING.clear();
		Iterator<Running> it = RUNNING.iterator();
		while (it.hasNext()) {
			Running r = it.next();
			if (r.delay > 0) {
				r.delay--;
				continue;
			}
			boolean done;
			try {
				done = r.task.tick(r.age++);
			} catch (RuntimeException e) {
				AvatarBending.LOGGER.error("Client effect crashed and was stopped", e);
				done = true;
			}
			if (done) {
				it.remove();
			}
		}
	}

	public static void clear() {
		RUNNING.clear();
		PENDING.clear();
	}
}
