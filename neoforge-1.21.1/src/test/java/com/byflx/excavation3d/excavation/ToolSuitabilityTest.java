package com.byflx.excavation3d.excavation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure rule evaluation; the GameTests cover the Minecraft component/tag adapter. */
class ToolSuitabilityTest {
    private record Rule(String block, Optional<Float> speed, Optional<Boolean> drops) {
    }

    private static boolean intended(List<Rule> rules, String block) {
        return MiningIntent.matches(rules, rule -> rule.block().equals(block),
                rule -> rule.speed().isPresent(), rule -> rule.drops().orElse(false));
    }

    @Test void pickaxeRulesMatchStoneButNotPlanksEvenThoughPlanksDropWithoutATool() {
        var rules = List.of(new Rule("stone", Optional.of(6F), Optional.of(true)));
        assertTrue(intended(rules, "stone"));
        assertFalse(intended(rules, "planks"));
    }

    @Test void axeAndShovelRulesSelectTheirOwnMaterials() {
        var axe = List.of(new Rule("planks", Optional.of(6F), Optional.of(true)));
        var shovel = List.of(new Rule("dirt", Optional.of(6F), Optional.of(true)));
        assertTrue(intended(axe, "planks"));
        assertFalse(intended(axe, "stone"));
        assertTrue(intended(shovel, "dirt"));
        assertFalse(intended(shovel, "stone"));
    }

    @Test void moddedSpeedOnlyRulesDeclareIntentEvenAtSpeedOne() {
        var rules = List.of(new Rule("custom", Optional.of(1F), Optional.empty()));
        assertTrue(intended(rules, "custom"));
        assertFalse(intended(rules, "stone"));
        assertFalse(intended(List.of(), "custom"));
    }

    @Test void positiveDropsOnlyRulesDeclareIntentButDenialsAndEmptyRulesDoNot() {
        assertTrue(intended(List.of(new Rule("custom", Optional.empty(), Optional.of(true))), "custom"));
        assertFalse(intended(List.of(new Rule("custom", Optional.empty(), Optional.of(false))), "custom"));
        assertFalse(intended(List.of(new Rule("custom", Optional.empty(), Optional.empty())), "custom"));
    }

    @Test void tierDenialDoesNotHideTheSeparateMineableRule() {
        var denial = new Rule("obsidian", Optional.empty(), Optional.of(false));
        var mineable = new Rule("obsidian", Optional.of(6F), Optional.of(true));
        // Intent and harvest tier are distinct: the adapter retains the stack's harvest check.
        assertTrue(intended(List.of(denial, mineable), "obsidian"));
        assertTrue(intended(List.of(mineable, denial), "obsidian"));
        assertFalse(intended(List.of(denial), "obsidian"));
    }
}
