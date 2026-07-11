package com.rfizzle.instinct.data;

import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * The three persistent entity attachments every Instinct feature rides on ({@code design/SPEC.md}
 * intro). All are codec-backed and latent: an animal carries no attachment bytes until a feature
 * writes one, so readers gate on {@code getAttached(...) == null → vanilla default}, never attach
 * on read. Entities serialize attachments with their chunk — no dirtying call needed.
 */
public final class InstinctAttachments {

    public static final AttachmentType<VeterancyData> VETERANCY = AttachmentRegistry.<VeterancyData>builder()
            .persistent(VeterancyData.CODEC)
            .initializer(VeterancyData::new)
            .buildAndRegister(Instinct.id("veterancy"));

    public static final AttachmentType<GeneticsData> GENETICS = AttachmentRegistry.<GeneticsData>builder()
            .persistent(GeneticsData.CODEC)
            .initializer(GeneticsData::new)
            .buildAndRegister(Instinct.id("genetics"));

    public static final AttachmentType<DownedData> DOWNED = AttachmentRegistry.<DownedData>builder()
            .persistent(DownedData.CODEC)
            .initializer(DownedData::new)
            .buildAndRegister(Instinct.id("downed"));

    private InstinctAttachments() {
    }

    /** Forces classload so the attachment types register during mod init. */
    public static void init() {
    }
}
