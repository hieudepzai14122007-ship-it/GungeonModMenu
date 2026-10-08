package com.avatarbending.fx;

import net.minecraft.entity.Entity;

/**
 * Bridge from common entity code to client-only visuals. The client sets the hook at startup;
 * on a dedicated server it stays a no-op, so no client classes are ever loaded there.
 */
public final class ClientHooks {
	@FunctionalInterface
	public interface EntityVisuals {
		void tick(Entity entity);
	}

	private static EntityVisuals entityVisuals = entity -> {
	};

	private ClientHooks() {
	}

	public static void setEntityVisuals(EntityVisuals visuals) {
		entityVisuals = visuals;
	}

	/** Called every client tick by bending entities to draw their trails and bodies. */
	public static void entityTick(Entity entity) {
		entityVisuals.tick(entity);
	}
}
