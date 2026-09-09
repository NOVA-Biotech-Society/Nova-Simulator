package simulation.vision;

import java.util.*;
import simulation.vision.PoseFrame.Landmark;

/** Dependency-free checks also callable by JUnit and on a plain JDK. */
public final class PoseMathChecks {
    private static int checks;
    private PoseMathChecks() { }
    public static void main(String[] args) { runAll(); System.out.println("Pose math: " + checks + " checks passed"); }
    public static void runAll() {
        checks = 0;
        standingAndAngles(); aspectRatioAndOrientation(); confidenceAndOcclusion(); filteringAndCalibration();
        validation();
    }
    public static List<Landmark> standing(int width, int height) {
        List<Landmark> points = new ArrayList<>(Collections.nCopies(33, new Landmark(.5, .5, 0, 1, 1)));
        for (int offset = 0; offset < 2; offset++) {
            put(points, 11 + offset, 500, 100, width, height);
            put(points, 23 + offset, 500, 250, width, height);
            put(points, 25 + offset, 500, 400, width, height);
            put(points, 27 + offset, 500, 550, width, height);
            put(points, 29 + offset, 480, 570, width, height);
            put(points, 31 + offset, 580, 570, width, height);
        }
        return points;
    }
    public static PoseFrame sample(long time, long id) { return map(new PoseMapper(), time, id, standing(1280,720), 1280,720); }
    private static PoseFrame map(PoseMapper mapper, long time, long id, List<Landmark> points, int width, int height) {
        return mapper.map(time, id, width, height, points, List.of());
    }
    private static void standingAndAngles() {
        PoseFrame standing = sample(1000,0);
        check(standing.trackingValid(), "standing tracks");
        close(standing.hipAngleRad(),0,"neutral hip"); close(standing.kneeAngleRad(),0,"neutral knee");
        close(standing.ankleAngleRad(),0,"flat foot neutral");
        List<Landmark> bent = standing(1280,720);
        put(bent,27,350,400,1280,720); put(bent,29,330,400,1280,720); put(bent,31,330,500,1280,720);
        PoseFrame knee = map(new PoseMapper(),1000,0,bent,1280,720);
        close(knee.kneeAngleRad(),Math.PI/2,"90 degree knee"); close(knee.ankleAngleRad(),0,"bent leg flat relative foot");
        List<Landmark> tilt = standing(1280,720);
        put(tilt,25,450,400,1280,720); put(tilt,27,400,550,1280,720);
        PoseFrame hip = map(new PoseMapper(),1000,0,tilt,1280,720);
        close(hip.hipAngleRad(),Math.atan2(50,150),"NOVA absolute hip sign");
        close(hip.kneeAngleRad(),0,"tilted straight knee");
        close(hip.ankleAngleRad(),Math.atan2(50,150),"positive dorsiflexion");
    }
    private static void aspectRatioAndOrientation() {
        List<Landmark> a=standing(1280,720), b=standing(800,800);
        put(a,25,450,400,1280,720); put(b,25,450,400,800,800);
        PoseFrame first=map(new PoseMapper(),1000,0,a,1280,720), second=map(new PoseMapper(),1000,0,b,800,800);
        close(first.rawHipAngleRad(),second.rawHipAngleRad(),"aspect-correct hip");
        close(first.rawKneeAngleRad(),second.rawKneeAngleRad(),"aspect-correct knee");
        List<Landmark> mirrored = a.stream().map(p -> new Landmark(1-p.x(),p.y(),p.z(),p.visibility(),p.presence())).toList();
        PoseMapper left=new PoseMapper(); left.configure(PoseMapper.Side.LEFT,PoseMapper.Facing.LEFT);
        PoseFrame mirror=map(left,1000,0,mirrored,1280,720);
        close(first.rawHipAngleRad(),mirror.rawHipAngleRad(),"facing inversion preserves hip");
        close(first.rawKneeAngleRad(),mirror.rawKneeAngleRad(),"mirror preserves knee");
        PoseMapper right=new PoseMapper(); right.configure(PoseMapper.Side.RIGHT,PoseMapper.Facing.RIGHT);
        close(map(right,1000,0,a,1280,720).kneeAngleRad(),0,"explicit right side independent of left");
    }
    private static void confidenceAndOcclusion() {
        List<Landmark> points=standing(1280,720);
        Landmark knee=points.get(25); points.set(25,new Landmark(knee.x(),knee.y(),0,1,.2));
        PoseFrame invalid=map(new PoseMapper(),1000,0,points,1280,720);
        check(!invalid.trackingValid(),"low presence rejects tracking"); close(invalid.kneeConfidence(),.2,"confidence propagates");
        points=standing(1280,720); points.set(27,new Landmark(-.1,.5,0,1,1));
        check(!map(new PoseMapper(),1000,0,points,1280,720).trackingValid(),"off-screen joint rejected");
        points=standing(1280,720); points.set(25,points.get(23));
        check(!map(new PoseMapper(),1000,0,points,1280,720).trackingValid(),"degenerate limb rejected");
        check(!map(new PoseMapper(),1000,0,List.of(),1280,720).trackingValid(),"missing subject rejected");
    }
    private static void filteringAndCalibration() {
        PoseMapper mapper=new PoseMapper();
        List<Landmark> standing=standing(1280,720), moving=standing(1280,720);
        put(moving,25,460,400,1280,720);
        map(mapper,1000,0,standing,1280,720);
        PoseFrame smoothed=map(mapper,1033,1,moving,1280,720);
        check(smoothed.hipAngleRad()>0 && smoothed.hipAngleRad()<smoothed.rawHipAngleRad(),"motion is smoothed");
        check(Math.abs(smoothed.hipAngularVelocityRadS())<=12,"velocity bounded");
        map(mapper,1066,2,List.of(),1280,720);
        close(map(mapper,1100,3,moving,1280,720).hipAngularVelocityRadS(),0,"occlusion resets derivative");
        close(map(mapper,2100,4,standing,1280,720).hipAngularVelocityRadS(),0,"gap resets derivative");
        mapper.calibrate();
        PoseFrame calibrated=null;
        for(int i=0;i<30;i++) calibrated=map(mapper,3000+i*33,5+i,standing,1280,720);
        check(calibrated.calibrated(),"30 stable frames calibrate");
        close(calibrated.hipAngleRad(),0,"standing calibrated hip");
        mapper.configure(PoseMapper.Side.RIGHT,PoseMapper.Facing.RIGHT);
        check(!map(mapper,5000,50,standing,1280,720).calibrated(),"orientation changes reset calibration");
        mapper.calibrate();
        for(int i=0;i<20;i++) map(mapper,6000+i*33,60+i,standing,1280,720);
        map(mapper,6700,80,List.of(),1280,720);
        for(int i=0;i<15;i++) calibrated=map(mapper,6800+i*33,81+i,standing,1280,720);
        check(!calibrated.calibrated(),"calibration requires consecutive visible frames");
        PoseCalibration neutral=new PoseCalibration(); neutral.begin();
        for(int i=0;i<40;i++) neutral.accept(new double[]{0,Math.toRadians(80),0});
        check(!neutral.isCalibrated(),"bent knee cannot become neutral");
        neutral.begin();
        for(int i=0;i<30;i++) neutral.accept(new double[]{.04,.03,-.05});
        close(neutral.offset(2),-.05,"calibration averages neutral offsets");
    }
    private static void validation() {
        boolean rejected=false;
        try { new Landmark(Double.NaN,0,0,1,1); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected,"nonfinite landmark rejected");
        rejected=false;
        try { map(new PoseMapper(),1000,0,List.of(new Landmark(0,0,0,1,1)),1280,720); }
        catch(IllegalArgumentException e) { rejected=true; }
        check(rejected,"partial skeleton rejected");
        PoseFrame frame=sample(1000,0); rejected=false;
        try { frame.landmarks().clear(); } catch(UnsupportedOperationException e) { rejected=true; }
        check(rejected,"landmarks immutable");
    }
    private static void put(List<Landmark> points,int i,double x,double y,int width,int height) {
        points.set(i,new Landmark(x/width,y/height,0,1,1));
    }
    private static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
    private static void close(double actual,double expected,String message) { check(Math.abs(actual-expected)<1e-7,message+": "+actual+" != "+expected); }
}
