package com.usujiotarako.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.usujiotarako.BehaviorPolicy;

/**
 * Keeps Traveler's Backpack's own dynamic background renderer, but teaches it
 * about Layered Hotbars' 18-pixel fourth player-inventory row.
 *
 * Traveler's original background sheets are 256x256. Its renderer does not
 * blit the whole sheet: it draws a variable top slice and a variable bottom
 * slice around the backpack-storage area. Layered Hotbars ships equivalent
 * 256x288 sheets whose lower player-inventory section has been extended by one
 * row. Replacing the renderer here preserves all of Traveler's positioning,
 * tank rendering, widgets and variable backpack row counts.
 */
@Pseudo
@Mixin(targets = "com.tiviacz.travelersbackpack.client.screens.AbstractBackpackScreen", remap = false)
public abstract class TravelersBackpackBackgroundMixin {
    private static final Identifier BETTER_HOTBARS_BACKGROUND_9 =
            Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/travelersbackpack/background_9.png");
    private static final Identifier BETTER_HOTBARS_BACKGROUND_11 =
            Identifier.fromNamespaceAndPath("better-hotbars", "textures/gui/travelersbackpack/background_11.png");

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 288;
    private static final int EXTRA_PLAYER_ROW_HEIGHT = 18;

    @Inject(method = "renderInventoryBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterHotbars$renderExpandedInventoryBackground(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            Identifier originalBackground,
            int width,
            int slotsHeight,
            CallbackInfo ci
    ) {
		if (!BehaviorPolicy.hasExtraRow()) return;
        String path = originalBackground.getPath();
        Identifier background = path.endsWith("background_11.png")
                ? BETTER_HOTBARS_BACKGROUND_11
                : BETTER_HOTBARS_BACKGROUND_9;

        int halfSlotsHeight = slotsHeight / 2;

        // This is Traveler's original top-slice calculation unchanged.
        int topHeight = 17 + halfSlotsHeight;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                background,
                x,
                y,
                0.0F,
                0.0F,
                width,
                topHeight,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );

        // Traveler originally samples this lower section beginning at
        // 256 - (98 + halfSlotsHeight). Keep that source anchor unchanged:
        // the supplied 288px textures were extended downward from this area.
        // Draw 18 extra pixels so the complete fourth inventory row and the
        // relocated hotbar backing are included.
        int originalBottomHeight = 98 + halfSlotsHeight;
        int bottomSourceY = 256 - originalBottomHeight;
        int bottomHeight = originalBottomHeight + EXTRA_PLAYER_ROW_HEIGHT;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                background,
                x,
                y + topHeight,
                0.0F,
                (float)bottomSourceY,
                width,
                bottomHeight,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );

        ci.cancel();
    }
}
