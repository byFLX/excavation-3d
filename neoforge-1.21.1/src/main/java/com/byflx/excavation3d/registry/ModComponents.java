package com.byflx.excavation3d.registry;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.data.ToolShapeData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Excavation3D.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolShapeData>> TOOL_SHAPE =
            COMPONENTS.register("tool_shape", () -> DataComponentType.<ToolShapeData>builder()
                    .persistent(ToolShapeData.CODEC)
                    .networkSynchronized(ToolShapeData.STREAM_CODEC)
                    .build());

    private ModComponents() {
    }
}
