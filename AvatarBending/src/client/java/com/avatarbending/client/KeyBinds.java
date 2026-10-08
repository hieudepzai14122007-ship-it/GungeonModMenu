package com.avatarbending.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeyBinds {
	public static final String CATEGORY = "key.category.avatarbending";

	public static final KeyBinding CAST = register("key.avatarbending.cast", GLFW.GLFW_KEY_R);
	public static final KeyBinding NEXT_ABILITY = register("key.avatarbending.next_ability", GLFW.GLFW_KEY_G);
	public static final KeyBinding SWITCH_ELEMENT = register("key.avatarbending.switch_element", GLFW.GLFW_KEY_V);
	public static final KeyBinding AVATAR_STATE = register("key.avatarbending.avatar_state", GLFW.GLFW_KEY_B);
	public static final KeyBinding MENU = register("key.avatarbending.menu", GLFW.GLFW_KEY_K);

	private KeyBinds() {
	}

	private static KeyBinding register(String id, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, CATEGORY));
	}

	public static void init() {
		// Static initializer registers the bindings.
	}
}
