package com.michaelsebero.shadeless;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

@Mod(modid = ShadelessBlocks.MODID, name = ShadelessBlocks.NAME, version = ShadelessBlocks.VERSION, clientSideOnly = true)
public class ShadelessBlocks {
    
    public static final String MODID = "shadelessblocks";
    public static final String NAME = "Shadeless Blocks";
    public static final String VERSION = "1.0";
    
    private static Constructor<?> bakedQuadConstructor;
    
    static {
        try {
            Class<?> bakedQuadClass = BakedQuad.class;
            bakedQuadConstructor = bakedQuadClass.getDeclaredConstructor(
                int[].class, int.class, EnumFacing.class, 
                net.minecraft.client.renderer.texture.TextureAtlasSprite.class, 
                boolean.class, net.minecraft.client.renderer.vertex.VertexFormat.class
            );
            bakedQuadConstructor.setAccessible(true);
            System.out.println("[ShadelessBlocks] Successfully found BakedQuad constructor!");
        } catch (Exception e) {
            System.err.println("[ShadelessBlocks] Failed to find BakedQuad constructor!");
            e.printStackTrace();
        }
    }
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onModelBake(ModelBakeEvent event) {
        System.out.println("[ShadelessBlocks] Removing shading from block models...");
        
        if (bakedQuadConstructor == null) {
            System.err.println("[ShadelessBlocks] Cannot proceed - BakedQuad constructor not found!");
            return;
        }
        
        int processedModels = 0;
        
        for (ModelResourceLocation key : event.getModelRegistry().getKeys()) {
            // Skip item models (inventory variants)
            if (key.getVariant().equals("inventory")) {
                continue;
            }
            
            IBakedModel originalModel = event.getModelRegistry().getObject(key);
            if (originalModel != null) {
                IBakedModel shadelessModel = new ShadelessModelWrapper(originalModel);
                event.getModelRegistry().putObject(key, shadelessModel);
                processedModels++;
            }
        }
        
        System.out.println("[ShadelessBlocks] Processed " + processedModels + " block models!");
    }
    
    public static BakedQuad createShadelessQuad(BakedQuad original) {
        if (bakedQuadConstructor == null) {
            return original;
        }
        
        try {
            return (BakedQuad) bakedQuadConstructor.newInstance(
                original.getVertexData(),
                original.getTintIndex(),
                original.getFace(),
                original.getSprite(),
                false, // applyDiffuseLighting = false (no shading!)
                original.getFormat()
            );
        } catch (Exception e) {
            return original;
        }
    }
    
    private static class ShadelessModelWrapper implements IBakedModel {
        private final IBakedModel original;
        
        public ShadelessModelWrapper(IBakedModel original) {
            this.original = original;
        }
        
        @Override
        public List<BakedQuad> getQuads(net.minecraft.block.state.IBlockState state, EnumFacing side, long rand) {
            List<BakedQuad> originalQuads = original.getQuads(state, side, rand);
            if (originalQuads == null || originalQuads.isEmpty()) {
                return originalQuads;
            }
            
            List<BakedQuad> shadelessQuads = new ArrayList<>();
            for (BakedQuad quad : originalQuads) {
                shadelessQuads.add(createShadelessQuad(quad));
            }
            return shadelessQuads;
        }
        
        @Override
        public boolean isAmbientOcclusion() {
            return original.isAmbientOcclusion();
        }
        
        @Override
        public boolean isGui3d() {
            return original.isGui3d();
        }
        
        @Override
        public boolean isBuiltInRenderer() {
            return original.isBuiltInRenderer();
        }
        
        @Override
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleTexture() {
            return original.getParticleTexture();
        }
        
        @Override
        public net.minecraft.client.renderer.block.model.ItemCameraTransforms getItemCameraTransforms() {
            return original.getItemCameraTransforms();
        }
        
        @Override
        public net.minecraft.client.renderer.block.model.ItemOverrideList getOverrides() {
            return original.getOverrides();
        }
    }
}
