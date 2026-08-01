module com.becker {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.becker to javafx.fxml;
    exports com.becker;
    exports com.becker.pieces;
}
