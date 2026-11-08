package com.grizzly.client;

import com.grizzly.GrizzlyMod;
import com.grizzly.entity.GrizzlyBear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Oso adulto: 24 px de ancho, 32 de alto y 48 de largo (1.5 x 2 x 3 bloques).
 * Textura 128x128.
 */
public class GrizzlyBearModel extends EntityModel<GrizzlyBear> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(GrizzlyMod.MODID, "grizzly_bear"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart frontRightLeg;
    private final ModelPart frontLeftLeg;
    private final ModelPart backRightLeg;
    private final ModelPart backLeftLeg;

    public GrizzlyBearModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.backRightLeg = root.getChild("back_right_leg");
        this.backLeftLeg = root.getChild("back_left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Cabeza baja + hocico
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7.0F, -6.0F, -8.0F, 14.0F, 12.0F, 8.0F)
                        .texOffs(46, 0).addBox(-3.5F, 0.0F, -11.0F, 7.0F, 5.0F, 3.0F),
                PartPose.offset(0.0F, 8.0F, -5.0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create()
                        .texOffs(68, 0).addBox(-7.0F, -9.0F, -4.0F, 3.0F, 3.0F, 1.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("left_ear", CubeListBuilder.create()
                        .texOffs(68, 0).mirror().addBox(4.0F, -9.0F, -4.0F, 3.0F, 3.0F, 1.0F),
                PartPose.ZERO);

        // Cuerpo largo (z de -5 a 32): 24 de ancho, 20 de alto, 37 de largo
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-12.0F, -10.0F, -18.5F, 24.0F, 20.0F, 37.0F),
                PartPose.offset(0.0F, 2.0F, 13.5F));

        // Patas gruesas
        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(0, 80).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 12.0F, 8.0F);
        root.addOrReplaceChild("front_right_leg", leg, PartPose.offset(-8.0F, 12.0F, -1.0F));
        root.addOrReplaceChild("front_left_leg", leg, PartPose.offset(8.0F, 12.0F, -1.0F));
        root.addOrReplaceChild("back_right_leg", leg, PartPose.offset(-8.0F, 12.0F, 28.0F));
        root.addOrReplaceChild("back_left_leg", leg, PartPose.offset(8.0F, 12.0F, 28.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(GrizzlyBear bear, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        // Cachorro: cabeza proporcionalmente más grande
        float headScale = bear.isBaby() ? 1.3F : 1.0F;
        this.head.xScale = headScale;
        this.head.yScale = headScale;
        this.head.zScale = headScale;

        float t = limbSwing * 0.6662F;
        float swing = 1.0F * limbSwingAmount;
        this.backRightLeg.xRot = Mth.cos(t) * swing;
        this.frontLeftLeg.xRot = Mth.cos(t) * swing;
        this.backLeftLeg.xRot = Mth.cos(t + Mth.PI) * swing;
        this.frontRightLeg.xRot = Mth.cos(t + Mth.PI) * swing;

        // Balanceo pesado del cuerpo al caminar
        this.body.zRot = Mth.sin(t) * 0.04F * limbSwingAmount;

        // Animación al recibir miel: levanta el hocico, "mastica" y alza las patas delanteras
        int eat = bear.getEatTicks();
        if (eat > 0) {
            float e = Math.min(1.0F, eat / 8.0F);
            float chew = Mth.sin(ageInTicks * 0.9F);
            this.head.xRot = Mth.lerp(e, this.head.xRot, -0.55F + chew * 0.18F);
            this.head.yRot = Mth.lerp(e, this.head.yRot, 0.0F);
            this.frontRightLeg.xRot = Mth.lerp(e, this.frontRightLeg.xRot, -0.8F + chew * 0.12F);
            this.frontLeftLeg.xRot = Mth.lerp(e, this.frontLeftLeg.xRot, -0.8F - chew * 0.12F);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
