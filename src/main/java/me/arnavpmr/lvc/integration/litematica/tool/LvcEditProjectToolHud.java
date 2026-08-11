package me.arnavpmr.lvc.integration.litematica.tool;

import java.util.List;
import net.minecraft.core.BlockPos;
import me.arnavpmr.lvc.overlay.LvcSubRegionStructuralDiffRegistry;
import me.arnavpmr.lvc.overlay.LvcManualOriginMarkerRegistry;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.tool.ToolMode;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;

/** Builds the Edit Project tool HUD using Litematica's placement HUD format. */
public final class LvcEditProjectToolHud
{
    private LvcEditProjectToolHud()
    {
    }

    public static void appendLines(List<String> lines)
    {
        String green = GuiBase.TXT_GREEN;
        String gold = GuiBase.TXT_GOLD;
        String red = GuiBase.TXT_RED;
        String white = GuiBase.TXT_WHITE;
        String reset = GuiBase.TXT_RST;
        String yes = green + StringUtils.translate("litematica.label.yes") + reset;
        String no = red + StringUtils.translate("litematica.label.no") + reset;
        SchematicPlacement placement = DataManager.getSchematicPlacementManager()
                .getSelectedSchematicPlacement();

        if (placement != null)
        {
            SubRegionPlacement subRegion = placement.getSelectedSubRegionPlacement();
            String subRegionName = subRegion != null ? subRegion.getName() : null;
            boolean regionsModified = placement.isRegionPlacementModified() ||
                    LvcSubRegionStructuralDiffRegistry.hasModifiedRegionDefinitions(placement);

            String label = StringUtils.translate(
                    "litematica.hud.schematic_placement.selected_placement");
            lines.add(String.format("%s: %s%s%s", label, green, placement.getName(), reset));

            label = StringUtils.translate("litematica.hud.schematic_placement.sub_region_count");
            String count = String.format("%s: %s%d%s", label, green,
                    placement.getSubRegionCount(), reset);
            label = StringUtils.translate(
                    "litematica.hud.schematic_placement.sub_regions_modified");
            lines.add(count + String.format(" - %s: %s", label, regionsModified ? yes : no));

            BlockPos origin = placement.getOrigin();
            String position = String.format("%d, %d, %d",
                    origin.getX(), origin.getY(), origin.getZ());
            lines.add(StringUtils.translate(
                    "litematica.hud.area_selection.origin", green + position + reset));

            if (subRegion != null && subRegionName != null)
            {
                boolean subRegionModified = subRegion.isRegionPlacementModifiedFromDefault() ||
                        LvcSubRegionStructuralDiffRegistry.isRegionDefinitionModified(
                                placement, subRegionName);
                label = StringUtils.translate(
                        "litematica.hud.schematic_placement.selected_sub_region");
                String modified = StringUtils.translate(
                        "litematica.hud.schematic_placement.sub_region_modified");
                lines.add(String.format("%s: %s%s%s - %s: %s", label, green,
                        subRegionName, reset, modified, subRegionModified ? yes : no));
            }
            else
            {
                BlockPos manualOrigin =
                        LvcManualOriginMarkerRegistry.workingWorldOrigin(
                                placement);

                if (manualOrigin != null)
                {
                    String manualPosition = String.format(
                            "%d, %d, %d",
                            manualOrigin.getX(),
                            manualOrigin.getY(),
                            manualOrigin.getZ());
                    label = StringUtils.translate(
                            "gitmatica.hud.manual_origin");
                    String modified = StringUtils.translate(
                            "litematica.hud.schematic_placement.sub_region_modified");
                    lines.add(String.format(
                            "%s: %s%s%s - %s: %s",
                            label,
                            green,
                            manualPosition,
                            reset,
                            modified,
                            LvcManualOriginMarkerRegistry.isModified(placement)
                                    ? yes
                                    : no));
                }
            }
        }
        else
        {
            String none = "<" + StringUtils.translate("litematica.label.none_lower") + ">";
            String label = StringUtils.translate(
                    "litematica.hud.schematic_placement.selected_placement");
            lines.add(String.format("%s: %s%s%s", label, white, none, reset));
        }

        String cornerMode = green +
                Configs.Generic.SELECTION_CORNERS_MODE.getOptionListValue().getDisplayName() +
                reset;
        lines.add(StringUtils.translate(
                "litematica.gui.button.area_editor.change_corner_mode", cornerMode));

        ToolMode mode = DataManager.getToolMode();
        String label = StringUtils.translate("litematica.hud.selected_mode");
        lines.add(String.format("%s [%s%d%s/%s%d%s]: %s%s%s", label,
                green, mode.ordinal() + 1, white,
                green, ToolMode.values().length, white,
                gold, mode.getName(), reset));
    }
}
