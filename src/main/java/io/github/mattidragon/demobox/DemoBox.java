package io.github.mattidragon.demobox;

import eu.pb4.predicate.api.PredicateRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoBox implements ModInitializer {
	public static final String MOD_ID = "demobox";
	public static final Logger LOGGER = LoggerFactory.getLogger("DemoBox");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		DemoBoxCommand.register();
		DemoBoxGame.register();
		PredicateRegistry.register(DemoBoxPredicate.ID, DemoBoxPredicate.CODEC);
	}
}
