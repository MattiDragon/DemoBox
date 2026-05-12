package io.github.mattidragon.demobox;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DemoBoxConfigs extends SavedData {
	private static final Codec<Map<Identifier, DemoConfig>> DATA_CODEC = Codec.unboundedMap(Identifier.CODEC, DemoConfig.CODEC.codec());
	public static final Codec<DemoBoxConfigs> CODEC = DATA_CODEC.xmap(
		data -> Util.make(new DemoBoxConfigs(), obj -> obj.configs.putAll(data)),
		DemoBoxConfigs::getConfigs
	);
	@SuppressWarnings("DataFlowIssue") // Fabric patches this to accept null
	public static final SavedDataType<DemoBoxConfigs> TYPE = new SavedDataType<>(DemoBox.id("configs"), DemoBoxConfigs::new, CODEC, null);

	private final Map<Identifier, DemoConfig> configs = new HashMap<>();

	public void createOrUpdateConfig(Identifier id, DemoConfig config) {
		configs.put(id, config);
		setDirty();
	}

	public boolean removeConfig(Identifier id) {
		var old = configs.remove(id);
		setDirty();
		return old != null;
	}

	public @Nullable DemoConfig getConfig(Identifier id) {
		return configs.get(id);
	}

	public Map<Identifier, DemoConfig> getConfigs() {
		return Collections.unmodifiableMap(configs);
	}
}
