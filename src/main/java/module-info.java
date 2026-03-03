module simulation.app {
    requires javafx.controls;
    requires javafx.graphics;
    opens simulation.app to javafx.graphics;
    opens simulation.model to javafx.base;
    exports simulation.app;
    exports simulation.model;
    exports simulation.physics;
    exports simulation.controller;
    exports simulation.view;
}