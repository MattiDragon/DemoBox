package io.github.mattidragon.demobox;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoBox implements ModInitializer {
    public static final String MOD_ID = "demobox";
    public static final Logger LOGGER = LoggerFactory.getLogger("DemoBox");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        DemoBoxCommand.register();
    }
}
