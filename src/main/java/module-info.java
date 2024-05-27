module org.example.shapecarvejavafx {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.shapecarvejavafx to javafx.fxml;
    exports org.example.shapecarvejavafx;
}