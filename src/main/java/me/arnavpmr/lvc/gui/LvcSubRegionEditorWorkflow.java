package me.arnavpmr.lvc.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import me.arnavpmr.lvc.LvcDiagnostics;
import me.arnavpmr.lvc.LvcFriendlyErrors.Operation;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.overlay.LvcTrackingSubRegionSelection;
import me.arnavpmr.lvc.semantic.LvcProjectEditorState;
import me.arnavpmr.lvc.semantic.LvcSemanticProjectEditor;
import me.arnavpmr.lvc.task.LvcOperationHandle;
import me.arnavpmr.lvc.task.LvcRefreshMarker;
import me.arnavpmr.lvc.task.LvcSemanticOverlayTask;
import me.arnavpmr.lvc.task.LvcTaskCallbacks;
import me.arnavpmr.lvc.task.LvcTaskRegistry;
import me.arnavpmr.lvc.task.LvcTaskScheduling;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListAreaAnalyzer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;

public final class LvcSubRegionEditorWorkflow
{
    private final GuiLvcProjectEditor gui;
    private final Path repositoryDirectory;

    LvcSubRegionEditorWorkflow(GuiLvcProjectEditor gui, Path repositoryDirectory)
    {
        this.gui = gui;
        this.repositoryDirectory = repositoryDirectory;
    }

    void promptNewRegion()
    {
        if (!this.canEdit())
        {
            return;
        }

        GuiBase.openGui(new GuiLvcTextInputDialog(
                128,
                "gitmatica.gui.title.lvc_project_editor.new_sub_region",
                "",
                this.gui,
                value -> this.validateName(null, value),
                (java.util.function.Consumer<String>) this::createRegion
        ));
    }

    void promptRenameRegion(LvcManifest.Region region)
    {
        if (!this.canEdit())
        {
            return;
        }

        GuiBase.openGui(new GuiLvcTextInputDialog(
                128,
                "gitmatica.gui.title.lvc_project_editor.rename_sub_region",
                region.name(),
                this.gui,
                value -> this.validateName(region.name(), value),
                (java.util.function.Consumer<String>) value -> this.renameRegion(region.name(), value)
        ));
    }

    void openRegionEditor(LvcManifest.Region region)
    {
        if (!this.canEdit())
        {
            return;
        }

        LvcProjectEditorState state = this.gui.getEditorState();

        if (state == null)
        {
            return;
        }

        this.gui.selectRegion(region.name());
        GuiBase.openGui(new GuiLvcProjectSubRegionDialog(
                this.gui,
                region,
                state.placementOrigin(),
                (min, size) -> this.updateRegionBounds(region.name(), min, size)
        ));
    }

    void confirmDeleteRegion(LvcManifest.Region region)
    {
        LvcProjectEditorState state = this.gui.getEditorState();

        if (!this.canEdit() || state == null)
        {
            return;
        }

        if (state.regions().size() <= 1)
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project_editor.region_required");
            return;
        }

