package me.arnavpmr.lvc.gui;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import me.arnavpmr.lvc.model.LvcManifest;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiDialogBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.KeyCodes;
import fi.dy.masa.malilib.util.StringUtils;

final class GuiLvcProjectSubRegionDialog extends GuiDialogBase
{
    @FunctionalInterface
    interface RegionSaveAction
    {
        boolean save(String name, BlockPos min, BlockPos size);
    }

    private static final int MIN_DIALOG_WIDTH = 236;
    private static final int DIALOG_HEIGHT = 202;
    private static final int PADDING = 10;
    private static final int NAME_LABEL_TOP = 27;
    private static final int NAME_FIELD_TOP = 38;
    private static final int ERROR_TOP = 56;
    private static final int CORNER_LABEL_TOP = 66;
    private static final int COORDINATE_TOP = 78;
    private static final int AXIS_LABEL_WIDTH = 12;
    private static final int FIELD_WIDTH = 68;
    private static final int FIELD_HEIGHT = 16;
    private static final int FIELD_GAP = 20;
    private static final int NUDGE_BUTTON_GAP = 4;
    private static final int NUDGE_BUTTON_WIDTH = 16;
    private static final int COLUMN_WIDTH =
            AXIS_LABEL_WIDTH + FIELD_WIDTH + NUDGE_BUTTON_GAP + NUDGE_BUTTON_WIDTH;
    private static final int COLUMN_GAP = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MOVE_BUTTON_TOP = COORDINATE_TOP + FIELD_GAP * 2 + 22;
    private static final int BACKGROUND_MOUSE = Integer.MIN_VALUE;
    private static final String PLUS_MINUS_HOVER =
            "litematica.gui.button.hover.plus_minus_tip_ctrl_alt_shift";

    private final LvcManifest.Region region;
    private final BlockPos placementOrigin;
    private final RegionSaveAction saveAction;
    private GuiTextFieldGeneric nameField;
    private final GuiTextFieldInteger[] firstCornerFields = new GuiTextFieldInteger[3];
    private final GuiTextFieldInteger[] secondCornerFields = new GuiTextFieldInteger[3];
    private String errorMessage = "";
    private boolean handled;

    GuiLvcProjectSubRegionDialog(Screen parent, LvcManifest.Region region, BlockPos placementOrigin,
                                 RegionSaveAction saveAction)
    {
        this.region = region;
        this.placementOrigin = placementOrigin;
        this.saveAction = saveAction;
        this.setParent(parent);
        this.title = StringUtils.translate("gitmatica.gui.title.lvc_project_editor.configure_sub_region", region.name());
        this.useTitleHierarchy = false;
        this.setWidthAndHeight(MIN_DIALOG_WIDTH, DIALOG_HEIGHT);
    }

    @Override
    public void initGui()
    {
        this.clearElements();
        int titleWidth = this.getStringWidth(this.getTitleString()) + PADDING * 2;
        int maxWidth = Math.max(MIN_DIALOG_WIDTH, this.getScreenWidth() - PADDING * 2);
        this.setWidthAndHeight(Math.min(maxWidth, Math.max(MIN_DIALOG_WIDTH, titleWidth)), DIALOG_HEIGHT);
        this.centerOnScreen();
        this.createNameField();
        this.createCoordinateFields(
                this.firstCornerFields, this.region.min(), this.dialogLeft + PADDING);
        this.createCoordinateFields(
                this.secondCornerFields, secondCorner(this.region), this.getSecondCornerColumnX());
        this.createMoveToPlayerButton(this.firstCornerFields, this.dialogLeft + PADDING);
        this.createMoveToPlayerButton(this.secondCornerFields, this.getSecondCornerColumnX());
        this.createButtons();
    }

    private void createNameField()
    {
        this.nameField = new GuiTextFieldGeneric(
                this.dialogLeft + PADDING,
                this.dialogTop + NAME_FIELD_TOP,
                this.dialogWidth - PADDING * 2,
                FIELD_HEIGHT,
                this.font
        );
        this.nameField.setMaxLength(128);
        this.nameField.setValueWrapper(this.region.name());
        this.addTextField(this.nameField, ignored -> false);
    }

