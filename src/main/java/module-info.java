module simulation.app {
    requires javafx.controls;
    requires javafx.graphics;
    requires com.fazecast.jSerialComm;

    opens simulation.app to javafx.graphics;
    opens simulation.model to javafx.base;

    exports simulation.app;
    exports simulation.model;
    exports simulation.physics;
    exports simulation.controller;
    exports simulation.view;
    exports simulation.hardware;
}