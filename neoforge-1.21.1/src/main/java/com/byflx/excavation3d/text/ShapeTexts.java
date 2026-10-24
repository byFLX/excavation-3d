package com.byflx.excavation3d.text;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.shape.GeneratedShape;
import com.byflx.excavation3d.shape.NumericInput;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeIssue;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeType;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Translatable texts for shape data. Only builds components (keys and arguments); translation
 * happens on the client, so the server can use these for replies too.
 */
public final class ShapeTexts {
    private static final String NS = Excavation3D.MOD_ID;

    private ShapeTexts() {
    }

    public static MutableComponent typeName(ShapeType type) {
        return Component.translatable("shape." + NS + "." + type.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent fieldName(String field) {
        return Component.translatable("field." + NS + "." + field);
    }

    public static MutableComponent issue(ShapeIssue issue) {
        Object[] args = new Object[issue.args().size() + 1];
        args[0] = fieldName(issue.field());
        for (int i = 0; i < issue.args().size(); i++) {
            args[i + 1] = issue.args().get(i);
        }
        return Component.translatable("issue." + NS + "." + issue.code().name().toLowerCase(Locale.ROOT), args);
    }

    public static MutableComponent problem(String field, NumericInput.Problem problem, int min, int max) {
        return Component.translatable("problem." + NS + "." + problem.name().toLowerCase(Locale.ROOT),
                fieldName(field), min, max);
    }

    public static MutableComponent extents(GeneratedShape shape) {
        return Component.translatable("gui." + NS + ".extents", shape.width(), shape.height(), shape.depth());
    }

    public static MutableComponent selected(GeneratedShape shape) {
        return Component.translatable("gui." + NS + ".selected", shape.count());
    }

    /** One-line state of a tool, as used by the tooltip and the in-game hint (plan §2, §9). */
    public static MutableComponent toolSummary(ToolShapeData data, ShapeLimits limits) {
        if (data == null || (data.config().isEmpty() && !data.isDamaged())) {
            return Component.translatable("tool." + NS + ".not_configured").withStyle(ChatFormatting.GRAY);
        }
        if (data.isDamaged()) {
            return Component.translatable("tool." + NS + ".damaged").withStyle(ChatFormatting.RED);
        }
        ShapeGenerator.Result result = ShapeGenerator.generate(data.config().get(), limits);
        if (result instanceof ShapeGenerator.Valid valid) {
            GeneratedShape shape = valid.shape();
            MutableComponent summary = Component.translatable("tool." + NS + ".active", typeName(data.config().get().type()),
                    shape.width(), shape.height(), shape.depth(), shape.count());
            return data.enabled() ? summary.withStyle(ChatFormatting.AQUA)
                    : Component.translatable("tool." + NS + ".paused", summary).withStyle(ChatFormatting.GRAY);
        }
        ShapeIssue first = ((ShapeGenerator.Invalid) result).issues().get(0);
        MutableComponent invalid = Component.translatable("tool." + NS + ".invalid", issue(first));
        return data.enabled() ? invalid.withStyle(ChatFormatting.RED)
                : Component.translatable("tool." + NS + ".paused", invalid).withStyle(ChatFormatting.GRAY);
    }
}
