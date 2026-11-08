package dev.momentum.desktop.ui;

import dev.momentum.auth.AccountStore;
import dev.momentum.desktop.host.ServiceHost;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Authentication screen: register a new account or sign in. Writes through
 * MomentumService to the real account store; sets the shared session token.
 */
public class AuthController implements PageView {

    private final ServiceHost host;
    private final ToastHost toasts;

    private final VBox root = new VBox(16);
    private final TabPane tabs = new TabPane();

    private final TextField regEmail = new TextField();
    private final PasswordField regPassword = new PasswordField();
    private final TextField regName = new TextField();
    private final Button regButton = new Button("Create account");

    private final TextField logEmail = new TextField();
    private final PasswordField logPassword = new PasswordField();
    private final Button logButton = new Button("Sign in");

    public AuthController(ServiceHost host, ToastHost toasts) {
        this.host = host;
        this.toasts = toasts;

        root.getStyleClass().add("page");
        root.setPadding(new Insets(0));
        root.getChildren().addAll(header(), tabs);

        tabs.getStyleClass().add("auth-tabs");

        Tab registerTab = new Tab("Create account");
        registerTab.setClosable(false);
        registerTab.setContent(registerForm());
        tabs.getTabs().add(registerTab);

        Tab loginTab = new Tab("Sign in");
        loginTab.setClosable(false);
        loginTab.setContent(loginForm());
        tabs.getTabs().add(loginTab);

        regButton.getStyleClass().addAll("btn", "btn-primary");
        regButton.setOnAction(event -> doRegister());
        logButton.getStyleClass().addAll("btn", "btn-primary");
        logButton.setOnAction(event -> doLogin());
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void onShown(Object parameter) {
        // Nothing to refresh.
    }

    /* ---------------------------------------------------------------- layout */

    private Node header() {
        Label title = new Label("Authentication");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(10, title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getStyleClass().add("page-head");
        return header;
    }

    private Node registerForm() {
        regEmail.setPromptText("Email");
        regPassword.setPromptText("Password");
        regName.setPromptText("Name");
        regEmail.setPrefWidth(260);
        regPassword.setPrefWidth(220);
        regName.setPrefWidth(220);

        VBox form = new VBox(10,
                field("Email", regEmail),
                field("Name", regName),
                field("Password", regPassword),
                regButton);
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("auth-form");
        VBox card = new VBox(10, new Label("Create a local account"), form);
        card.getStyleClass().add("card");
        card.setMaxWidth(520);
        HBox wrap = new HBox(10, card);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private Node loginForm() {
        logEmail.setPromptText("Email");
        logPassword.setPromptText("Password");
        logEmail.setPrefWidth(260);
        logPassword.setPrefWidth(220);

        VBox form = new VBox(10,
                field("Email", logEmail),
                field("Password", logPassword),
                logButton);
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("auth-form");
        VBox card = new VBox(10, new Label("Sign in to your account"), form);
        card.getStyleClass().add("card");
        card.setMaxWidth(520);
        HBox wrap = new HBox(10, card);
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private static Node field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, control);
        box.getStyleClass().add("form-field-box");
        return box;
    }

    /* ---------------------------------------------------------------- actions */

    private void doRegister() {
        String email = regEmail.getText();
        String name = regName.getText();
        String password = regPassword.getText();
        if (email == null || email.isBlank() || name == null || name.isBlank() || password == null || password.isBlank()) {
            toasts.show("Email, name and password are required.", ToastHost.Tone.ERROR);
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AccountStore.AuthResult res = host.service().register(email, password, name);
                host.setCurrentToken(res.token());
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            toasts.show("Account created and signed in.", ToastHost.Tone.SUCCESS);
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Registration failed: " + task.getException().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "auth-register").start();
    }

    private void doLogin() {
        String email = logEmail.getText();
        String password = logPassword.getText();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            toasts.show("Email and password are required.", ToastHost.Tone.ERROR);
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AccountStore.AuthResult res = host.service().login(email, password);
                host.setCurrentToken(res.token());
                return null;
            }
        };
        task.setOnSucceeded(event -> Platform.runLater(() -> {
            toasts.show("Signed in.", ToastHost.Tone.SUCCESS);
        }));
        task.setOnFailed(event -> Platform.runLater(() ->
                toasts.show("Sign in failed: " + task.getException().getMessage(), ToastHost.Tone.ERROR)));
        new Thread(task, "auth-login").start();
    }
}
