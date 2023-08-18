package io.github.mattidragon.demobox;

import com.mojang.serialization.MapCodec;
import eu.pb4.predicate.api.AbstractPredicate;
import eu.pb4.predicate.api.PredicateContext;
import eu.pb4.predicate.api.PredicateResult;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.manager.GameSpaceManager;

public class DemoBoxPredicate extends AbstractPredicate {
    public static final Identifier ID = DemoBox.id("in_demo");
    public static final DemoBoxPredicate INSTANCE = new DemoBoxPredicate();
    public static final MapCodec<DemoBoxPredicate> CODEC = MapCodec.unit(INSTANCE);

    public DemoBoxPredicate() {
        super(ID, CODEC);
    }

    @Override
    public PredicateResult<?> test(PredicateContext context) {
        var world = context.world();
        if (world == null) return PredicateResult.ofFailure();
        var gameSpace = GameSpaceManager.get().byWorld(world);
        if (gameSpace == null) return PredicateResult.ofFailure();
        if (gameSpace.getMetadata().sourceConfig().type() != DemoBoxGame.TYPE) return PredicateResult.ofFailure();
        return PredicateResult.ofSuccess();
    }
}
