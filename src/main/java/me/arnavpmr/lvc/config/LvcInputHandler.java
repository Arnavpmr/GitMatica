package me.arnavpmr.lvc.config;

import net.minecraft.client.input.KeyEvent;
import me.arnavpmr.lvc.LvcReference;
import me.arnavpmr.lvc.integration.litematica.selection.LvcSubRegionEditSession;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.KeyCodes;

public final class LvcInputHandler implements IKeybindProvider, IKeyboardInputHandler
{
    private static final LvcInputHandler INSTANCE = new LvcInputHandler();

    private LvcInputHandler()
    {
    }

    public static LvcInputHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager)
    {
        for (IHotkey hotkey : LvcHotkeys.HOTKEY_LIST)
        {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager)
    {
        manager.addHotkeysForCategory(LvcReference.MOD_NAME,
                LvcReference.MOD_ID + ".hotkeys.category.project", LvcHotkeys.HOTKEY_LIST);
    }

    @Override
    public boolean onKeyInput(KeyEvent input, boolean eventKeyState)
    {
        if (!eventKeyState || GuiUtils.getCurrentScreen() != null ||
                !LvcToolModes.isEditSubregionsActive())
        {
            return false;
        }

        return (input.key() == KeyCodes.KEY_ENTER || input.key() == KeyCodes.KEY_KP_ENTER) &&
                LvcSubRegionEditSession.applyCurrentBounds();
    }
}
