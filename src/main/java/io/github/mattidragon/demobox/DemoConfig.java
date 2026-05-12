package io.github.mattidragon.demobox;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record DemoConfig(Identifier structure, Vec3 spawnPos, String setupCommands, String joinCommands) {
	public static final MapCodec<DemoConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Identifier.CODEC.fieldOf("structure").forGetter(DemoConfig::structure),
		Vec3.CODEC.fieldOf("spawn_pos").forGetter(DemoConfig::spawnPos),
		Codec.STRING.fieldOf("setup_commands").forGetter(DemoConfig::setupCommands),
		Codec.STRING.fieldOf("join_commands").forGetter(DemoConfig::joinCommands)
	).apply(i, DemoConfig::new));

	private static final Codec<Vec3> VEC3_STRING_CODEC = Codec.STRING.comapFlatMap(s -> {
		var reader = new StringReader(s);
		WorldCoordinates worldCoordinates;
		try {
			worldCoordinates = WorldCoordinates.parseDouble(reader, true);
		} catch (CommandSyntaxException e) {
			return DataResult.error(() -> "Failed to decode coordinates: " + e.getMessage());
		}
		while (reader.canRead()) {
			if (reader.read() != ' ') {
				return DataResult.error(() -> "Unexpected character while parsing coordinates: '" + (char) reader.getCursor() + "'");
			}
		}

		return DataResult.success(new Vec3(
			worldCoordinates.x().get(0.5),
			worldCoordinates.y().get(2),
			worldCoordinates.z().get(0.5)
		));
	}, vec -> "%s %s %s".formatted(vec.x, vec.y, vec.z));

	public static final Codec<DemoConfig> DIALOG_CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.fieldOf("structure").forGetter(DemoConfig::structure),
		VEC3_STRING_CODEC.fieldOf("spawn_pos").forGetter(DemoConfig::spawnPos),
		Codec.STRING.fieldOf("setup_commands").forGetter(DemoConfig::setupCommands),
		Codec.STRING.fieldOf("join_commands").forGetter(DemoConfig::joinCommands)
	).apply(i, DemoConfig::new));
}
