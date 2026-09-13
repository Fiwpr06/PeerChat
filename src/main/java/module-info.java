module com.peerchat {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.logging;

    opens com.peerchat.client to javafx.graphics, javafx.fxml;
    opens com.peerchat.client.controller to javafx.fxml;
    opens com.peerchat.client.model to javafx.base;
    opens com.peerchat.shared.model to javafx.base;

    exports com.peerchat.client;
    exports com.peerchat.client.controller;
    exports com.peerchat.client.model;
    exports com.peerchat.shared.model;
    exports com.peerchat.shared.protocol;
    exports com.peerchat.shared.util;
    exports com.peerchat.server;
}