    private void createCoordinateFields(GuiTextFieldInteger[] fields, List<Integer> values, int x)
    {
        int y = this.dialogTop + COORDINATE_TOP;

        for (int index = 0; index < fields.length; index++)
        {
            GuiTextFieldInteger field = new GuiTextFieldInteger(
                    x + AXIS_LABEL_WIDTH, y, FIELD_WIDTH, FIELD_HEIGHT, this.font);
            field.setValueWrapper(String.valueOf(values.get(index)));
            fields[index] = field;
            this.addTextField(field, ignored -> false);
            this.addButton(new ButtonGeneric(
                            x + AXIS_LABEL_WIDTH + FIELD_WIDTH + NUDGE_BUTTON_GAP,
                            y,
                            Icons.BUTTON_PLUS_MINUS_16,
                            StringUtils.translate(PLUS_MINUS_HOVER)),
                    (button, mouseButton) -> this.nudgeField(field, mouseButton));
            y += FIELD_GAP;
        }
    }

    private void createMoveToPlayerButton(GuiTextFieldInteger[] fields, int x)
    {
        String label = StringUtils.translate("litematica.gui.button.move_to_player");
        int y = this.dialogTop + MOVE_BUTTON_TOP;
        this.addButton(new ButtonGeneric(x, y, COLUMN_WIDTH, BUTTON_HEIGHT, label),
                (button, mouseButton) -> this.moveCornerToPlayer(fields));
    }

    private void createButtons()
    {
        String apply = StringUtils.translate("gitmatica.gui.button.lvc_project_editor.apply");
        String discard = StringUtils.translate("gitmatica.gui.button.lvc_project_editor.discard");
        String cancel = StringUtils.translate("malilib.gui.button.cancel");
        int applyWidth = Math.max(44, this.getStringWidth(apply) + 14);
        int discardWidth = Math.max(44, this.getStringWidth(discard) + 14);
        int cancelWidth = Math.max(44, this.getStringWidth(cancel) + 14);
        int x = this.dialogLeft + PADDING;
        int y = this.dialogTop + this.dialogHeight - PADDING - BUTTON_HEIGHT;

        this.addButton(new ButtonGeneric(x, y, applyWidth, BUTTON_HEIGHT, apply),
                (button, mouseButton) -> this.apply());
        x += applyWidth + 4;
        this.addButton(new ButtonGeneric(x, y, discardWidth, BUTTON_HEIGHT, discard),
                (button, mouseButton) -> this.discard());
        x += discardWidth + 4;
        this.addButton(new ButtonGeneric(x, y, cancelWidth, BUTTON_HEIGHT, cancel),
                (button, mouseButton) -> this.cancel());
    }

    private void nudgeField(GuiTextFieldInteger field, int mouseButton)
    {
        int amount = mouseButton == 1 ? -1 : 1;

        if (GuiBase.isCtrlDown())
        {
            amount *= 100;
        }

        if (GuiBase.isShiftDown())
        {
            amount *= 10;
        }

        if (GuiBase.isAltDown())
        {
            amount *= 5;
        }

        try
        {
            int value = Integer.parseInt(field.getValueWrapper().trim());
            field.setValueWrapper(String.valueOf(value + amount));
            this.errorMessage = "";
        }
        catch (NumberFormatException e)
        {
            this.errorMessage = StringUtils.translate("gitmatica.error.lvc_project_editor.invalid_integer");
        }
    }

    private void moveCornerToPlayer(GuiTextFieldInteger[] fields)
    {
        if (this.mc.player == null)
        {
            this.errorMessage = StringUtils.translate("gitmatica.error.lvc_project.no_player");
            return;
        }

        BlockPos playerPosition =
                fi.dy.masa.malilib.util.position.PositionUtils.getEntityBlockPos(this.mc.player);
        BlockPos projectPosition = playerPosition.subtract(this.placementOrigin);
        fields[0].setValueWrapper(String.valueOf(projectPosition.getX()));
        fields[1].setValueWrapper(String.valueOf(projectPosition.getY()));
        fields[2].setValueWrapper(String.valueOf(projectPosition.getZ()));
        this.errorMessage = "";
    }

    @Override
    public boolean onKeyTyped(KeyEvent input)
    {
        if (input.key() == KeyCodes.KEY_ESCAPE)
        {
            this.cancel();
            return true;
        }

        return super.onKeyTyped(input);
    }

    @Override
    protected void drawWidgets(GuiContext ctx, int mouseX, int mouseY)
    {
    }

    @Override
    protected void drawButtons(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
    }

