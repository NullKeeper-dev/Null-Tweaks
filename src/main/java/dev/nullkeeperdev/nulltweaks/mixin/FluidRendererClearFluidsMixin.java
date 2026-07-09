package dev.nullkeeperdev.nulltweaks.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nullkeeperdev.nulltweaks.feature.clearfluids.ClearFluidsFeature;
import dev.nullkeeperdev.nulltweaks.feature.clearfluids.ClearFluidsRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererClearFluidsMixin {
    @Inject(method = "tesselate", at = @At("HEAD"))
    private void nulltweaks$beginClearFluid(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        ClearFluidsRenderContext.set(ClearFluidsFeature.renderSettingsFor(fluidState));
    }

    @Inject(method = "tesselate", at = @At("RETURN"))
    private void nulltweaks$endClearFluid(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        ClearFluidsRenderContext.clear();
    }

    @Redirect(
            method = "tesselate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidModel;layer()Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;"))
    private ChunkSectionLayer nulltweaks$useTranslucentLayer(FluidModel model) {
        ClearFluidsFeature.RenderSettings settings = ClearFluidsRenderContext.settings();
        if (settings != null && settings.opacity() < 100) {
            return ChunkSectionLayer.TRANSLUCENT;
        }

        return model.layer();
    }

    @ModifyVariable(method = "vertex", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nulltweaks$applyClearFluidColor(int color) {
        ClearFluidsFeature.RenderSettings settings = ClearFluidsRenderContext.settings();
        return settings == null ? color : settings.applyTo(color);
    }
}
