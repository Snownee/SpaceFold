package snownee.spacefold.client.paint;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedRootBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.world.level.block.state.BlockState;

public class PaintRoot extends WrapperUnbakedRootBlockStateModel {
	public PaintRoot(BlockStateModel.UnbakedRoot wrapped) {
		super(wrapped);
	}

	@Override
	public BlockStateModel bake(BlockState state, ModelBaker baker) {
		return new PaintModel(super.bake(state, baker));
	}
}