    @Override
    protected void drawTextFields(GuiContext ctx, int mouseX, int mouseY)
    {
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        // The title is drawn inside the popup.
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        Screen parent = this.getParent();

        if (parent != null)
        {
            parent.extractRenderState(ctx.getGuiGraphics(), BACKGROUND_MOUSE, BACKGROUND_MOUSE, partialTicks);
        }

        RenderUtils.drawOutlinedBox(ctx, this.dialogLeft, this.dialogTop, this.dialogWidth, this.dialogHeight,
                0xE0000000, COLOR_HORIZONTAL_BAR);
        this.drawStringWithShadow(ctx, this.getTitleString(), this.dialogLeft + PADDING, this.dialogTop + 7, COLOR_WHITE);
        this.drawStringWithShadow(ctx,
                StringUtils.translate("gitmatica.gui.label.lvc_project_editor.sub_region_name"),
                this.dialogLeft + PADDING,
                this.dialogTop + NAME_LABEL_TOP,
                COLOR_WHITE);
        this.drawColumn(ctx, this.dialogLeft + PADDING,
                StringUtils.translate("litematica.gui.label.area_editor.corner_1"));
        this.drawColumn(ctx, this.getSecondCornerColumnX(),
                StringUtils.translate("litematica.gui.label.area_editor.corner_2"));

        if (!this.errorMessage.isBlank())
        {
            this.drawStringWithShadow(ctx, this.errorMessage, this.dialogLeft + PADDING,
                    this.dialogTop + ERROR_TOP, 0xFFFF5555);
        }

        super.drawTextFields(ctx, mouseX, mouseY);
        super.drawButtons(ctx, mouseX, mouseY, partialTicks);
    }

    private int getSecondCornerColumnX()
    {
        return this.dialogLeft + PADDING + COLUMN_WIDTH + COLUMN_GAP;
    }

    private void drawColumn(GuiContext ctx, int x, String title)
    {
        if (!title.isBlank())
        {
            this.drawStringWithShadow(ctx, title, x, this.dialogTop + CORNER_LABEL_TOP, 0xFFAAAAAA);
        }

        int y = this.dialogTop + COORDINATE_TOP + 4;

        for (String axis : List.of("X:", "Y:", "Z:"))
        {
            this.drawStringWithShadow(ctx, axis, x, y, COLOR_WHITE);
            y += FIELD_GAP;
        }
    }

    private void apply()
    {
        if (this.handled)
        {
            return;
        }

        if (this.applyCurrentDefinition())
        {
            this.handled = true;
            this.closeGui(true);
        }
    }

    private boolean applyCurrentDefinition()
    {
        try
        {
            String name = this.nameField.getValueWrapper().trim();

            if (name.isBlank())
            {
                this.errorMessage = StringUtils.translate(
                        "gitmatica.error.lvc_project_editor.region_name_required");
                this.nameField.setFocused(true);
                return false;
            }

            BlockPos firstCorner = readPosition(this.firstCornerFields);
            BlockPos secondCorner = readPosition(this.secondCornerFields);
            BlockPos min = PositionUtils.getMinCorner(firstCorner, secondCorner);
            BlockPos max = PositionUtils.getMaxCorner(firstCorner, secondCorner);
            BlockPos size = max.subtract(min).offset(1, 1, 1);
            BlockPos initialMin = positionFromList(this.region.min());
            BlockPos initialSize = positionFromList(this.region.size());

            if (name.equals(this.region.name()) && min.equals(initialMin) && size.equals(initialSize))
            {
                return true;
            }

            if (this.saveAction.save(name, min, size))
            {
                return true;
            }
        }
        catch (NumberFormatException e)
        {
            this.errorMessage = StringUtils.translate("gitmatica.error.lvc_project_editor.invalid_integer");
        }

        return false;
    }

    private void discard()
    {
        this.nameField.setValueWrapper(this.region.name());
        this.setCoordinateFields(this.firstCornerFields, this.region.min());
        this.setCoordinateFields(this.secondCornerFields, secondCorner(this.region));
        this.nameField.setFocused(false);
        this.errorMessage = "";
    }

    private void cancel()
    {
        if (!this.handled)
        {
            this.handled = true;
            this.closeGui(true);
        }
    }

    private static BlockPos readPosition(GuiTextFieldGeneric[] fields)
    {
        return new BlockPos(
                Integer.parseInt(fields[0].getValueWrapper().trim()),
                Integer.parseInt(fields[1].getValueWrapper().trim()),
                Integer.parseInt(fields[2].getValueWrapper().trim())
        );
    }

    private void setCoordinateFields(GuiTextFieldInteger[] fields, List<Integer> values)
    {
        for (int index = 0; index < fields.length; index++)
        {
            fields[index].setValueWrapper(String.valueOf(values.get(index)));
            fields[index].setFocused(false);
        }
    }

    private static List<Integer> secondCorner(LvcManifest.Region region)
    {
        return List.of(
                region.min().get(0) + region.size().get(0) - 1,
                region.min().get(1) + region.size().get(1) - 1,
                region.min().get(2) + region.size().get(2) - 1
        );
    }

    private static BlockPos positionFromList(List<Integer> values)
    {
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }
}
