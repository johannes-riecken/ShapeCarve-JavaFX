module org.example.shapecarvejavafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.fxyz3d.core;
    requires org.jetbrains.annotations;


    opens org.example.shapecarvejavafx to javafx.fxml;
    exports org.example.shapecarvejavafx;
}