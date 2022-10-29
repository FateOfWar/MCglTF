package com.modularmods.mcgltf;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import com.modularmods.mcgltf.mixin.Matrix4fAccessor;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MathUtils;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.SkinModel;

public class RenderedGltfModelGL40 extends RenderedGltfModel {

	public RenderedGltfModelGL40(List<Runnable> gltfRenderData, GltfModel gltfModel) {
		super(gltfRenderData, gltfModel);
	}
	
	@Override
	protected void processSceneModels(List<Runnable> gltfRenderData, List<SceneModel> sceneModels) {
		for(SceneModel sceneModel : sceneModels) {
			RenderedGltfScene renderedGltfScene = new RenderedGltfSceneGL40();
			renderedGltfScenes.add(renderedGltfScene);
			
			for(NodeModel nodeModel : sceneModel.getNodeModels()) {
				Triple<List<Runnable>, List<Runnable>, List<Runnable>> commands = rootNodeModelToCommands.get(nodeModel);
				List<Runnable> rootSkinningCommands;
				List<Runnable> vanillaRootRenderCommands;
				List<Runnable> shaderModRootRenderCommands;
				if(commands == null) {
					rootSkinningCommands = new ArrayList<Runnable>();
					vanillaRootRenderCommands = new ArrayList<Runnable>();
					shaderModRootRenderCommands = new ArrayList<Runnable>();
					processNodeModel(gltfRenderData, nodeModel, rootSkinningCommands, vanillaRootRenderCommands, shaderModRootRenderCommands);
					rootNodeModelToCommands.put(nodeModel, Triple.of(rootSkinningCommands, vanillaRootRenderCommands, shaderModRootRenderCommands));
				}
				else {
					rootSkinningCommands = commands.getLeft();
					vanillaRootRenderCommands = commands.getMiddle();
					shaderModRootRenderCommands = commands.getRight();
				}
				renderedGltfScene.skinningCommands.addAll(rootSkinningCommands);
				renderedGltfScene.vanillaRenderCommands.addAll(vanillaRootRenderCommands);
				renderedGltfScene.shaderModRenderCommands.addAll(shaderModRootRenderCommands);
			}
		}
	}
	
