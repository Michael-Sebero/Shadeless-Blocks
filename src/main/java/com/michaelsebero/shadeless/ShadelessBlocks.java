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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod(modid = ShadelessBlocks.MODID, name = ShadelessBlocks.NAME, version = ShadelessBlocks.VERSION, clientSideOnly = true)
public class ShadelessBlocks {
    
    public static final String MODID = "shadelessblocks";
    public static final String NAME = "Shadeless Blocks";
    public static final String VERSION = "1.5";
    
    private static Constructor<?> bakedQuadConstructor;
    
    // Only blacklist mods that are truly incompatible
    private static final Set<String> BLACKLISTED_MODS = new HashSet<>();
    
    // Whitelist for known compatible custom model classes
    private static final Set<String> WHITELISTED_MODELS = new HashSet<>();
    
    static {
        // Only add mods that have serious compatibility issues
        // GregTech removed - blocks work fine, items are already skipped via inventory check
        BLACKLISTED_MODS.add("appliedenergistics2");
        BLACKLISTED_MODS.add("ae2");
        BLACKLISTED_MODS.add("ic2");
        
        // Add Dynamic Trees models
        WHITELISTED_MODELS.add("BakedModelBlockBranchBasic");
        WHITELISTED_MODELS.add("BakedModelBlockBranchThick");
        WHITELISTED_MODELS.add("BakedModelBlockBranchCactus");
        
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
        int skippedModels = 0;
        
        for (ModelResourceLocation key : event.getModelRegistry().getKeys()) {
            // Skip item models (inventory variants) - this prevents GregTech items from being affected
            if (key.getVariant().equals("inventory")) {
                continue;
            }
            
            // Check if this model is from a blacklisted mod
            String resourceDomain = key.getResourceDomain();
            if (BLACKLISTED_MODS.contains(resourceDomain)) {
                skippedModels++;
                continue;
            }
            
            IBakedModel originalModel = event.getModelRegistry().getObject(key);
            if (originalModel != null) {
                // Check if this is a safe model to wrap
                if (isSafeToWrap(originalModel, key)) {
                    try {
                        IBakedModel shadelessModel = new ShadelessModelWrapper(originalModel);
                        event.getModelRegistry().putObject(key, shadelessModel);
                        processedModels++;
                    } catch (Exception e) {
                        System.err.println("[ShadelessBlocks] Failed to wrap model: " + key);
                        skippedModels++;
                    }
                } else {
                    skippedModels++;
                }
            }
        }
        
        System.out.println("[ShadelessBlocks] Processed " + processedModels + " models, skipped " + skippedModels + " incompatible models");
    }
    
    private boolean isSafeToWrap(IBakedModel model, ModelResourceLocation location) {
        String className = model.getClass().getName();
        String simpleClassName = model.getClass().getSimpleName();
        String resourceDomain = location.getResourceDomain();
        
        // Check whitelist first - explicitly allowed models
        if (WHITELISTED_MODELS.contains(simpleClassName)) {
            System.out.println("[ShadelessBlocks] Whitelisted model: " + simpleClassName + " for " + location);
            return true;
        }
        
        // Allow all GregTech models - they support shadeless rendering
        if (resourceDomain.equals("gregtech")) {
            System.out.println("[ShadelessBlocks] Processing GregTech model: " + simpleClassName + " for " + location);
            // Still skip built-in renderers for GregTech
            try {
                if (model.isBuiltInRenderer()) {
                    System.out.println("[ShadelessBlocks] Skipping GregTech built-in renderer for " + location);
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        
        // Skip models with custom implementations (non-vanilla model classes)
        if (!simpleClassName.equals("SimpleBakedModel") && 
            !simpleClassName.equals("WeightedBakedModel") &&
            !simpleClassName.equals("MultipartBakedModel") &&
            !simpleClassName.equals("BuiltInModel") &&
            !className.startsWith("net.minecraft.client.renderer.block.model") &&
            !className.contains("ModelWrapper")) {
            
            // Allow some known good Forge model types
            if (!className.contains("forge") || 
                (!className.contains("MultiLayerModel") && 
                 !className.contains("ItemLayerModel") &&
                 !className.contains("PerspectiveMapWrapper"))) {
                return false;
            }
        }
        
        // Skip if model uses built-in renderer (custom rendering like TESR)
        try {
            if (model.isBuiltInRenderer()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        
        // Test if we can safely get quads
        try {
            List<BakedQuad> testQuads = model.getQuads(null, null, 0);
            if (testQuads == null) {
                return true;
            }
            // Try to access the first quad to ensure it's valid
            if (!testQuads.isEmpty()) {
                BakedQuad testQuad = testQuads.get(0);
                if (testQuad == null) {
                    return false;
                }
                // Verify we can access quad properties
                testQuad.getVertexData();
                testQuad.getFace();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static BakedQuad createShadelessQuad(BakedQuad original) {
        if (bakedQuadConstructor == null || original == null) {
            return original;
        }
        
        try {
            // Clone the vertex data to avoid modifying the original
            int[] originalData = original.getVertexData();
            int[] newData = new int[originalData.length];
            System.arraycopy(originalData, 0, newData, 0, originalData.length);
            
            return (BakedQuad) bakedQuadConstructor.newInstance(
                newData,
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
            try {
                List<BakedQuad> originalQuads = original.getQuads(state, side, rand);
                if (originalQuads == null || originalQuads.isEmpty()) {
                    return originalQuads;
                }
                
                List<BakedQuad> shadelessQuads = new ArrayList<>(originalQuads.size());
                for (BakedQuad quad : originalQuads) {
                    if (quad != null) {
                        shadelessQuads.add(createShadelessQuad(quad));
                    }
                }
                return shadelessQuads;
            } catch (Exception e) {
                // If something goes wrong, return original quads to prevent crashes
                try {
                    return original.getQuads(state, side, rand);
                } catch (Exception e2) {
                    return new ArrayList<>();
                }
            }
        }
        
        @Override
        public boolean isAmbientOcclusion() {
            try {
                return original.isAmbientOcclusion();
            } catch (Exception e) {
                return true;
            }
        }
        
        @Override
        public boolean isGui3d() {
            try {
                return original.isGui3d();
            } catch (Exception e) {
                return true;
            }
        }
        
        @Override
        public boolean isBuiltInRenderer() {
            try {
                return original.isBuiltInRenderer();
            } catch (Exception e) {
                return false;
            }
        }
        
        @Override
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleTexture() {
            try {
                return original.getParticleTexture();
            } catch (Exception e) {
                return null;
            }
        }
        
        @Override
        public net.minecraft.client.renderer.block.model.ItemCameraTransforms getItemCameraTransforms() {
            try {
                return original.getItemCameraTransforms();
            } catch (Exception e) {
                return net.minecraft.client.renderer.block.model.ItemCameraTransforms.DEFAULT;
            }
        }
        
        @Override
        public net.minecraft.client.renderer.block.model.ItemOverrideList getOverrides() {
            try {
                return original.getOverrides();
            } catch (Exception e) {
                return net.minecraft.client.renderer.block.model.ItemOverrideList.NONE;
            }
        }
    }
}
