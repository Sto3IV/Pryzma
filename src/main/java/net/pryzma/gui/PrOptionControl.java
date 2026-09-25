package net.pryzma.gui;

/** A widget bound to one option: it can redraw its label and names its tooltip lines. */
interface PrOptionControl {
    String tooltipKey();

    void refresh();
}
