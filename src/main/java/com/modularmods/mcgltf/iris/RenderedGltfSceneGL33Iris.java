package com.modularmods.mcgltf.iris;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import com.modularmods.mcgltf.MCglTF;
import com.modularmods.mcgltf.RenderedGltfModel;
import com.modularmods.mcgltf.RenderedGltfSceneGL33;

public class RenderedGltfSceneGL33Iris extends RenderedGltfSceneGL33 {

	@Override
	public void renderForVanilla() {
		if(!skinningCommands.isEmpty()) {
			GL20.glUseProgram(MCglTF.getInstance().getGlProgramSkinnig());
			GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
			skinningCommands.forEach(Runnable::run);
			GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
			GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
			GL20.glUseProgram(RenderedGltfModel.CURRENT_SHADER_INSTANCE.getId());
		}
		
		vanillaRenderCommands.forEach(Runnable::run);
		
		RenderedGltfModel.NODE_GLOBAL_TRANSFORMATION_LOOKUP_CACHE.clear();
	}

	@Override
	public void renderForShaderMod() {
		if(!skinningCommands.isEmpty()) {
			GL20.glUseProgram(MCglTF.getInstance().getGlProgramSkinnig());
			GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
			skinningCommands.forEach(Runnable::run);
			GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
			GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
			GL20.glUseProgram(RenderedGltfModel.CURRENT_SHADER_INSTANCE.getId());
		}
		
		shaderModRenderCommands.forEach(Runnable::run);
		
		RenderedGltfModel.NODE_GLOBAL_TRANSFORMATION_LOOKUP_CACHE.clear();
	}

}
