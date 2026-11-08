package dev.momentum.desktop.ui;

import javafx.scene.Node;

/** A page of the shell: owns its root node and refreshes on show. */
public interface PageView {
    Node root();
    void onShown(Object parameter);
}
