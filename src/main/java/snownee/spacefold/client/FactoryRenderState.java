package snownee.spacefold.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;

public class FactoryRenderState extends BlockEntityRenderState {
	ItemEntityRenderState item = new ItemEntityRenderState();
	float shakeX;
	float shakeY;
	float shakeZ;
}
