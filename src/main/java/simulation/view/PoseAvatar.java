package simulation.view;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import simulation.model.HumanModel;
import simulation.vision.PoseFrame;

/** Observation-only ghost, deliberately holding no SimulationState/engine/controller reference. */
public final class PoseAvatar extends Group {
    private final Cylinder[] segments = new Cylinder[3];
    private final Sphere[] joints = new Sphere[4];
    public PoseAvatar() {
        PhongMaterial material = new PhongMaterial(Color.web("#53e0cb", 0.8));
        for (int i = 0; i < 3; i++) {
            segments[i] = new Cylinder(0.018, 0.4);
            segments[i].setMaterial(material);
            getChildren().add(segments[i]);
        }
        for (int i = 0; i < 4; i++) {
            joints[i] = new Sphere(0.025);
            joints[i].setMaterial(material);
            getChildren().add(joints[i]);
        }
        setMouseTransparent(true);
        setVisible(false);
        setTranslateZ(-0.12);
    }
    public void update(PoseFrame frame, HumanModel reference) {
        if (!frame.trackingValid()) { setVisible(false); return; }
        // Match NOVA's explicit segment convention: child absolute = parent + joint.
        double[] angles = {frame.hipAngleRad(), frame.hipAngleRad() + frame.kneeAngleRad(),
                frame.hipAngleRad() + frame.kneeAngleRad() + frame.ankleAngleRad()};
        double[] lengths = {reference.getThigh().getLength(), reference.getShank().getLength(), reference.getFoot().getLength()};
        double x = reference.getHipAnchorX(), y = reference.getHipAnchorY();
        for (int i = 0; i < 3; i++) {
            joints[i].setTranslateX(x); joints[i].setTranslateY(-y);
            segments[i].setHeight(lengths[i]);
            segments[i].getTransforms().setAll(new Translate(x, -y, 0),
                    new Rotate(-Math.toDegrees(angles[i]), Rotate.Z_AXIS), new Translate(0, lengths[i] / 2, 0));
            x += lengths[i] * Math.sin(angles[i]); y -= lengths[i] * Math.cos(angles[i]);
        }
        joints[3].setTranslateX(x); joints[3].setTranslateY(-y);
        setVisible(true);
    }
}
