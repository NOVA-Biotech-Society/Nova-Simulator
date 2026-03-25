package simulation.model;

/**
 * Represents the human leg model: three rigid body segments (thigh, shank, foot)
 * connected by three joints (hip, knee, ankle).
 * <p>
 * The model is constructed from body parameters (height, mass, segment ratios).
 * A pelvis anchor point provides the fixed attachment at the hip.
 * </p>
 */
public class HumanModel {

    // --- Segments ---
    private final RigidBodySegment thigh;
    private final RigidBodySegment shank;
    private final RigidBodySegment foot;

    // --- Joints ---
    private final Joint hipJoint;
    private final Joint kneeJoint;
    private final Joint ankleJoint;

    // --- Body parameters ---
    private double height;      // m
    private double totalMass;   // kg
    private double thighLength;
    private double shankLength;
    private double footLength;
    double thighMass = totalMass;
    double shankMass = totalMass;
    double footMass  = totalMass;

    // Pelvis (hip) anchor position in world coordinates
    private double hipAnchorX;
    private double hipAnchorY;

    /**
     * Creates a human leg model from basic body parameters.
     * Segment lengths and masses are derived from anthropometric ratios.
     *
     * @param height    total body height in meters
     * @param totalMass total body mass in kg
     */
    public HumanModel(double height, double totalMass) {
        this.height = height;
        this.totalMass = totalMass;

        double thighMass = totalMass * 0.10;   // ~10% of body mass per thigh
        double shankMass = totalMass * 0.047;  // ~4.7% per shank
        double footMass  = totalMass * 0.014;  // ~1.4% per foot

        double segmentWidth = 0.06;             // rendering width in meters

        // Create segments
        thigh = new RigidBodySegment("Thigh", thighMass, thighLength, segmentWidth);
        shank = new RigidBodySegment("Shank", shankMass, shankLength, segmentWidth * 0.85);
        foot  = new RigidBodySegment("Foot",  footMass,  footLength,  segmentWidth * 0.7);

        // Anthropometric ratios (approximate, from Winter's biomechanics)
        updateSegmentationDimensions();
        updateSegmentationMass();

        // Create joints with physiological angle limits (radians)
        // Hip joint: represents the absolute angle of the thigh relative to the vertical.
        // We use thigh as both parent and child since there's no explicit pelvis segment.
        // The hip anchor point is fixed in space; the thigh rotates about it.
        // Hip: extension (-30°) to flexion (+130°)
        hipJoint = new Joint("Hip", JointType.HIP, thigh, thigh,
                Math.toRadians(-30), Math.toRadians(130));

        // Knee joint: shank relative to thigh
        // Convention: knee flexion is positive, extension is 0.
        // Physiological range: 0° (straight) to ~140° (full flexion)
        kneeJoint = new Joint("Knee", JointType.KNEE, thigh, shank,
                Math.toRadians(0), Math.toRadians(140));

        // Ankle joint: foot relative to shank
        // Convention: dorsiflexion positive, plantarflexion negative
        // Range: -50° (plantarflexion) to +30° (dorsiflexion)
        ankleJoint = new Joint("Ankle", JointType.ANKLE, shank, foot,
                Math.toRadians(-50), Math.toRadians(30));

        // Set damping
        hipJoint.setDamping(2.0);
        kneeJoint.setDamping(1.5);
        ankleJoint.setDamping(1.0);

        // Default hip anchor (standing on ground, hip at top)
        hipAnchorX = 0;
        hipAnchorY = thighLength + shankLength + footLength;
    }

    /**
     * Resets the model to the initial standing pose (all segments vertical, all angles zero).
     */
    public void resetToStandingPose() {
        double thighLen = thigh.getLength();
        double shankLen = shank.getLength();
        double footLen  = foot.getLength();

        hipAnchorY = thighLen + shankLen + footLen;
        hipAnchorX = 0;

        // All segments vertical (angle = 0)
        thigh.setAngle(0);
        thigh.setPosX(hipAnchorX);
        thigh.setPosY(hipAnchorY);
        thigh.setVelX(0); thigh.setVelY(0); thigh.setAngularVelocity(0);

        shank.setAngle(0);
        shank.setPosX(thigh.getDistalX());
        shank.setPosY(thigh.getDistalY());
        shank.setVelX(0); shank.setVelY(0); shank.setAngularVelocity(0);

        foot.setAngle(0);
        foot.setPosX(shank.getDistalX());
        foot.setPosY(shank.getDistalY());
        foot.setVelX(0); foot.setVelY(0); foot.setAngularVelocity(0);

        hipJoint.setAngle(0);
        hipJoint.setAngularVelocity(0);
        kneeJoint.setAngle(0);
        kneeJoint.setAngularVelocity(0);
        ankleJoint.setAngle(0);
        ankleJoint.setAngularVelocity(0);
    }

    /**
     * Enforces positional constraints so segments stay connected at joints.
     * The thigh's proximal end is pinned to the hip anchor; shank follows thigh; foot follows shank.
     */
    public void enforcePositionConstraints() {
        // Pin thigh to hip anchor
        thigh.setPosX(hipAnchorX);
        thigh.setPosY(hipAnchorY);

        // Shank proximal end = thigh distal end
        shank.setPosX(thigh.getDistalX());
        shank.setPosY(thigh.getDistalY());

        // Foot proximal end = shank distal end
        foot.setPosX(shank.getDistalX());
        foot.setPosY(shank.getDistalY());
    }

    public void updateSegmentationDimensions(){
        this.thighLength = height * 0.245;   // ~24.5% of height
        this.shankLength = height * 0.246;   // ~24.6% of height
        this.footLength  = height * 0.039;   // foot "height" for vertical representation
        thigh.setLength(this.thighLength);
        shank.setLength(this.shankLength);
        foot.setLength(this.footLength);
    }

    public void updateSegmentationMass(){
        this.thighMass = totalMass * 0.10;   // ~10% of body mass per thigh
        this.shankMass = totalMass * 0.047;  // ~4.7% per shank
        this.footMass  = totalMass * 0.014;  // ~1.4% per foot
        thigh.setMass(this.thighMass);
        shank.setMass(this.shankMass);
        foot.setMass(this.footMass);
    }


    // ---- Getters ----

    public RigidBodySegment getThigh() { return thigh; }
    public RigidBodySegment getShank() { return shank; }
    public RigidBodySegment getFoot()  { return foot; }

    public Joint getHipJoint()   { return hipJoint; }
    public Joint getKneeJoint()  { return kneeJoint; }
    public Joint getAnkleJoint() { return ankleJoint; }

    public Joint[] getAllJoints() { return new Joint[]{ hipJoint, kneeJoint, ankleJoint }; }
    public RigidBodySegment[] getAllSegments() { return new RigidBodySegment[]{ thigh, shank, foot }; }

    public double getHeight() { return height; }
    public double getTotalMass() { return totalMass; }
    public double getHipAnchorX() { return hipAnchorX; }
    public double getHipAnchorY() { return hipAnchorY; }
    public void setHipAnchorX(double x) { this.hipAnchorX = x; }
    public void setHipAnchorY(double y) { this.hipAnchorY = y; }
    public void setHeight(double height) {
        this.height = height;
        updateSegmentationDimensions();
        enforcePositionConstraints();
    }
    public void setTotalMass(double totalMass) {
        this.totalMass = totalMass;
        updateSegmentationMass();
        enforcePositionConstraints();
    }
}


