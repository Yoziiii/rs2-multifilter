package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.gui.GuiGraphics;

/** Small integer-aligned pixel masks; no font-dependent symbol glyphs. */
public enum EditorIcon {
    PREVIOUS("     #", "    ##", "   ##", "  ##", "   ##", "    ##", "     #"),
    NEXT("  #", "  ##", "   ##", "    ##", "   ##", "  ##", "  #"),
    ADD("   ##", "   ##", "   ##", "########", "########", "   ##", "   ##", "   ##"),
    EXCLUDE("   ##", "   ##  #", "   ##  #", "###### #", "###### #", "   ##", "   ##  #", "   ##  #"),
    TRASH("   ####", " ########", "", "  ######", "  # ## #", "  # ## #", "  # ## #", "  ######"),
    CLEAR("      ##", "     ##", "    ##", "   ####", "  ######", " ######", "######", " # # #"),
    CONFIRM("      ###", "      ###", "     ###", " ## ###", " #####", "  ###", "   #"),
    ANY("##    ##", "##    ##", "", "###  ###", " ######", "  ####", "   ##"),
    ALL("##    ##", "##    ##", "", "   ##", "  ####", " ######", "###  ###"),
    DENY("  ####", " ##  ##", "##  ####", "## ## ##", "####  ##", " ##  ##", "  ####"),
    FUZZY("##   ##", "##   ##", "", " ######", "", "##   ##", "##   ##"),
    EXACT("##    ##", " #    #", "  ####", "  ####", " #    #", "##    ##"),
    PICTURE("########", "#    # #", "#      #", "#   ## #", "#  ######", "# ###  #", "########"),
    GRID("## ## ##", "## ## ##", "", "## ## ##", "## ## ##", "", "## ## ##", "## ## ##"),
    PIN(" ######", "  ####", "  ####", "  ####", "########", "   ##", "   ##", "   #"),
    FOLDER("####", "########", "#      #", "#  ##  #", "# #### #", "#  ##  #", "########");

    private final String[] pixels;
    EditorIcon(String... pixels) { this.pixels = pixels; }

    public void render(GuiGraphics g, int centerX, int centerY, int color) {
        int w = 0;
        for (String row : pixels) w = Math.max(w, row.length());
        int x = centerX - w / 2, y = centerY - pixels.length / 2;
        for (int row = 0; row < pixels.length; row++)
            for (int col = 0; col < pixels[row].length(); col++)
                if (pixels[row].charAt(col) == '#') g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
    }
}
