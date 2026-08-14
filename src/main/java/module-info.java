module com.becker {
    requires java.sql;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.xerial.sqlitejdbc;

    opens com.becker to javafx.fxml;
    exports com.becker;
    exports com.becker.pieces;
}
