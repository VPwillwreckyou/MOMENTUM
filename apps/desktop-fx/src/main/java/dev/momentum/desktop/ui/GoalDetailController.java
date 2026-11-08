package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Goal detail screen — implementation lands in the Goals increment of Phase 4. */
public class GoalDetailController implements PageView {

    private final VBox root = new VBox(16);
    private final Label placeholder = new Label("Goal detail coming soon.");

    public GoalDetailController(ServiceHost host, ThemeManager themes, ToastHost toasts,
                                ModalOverlay modals, NavigationManager navigation) {
        root.getStyleClass().add("page");
        root.getChildren().addAll(new Label("Goal detail"), placeholder);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        placeholder.setText("Opening goal: " + String.valueOf(parameter));
    }
}