	@Override
	protected void processNodeModel(List<Runnable> gltfRenderData, NodeModel nodeModel, List<Runnable> skinningCommands, List<Runnable> vanillaRenderCommands, List<Runnable> shaderModRenderCommands) {
		ArrayList<Runnable> nodeSkinningCommands = new ArrayList<Runnable>();
		ArrayList<Runnable> vanillaNodeRenderCommands = new ArrayList<Runnable>();
		ArrayList<Runnable> shaderModNodeRenderCommands = new ArrayList<Runnable>();
		SkinModel skinModel = nodeModel.getSkinModel();
		if(skinModel != null) {
			int jointCount = skinModel.getJoints().size();
			int jointMatrixSize = jointCount * 16;
			
			int jointMatrixBuffer = GL15.glGenBuffers();
			gltfRenderData.add(() -> GL15.glDeleteBuffers(jointMatrixBuffer));
			GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, jointMatrixBuffer);
			GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, jointMatrixSize * Float.BYTES, GL15.GL_STATIC_DRAW);
			int glTexture = GL11.glGenTextures();
			gltfRenderData.add(() -> GL11.glDeleteTextures(glTexture));
			GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture);
			GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, jointMatrixBuffer);
			
			float[][] transforms = new float[jointCount][];
			float[] invertNodeTransform = new float[16];
			float[] bindShapeMatrix = new float[16];
			float[] jointMatrices = new float[jointMatrixSize];
			
			List<Runnable> jointMatricesTransformCommands = new ArrayList<Runnable>(jointCount);
			for(int joint = 0; joint < jointCount; joint++) {
				int i = joint;
				float[] transform = transforms[i] = new float[16];
				float[] inverseBindMatrix = new float[16];
				jointMatricesTransformCommands.add(() -> {
					MathUtils.mul4x4(invertNodeTransform, transform, transform);
					skinModel.getInverseBindMatrix(i, inverseBindMatrix);
					MathUtils.mul4x4(transform, inverseBindMatrix, transform);
					MathUtils.mul4x4(transform, bindShapeMatrix, transform);
					System.arraycopy(transform, 0, jointMatrices, i * 16, 16);
				});
			}
			
			nodeSkinningCommands.add(() -> {
				for(int i = 0; i < transforms.length; i++) {
					System.arraycopy(findGlobalTransform(skinModel.getJoints().get(i)), 0, transforms[i], 0, 16);
				}
				MathUtils.invert4x4(findGlobalTransform(nodeModel), invertNodeTransform);
				skinModel.getBindShapeMatrix(bindShapeMatrix);
				jointMatricesTransformCommands.parallelStream().forEach(Runnable::run);
				
				GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, jointMatrixBuffer);
				GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, putFloatBuffer(jointMatrices));
				
				GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture);
			});

			for(MeshModel meshModel : nodeModel.getMeshModels()) {
				for(MeshPrimitiveModel meshPrimitiveModel : meshModel.getMeshPrimitiveModels()) {
					processMeshPrimitiveModel(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, nodeSkinningCommands, vanillaNodeRenderCommands, shaderModNodeRenderCommands);
				}
			}
		}
		else {
			if(!nodeModel.getMeshModels().isEmpty()) {
				for(MeshModel meshModel : nodeModel.getMeshModels()) {
					for(MeshPrimitiveModel meshPrimitiveModel : meshModel.getMeshPrimitiveModels()) {
						processMeshPrimitiveModel(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, vanillaNodeRenderCommands, shaderModNodeRenderCommands);
					}
				}
			}
		}
		nodeModel.getChildren().forEach((childNode) -> processNodeModel(gltfRenderData, childNode, nodeSkinningCommands, vanillaNodeRenderCommands, shaderModNodeRenderCommands));
		if(!nodeSkinningCommands.isEmpty()) {
			// Zero-scale meshes visibility optimization
			// https://github.com/KhronosGroup/glTF/pull/2059
			skinningCommands.add(() -> {
				float[] scale = nodeModel.getScale();
				if(scale == null || scale[0] != 0.0F || scale[1] != 0.0F || scale[2] != 0.0F) {
					nodeSkinningCommands.forEach(Runnable::run);
				}
			});
		}
		if(!vanillaNodeRenderCommands.isEmpty()) {
			vanillaRenderCommands.add(() -> {
				float[] scale = nodeModel.getScale();
				if(scale == null || scale[0] != 0.0F || scale[1] != 0.0F || scale[2] != 0.0F) {
					Matrix4f pose = new Matrix4f();
					float[] transform = findGlobalTransform(nodeModel);
					Matrix4fAccessor accessor = (Matrix4fAccessor)(Object) pose;
					accessor.setM00(transform[0]);
					accessor.setM01(transform[1]);
					accessor.setM02(transform[2]);
					accessor.setM03(transform[3]);
					accessor.setM10(transform[4]);
					accessor.setM11(transform[5]);
					accessor.setM12(transform[6]);
					accessor.setM13(transform[7]);
					accessor.setM20(transform[8]);
					accessor.setM21(transform[9]);
					accessor.setM22(transform[10]);
					accessor.setM23(transform[11]);
					accessor.setM30(transform[12]);
					accessor.setM31(transform[13]);
					accessor.setM32(transform[14]);
					accessor.setM33(transform[15]);
					Matrix3f normal = new Matrix3f(pose);
					
					pose.transpose();
					Matrix4f currentPose = CURRENT_POSE.copy();
					currentPose.multiply(pose);
					
					normal.transpose();
					Matrix3f currentNormal = CURRENT_NORMAL.copy();
					currentNormal.mul(normal);
					
					CURRENT_SHADER_INSTANCE.MODEL_VIEW_MATRIX.set(currentPose);
					CURRENT_SHADER_INSTANCE.MODEL_VIEW_MATRIX.upload();
					
					currentNormal.transpose();
					Vector3f light0Direction = LIGHT0_DIRECTION.copy();
					Vector3f light1Direction = LIGHT1_DIRECTION.copy();
					light0Direction.transform(currentNormal);
					light1Direction.transform(currentNormal);
					CURRENT_SHADER_INSTANCE.LIGHT0_DIRECTION.set(light0Direction);
					CURRENT_SHADER_INSTANCE.LIGHT1_DIRECTION.set(light1Direction);
					CURRENT_SHADER_INSTANCE.LIGHT0_DIRECTION.upload();
					CURRENT_SHADER_INSTANCE.LIGHT1_DIRECTION.upload();
					
					vanillaNodeRenderCommands.forEach(Runnable::run);
				}
			});
			shaderModRenderCommands.add(() -> {
				float[] scale = nodeModel.getScale();
				if(scale == null || scale[0] != 0.0F || scale[1] != 0.0F || scale[2] != 0.0F) {
					Matrix4f pose = new Matrix4f();
					float[] transform = findGlobalTransform(nodeModel);
					Matrix4fAccessor accessor = (Matrix4fAccessor)(Object) pose;
					accessor.setM00(transform[0]);
					accessor.setM01(transform[1]);
					accessor.setM02(transform[2]);
					accessor.setM03(transform[3]);
					accessor.setM10(transform[4]);
					accessor.setM11(transform[5]);
					accessor.setM12(transform[6]);
					accessor.setM13(transform[7]);
					accessor.setM20(transform[8]);
					accessor.setM21(transform[9]);
					accessor.setM22(transform[10]);
					accessor.setM23(transform[11]);
					accessor.setM30(transform[12]);
					accessor.setM31(transform[13]);
					accessor.setM32(transform[14]);
					accessor.setM33(transform[15]);
					Matrix3f normal = new Matrix3f(pose);
					
					pose.transpose();
					Matrix4f currentPose = CURRENT_POSE.copy();
					currentPose.multiply(pose);
					
					normal.transpose();
					Matrix3f currentNormal = CURRENT_NORMAL.copy();
					currentNormal.mul(normal);
					
					currentPose.store(BUF_FLOAT_16);
					GL20.glUniformMatrix4fv(MODEL_VIEW_MATRIX, false, BUF_FLOAT_16);
					
					currentPose.invert();
					currentPose.store(BUF_FLOAT_16);
					GL20.glUniformMatrix4fv(MODEL_VIEW_MATRIX_INVERSE, false, BUF_FLOAT_16);
					
					currentNormal.store(BUF_FLOAT_9);
					GL20.glUniformMatrix3fv(NORMAL_MATRIX, false, BUF_FLOAT_9);
					
					shaderModNodeRenderCommands.forEach(Runnable::run);
				}
			});
		}
	}

}