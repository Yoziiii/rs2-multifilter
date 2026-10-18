package dev.rs2multifilter.minecraft.client;

import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Left click keeps the existing action; right click only copies the full internal value. */
public class RuleEntryButton extends EditorButton {
    private String clipboardValue;
    private final Consumer<String> copy;

    public RuleEntryButton(int x, int y, int width, int height, Component label,
                           OnPress press, String clipboardValue, Consumer<String> copy) {
        super(x, y, width, height, label, press);
        this.clipboardValue = clipboardValue;
        this.copy = copy;
    }

    protected void setClipboardValue(String value) { clipboardValue = value; }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1 && active && visible && isMouseOver(x, y)) {
            copy.accept(clipboardValue);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
}