        GuiBase.openGui(new GuiConfirmAction(
                420,
                "gitmatica.gui.title.lvc_project_editor.confirm_delete_region",
                new DeleteRegionConfirmation(this, region.name()),
                this.gui,
                "gitmatica.gui.message.lvc_project_editor.confirm_delete_region",
                region.name()
        ));
    }

    void analyzeArea()
    {
        LvcProjectEditorState state = this.gui.getEditorState();

        if (state == null || state.regions().isEmpty())
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.no_tracking_areas");
            return;
        }

        AreaSelection selection = new AreaSelection();
        selection.setName(state.projectName());
        selection.setExplicitOrigin(state.placementOrigin());

        for (LvcManifest.Region region : state.regions())
        {
            BlockPos min = state.placementOrigin().offset(position(region.min()));
            BlockPos size = position(region.size());
            BlockPos max = min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
            selection.addSubRegionBox(new Box(min, max, region.name()), false);
        }

        MaterialListAreaAnalyzer materialList = new MaterialListAreaAnalyzer(selection);
        DataManager.setMaterialList(materialList);
        GuiBase.openGui(new GuiMaterialList(materialList));
        materialList.reCreateMaterialList();
    }

    void promptSaveVersion()
    {
        LvcProjectEditorState state = this.gui.getEditorState();

        if (state == null || state.regions().isEmpty())
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.no_tracking_areas");
            return;
        }

        GuiLvcProjectManager.openSaveVersionFromCurrentScreen(
                this.repositoryDirectory, state.projectName());
    }

    private void createRegion(String name)
    {
        LvcProjectEditorState state = this.gui.getEditorState();

        if (!this.canEdit())
        {
            return;
        }

        if (state == null)
        {
            LvcGuiMessages.show(
                    MessageType.ERROR, "gitmatica.error.lvc_project_editor.unknown_error");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null)
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.no_player");
            return;
        }

        if (minecraft.level == null)
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.no_world");
            return;
        }

        try
        {
            BlockPos playerPosition = fi.dy.masa.malilib.util.position.PositionUtils.getEntityBlockPos(player);
            LvcManifest.Region region = LvcSemanticProjectEditor.createRegion(
                    this.repositoryDirectory,
                    name,
                    playerPosition.subtract(state.placementOrigin()),
                    new BlockPos(1, 1, 1)
            );
            this.gui.selectRegion(region.name());
            this.refreshAfterDefinitionChange();
            LvcGuiMessages.show(MessageType.SUCCESS,
                    "gitmatica.message.lvc_project_editor.region_created", region.name());
        }
        catch (Exception e)
        {
            this.showSaveError(e);
        }
    }

    private void renameRegion(String currentName, String name)
    {
        LvcManifest.Region region = this.regionByName(currentName);

        if (!this.canEdit() || region == null)
        {
            return;
        }

        try
        {
            LvcSemanticProjectEditor.updateRegion(this.repositoryDirectory, currentName, name,
                    position(region.min()), position(region.size()));
            this.gui.selectRegion(name.trim());
            this.refreshAfterDefinitionChange();
            LvcGuiMessages.show(MessageType.SUCCESS,
                    "gitmatica.message.lvc_project_editor.region_renamed", name.trim());
        }
        catch (Exception e)
        {
            this.showSaveError(e);
        }
    }

    private boolean updateRegionBounds(String regionName, BlockPos min, BlockPos size)
    {
        if (!this.canEdit())
        {
            return false;
        }

        try
        {
            LvcSemanticProjectEditor.updateRegion(
                    this.repositoryDirectory, regionName, regionName, min, size);
            this.refreshAfterDefinitionChange();
            LvcGuiMessages.show(MessageType.SUCCESS,
                    "gitmatica.message.lvc_project_editor.region_updated");
            return true;
        }
        catch (Exception e)
        {
            this.showSaveError(e);
            return false;
        }
    }

    private void deleteRegion(String regionName)
    {
        if (!this.canEdit())
        {
            return;
        }

        try
        {
            LvcSemanticProjectEditor.deleteRegion(this.repositoryDirectory, regionName);
            this.gui.selectRegion(null);
            this.refreshAfterDefinitionChange();
            LvcGuiMessages.show(MessageType.SUCCESS,
                    "gitmatica.message.lvc_project_editor.region_deleted");
        }
        catch (Exception e)
        {
            this.showSaveError(e);
        }
    }

    private void refreshAfterDefinitionChange()
    {
        this.gui.initGui();
        refreshTrackingOverlayFromWorkingTree(this.repositoryDirectory, this.gui.getProjectName());
    }

    private boolean canEdit()
    {
        if (LvcTaskRegistry.hasActiveOperation())
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.operation_running",
                    LvcTaskRegistry.activeOperationName());
            return false;
        }

        return true;
    }

    @Nullable
    private String validateName(@Nullable String editedName, String value)
    {
        if (value == null || value.isBlank())
        {
            return StringUtils.translate("gitmatica.error.lvc_project_editor.region_name_required");
        }

        LvcProjectEditorState state = this.gui.getEditorState();

        if (state != null && state.regions().stream().anyMatch(region ->
                !region.name().equals(editedName) && region.name().equals(value.trim())))
        {
            return StringUtils.translate(
                    "gitmatica.error.lvc_project_editor.region_name_duplicate", value.trim());
        }

        return null;
    }

    @Nullable
    private LvcManifest.Region regionByName(String name)
    {
        LvcProjectEditorState state = this.gui.getEditorState();
        return state == null ? null : state.regions().stream()
                .filter(region -> region.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private void showSaveError(Exception error)
    {
        LvcGuiMessages.show(MessageType.ERROR,
                "gitmatica.error.lvc_project_editor.save_failed", error.getMessage());
    }

    static boolean openSelectedSubRegionDialog(Path repositoryDirectory,
                                               @Nullable String selectedRegionName) throws IOException
    {
        LvcProjectEditorState state = LvcSemanticProjectEditor.readState(repositoryDirectory);
        LvcManifest.Region selected = selectedRegionName == null && state.regions().size() == 1
                ? state.regions().getFirst()
                : state.regions().stream()
                        .filter(region -> region.name().equals(selectedRegionName))
                        .findFirst()
                        .orElse(null);

        if (selected == null)
        {
            return false;
        }

        GuiBase.openGui(new GuiLvcProjectSubRegionDialog(
                GuiUtils.getCurrentScreen(), selected, state.placementOrigin(),
                (min, size) -> applyRegionBounds(
                        repositoryDirectory, state.projectName(), selected.name(), min, size)
        ));
        return true;
    }

    public static boolean applyRegionBounds(Path repositoryDirectory, String projectName,
                                            String regionName, BlockPos min, BlockPos size)
    {
        if (LvcTaskRegistry.hasActiveOperation())
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.operation_running",
                    LvcTaskRegistry.activeOperationName());
            return false;
        }

        try
        {
            LvcSemanticProjectEditor.updateRegion(repositoryDirectory, regionName, regionName, min, size);
            LvcTrackingSubRegionSelection.set(repositoryDirectory, regionName);
            refreshTrackingOverlayFromWorkingTree(repositoryDirectory, projectName);
            LvcGuiMessages.show(MessageType.SUCCESS,
                    "gitmatica.message.lvc_project_editor.region_updated");
            return true;
        }
        catch (Exception e)
        {
            LvcGuiMessages.show(MessageType.ERROR,
                    "gitmatica.error.lvc_project_editor.save_failed", e.getMessage());
            return false;
        }
    }

    private static void refreshTrackingOverlayFromWorkingTree(Path repositoryDirectory, String projectName)
    {
        ClientLevel clientLevel = Minecraft.getInstance().level;

        if (clientLevel == null)
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.no_world");
            return;
        }

        Optional<LvcOperationHandle> handle = LvcTaskRegistry.tryAcquireBackground(
                LvcSemanticOverlayTask.OPERATION_NAME, repositoryDirectory);

        if (handle.isEmpty())
        {
            LvcGuiMessages.show(MessageType.ERROR, "gitmatica.error.lvc_project.operation_running",
                    LvcTaskRegistry.activeOperationName());
            return;
        }

        LvcSemanticOverlayTask task = LvcSemanticOverlayTask.workingTreeRebuild(
                handle.get(), repositoryDirectory, projectName, clientLevel, null, true,
                LvcTaskCallbacks.of(
                        overlay -> clearRefreshMarker(repositoryDirectory),
                        error -> LvcGuiMessages.showTaskError(
                                Operation.LOAD_OVERLAY,
                                "gitmatica.error.lvc_project.tracking_failed",
                                error),
                        () -> LvcDiagnostics.debug(
                                "Subregion overlay refresh aborted repo='{}'", repositoryDirectory)
                )
        );
        LvcTaskScheduling.scheduleForWorld(clientLevel, task);
    }

    private static void clearRefreshMarker(Path repositoryDirectory)
    {
        try
        {
            LvcRefreshMarker.delete(repositoryDirectory);
        }
        catch (Exception e)
        {
            LvcDiagnostics.warn("Failed to clear subregion overlay refresh marker repo='{}' error='{}'",
                    repositoryDirectory, e.getMessage());
        }
    }

    private static BlockPos position(List<Integer> values)
    {
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    private record DeleteRegionConfirmation(LvcSubRegionEditorWorkflow workflow, String regionName)
            implements fi.dy.masa.malilib.interfaces.IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.workflow.deleteRegion(this.regionName);
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }
}
