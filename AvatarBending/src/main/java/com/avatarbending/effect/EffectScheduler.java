package com.avatarbending.effect;

import com.avatarbending.AvatarBending;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs multi-tick spell effects (expanding rings, charge-ups, trails) on the server thread.
 */
public final class EffectScheduler {
	@FunctionalInterface
	public interface TickingEffect {
		/**
		 * @param age ticks since the effect started (0 on the first call)
		 * @return true when the effect is finished
		 */
		boolean tick(int age);
	}

	private static final class Running {
		final TickingEffect effect;
		int age;
		int delay;

		Running(TickingEffect effect, int delay) {
			this.effect = effect;
			this.delay = delay;
		}
	}

	private static final List<Running> RUNNING = new ArrayList<>();
	private static final List<Running> PENDING = new ArrayList<>();
	private static int errors;

	private EffectScheduler() {
	}

	/** Starts an effect on the next server tick. */
	public static void schedule(TickingEffect effect) {
		schedule(0, effect);
	}

	/** Starts an effect after {@code delay} ticks. */
	public static void schedule(int delay, TickingEffect effect) {
		PENDING.add(new Running(effect, delay));
	}

	/** Runs {@code action} once, {@code delay} ticks from now. */
	public static void later(int delay, Runnable action) {
		schedule(delay, age -> {
			action.run();
			return true;
		});
	}

	public static void tick() {
		RUNNING.addAll(PENDING);
		PENDING.clear();
		Iterator<Running> iterator = RUNNING.iterator();
		while (iterator.hasNext()) {
			Running running = iterator.next();
			if (running.delay > 0) {
				running.delay--;
				continue;
			}
			boolean done;
			try {
				done = running.effect.tick(running.age++);
			} catch (RuntimeException e) {
				errors++;
				AvatarBending.LOGGER.error("Bending effect crashed and was stopped", e);
				done = true;
			}
			if (done) {
				iterator.remove();
			}
		}
	}

	public static void clear() {
		RUNNING.clear();
		PENDING.clear();
	}

	public static int activeCount() {
		return RUNNING.size() + PENDING.size();
	}

	/** Number of effects that threw an exception since startup (checked by the GameTests). */
	public static int errorCount() {
		return errors;
	}
}
