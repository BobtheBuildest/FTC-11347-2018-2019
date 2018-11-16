// ***********************************************************************
// SwerveAuto
// ***********************************************************************
// The autonomous mode for swerve operations

//
// ****** IMPORTANT NOTE FOR STATE CHANGES ******
// The state machine used here has the ability to automatically add a wait
// before the next state starts operation. This is very useful for robot
// operations because the robot sometimes needs a delay before it starts
// any status checks in the following state. It allows the robot to start
// and action and wait before taking the next action.
//


// TODO - Add new log file just for autonomous state results? Or flag at END of each state...

package org.firstinspires.ftc.teamcode;

import com.disnodeteam.dogecv.CameraViewDisplay;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.disnodeteam.dogecv.DogeCV;
import com.disnodeteam.dogecv.CameraViewDisplay;
import org.firstinspires.ftc.teamcode.SamplingOrderDetector;
import com.qualcomm.robotcore.hardware.DcMotor;

// ***********************************************************************
// Definitions from Qualcomm code for OpMode recognition
// ***********************************************************************
// **** DO NOT ENABLE - Started from the Silver and Gold auto code derived classes now ****
// ****   @Autonomous(name="Swerve: 1-Auto 0.6", group="Swerve")
// ***********************************************************************
// 11/9/2018 - Swerve drive is now capable of auto correcting orientation to a degree while driving. The wheels actively adjust while moving
public class SwerveAuto extends SwerveCore {

    //private static Boolean useLightFlicker = Boolean.TRUE;

    // State machine for where we are on the autonomous journey

    // NEW states and initial working code done at GRITS meeting 10/13/2018

    enum autoStates {
        SWERVE_DONE,
        SWERVE_INIT,
        SWERVE_START,
        SWERVE_DROP,
        SWERVE_SLIDE,
        SWERVE_PRESCAN,
        SWERVE_SCAN,
        SWERVE_TO_PARTICLES,
        SWERVE_TO_WALL,
        SWERVE_WALL_PAUSE,
        SWERVE_WALL_TURN,
        SWERVE_TO_DEPOT,
        SWERVE_PLACE_MARKER,
        SWERVE_TO_PIT,
        SWERVE_LAST_MOVE,
        SWERVE_TEST_TURN_ROBOT,
        SWERVE_TEST_MOVE_ROBOT,
        SWERVE_TEST_MOVE1_ROBOT,
        SWERVE_TEST_MOVE2_ROBOT,
        SWERVE_TEST_BREAK_ROBOT,
        SWERVE_DELAY
    }

    private autoStates revTankState;
    private double stateStartTime;
    private double stateWaitTime;
    // report of our time wait
    private String checkReport;
    private String loopSenseStatus;
    private Boolean autoDriveWait;
    private Boolean autoDriveStop;


    // debug options to run a few states
    // -- enabled/controlled in SwerveAutoTEST init/start
    boolean debugActive = Boolean.FALSE;
    long debugStates = 1;
    autoStates debugStartState = autoStates.SWERVE_DONE;


    // Sensor data for robot positioning
    private float robotTurn[];
    private float robotMove[];
    private float lastSenseTime;

    // Robot orientation data
    private float robotOrientation[];
    private float robotSpeed[];
    private float robotPosition[];
    private int delaycount;
    // for Vuforia detection
    private SamplingOrderDetector detector;

    // variables for auto actions
    private int moveTimePushoff;

    // ***********************************************************************
    // SwerveAuto
    // ***********************************************************************
    // Constructs the class.
    // The system calls this member when the class is instantiated.
    public SwerveAuto() {
        // Initialize base classes.
        // All via self-construction.

        // Initialize class members.
        // All via self-construction.
    }


    // ***********************************************************************
    // getStateName
    // ***********************************************************************
    // Return the name of the current state
    public String getStateName(autoStates myState) {
        // Set the name for the state we are in
        switch (myState) {
            case SWERVE_INIT:
                return "INITIALIZING";

//          time based auto
            case SWERVE_START:
                return "START";
            case SWERVE_DROP:
                return "Drop down from lander";
            case SWERVE_SLIDE:
                return "Slide over after drop";
            case SWERVE_TO_PARTICLES:
                return "Move to particles";
            case SWERVE_WALL_PAUSE:
                return "Wait for partner at the wall";
            case SWERVE_TO_WALL:
                return "Run to the wall";
            case SWERVE_WALL_TURN:
                return "Turn/orient at the wall";
            case SWERVE_TO_DEPOT:
                return "Head for the depot";
            case SWERVE_PLACE_MARKER:
                return "Place the team marker";
            case SWERVE_TO_PIT:
                return "Move into the pit";
            case SWERVE_LAST_MOVE:
                return "Waiting for last move to complete";
            case SWERVE_DONE:
                return "All done - robot waits while stopped";

//          encoder based auto


//          Testing cases
            case SWERVE_TEST_TURN_ROBOT:
                return "TEST - turn robot";
            case SWERVE_TEST_MOVE_ROBOT:
                return "TEST - move robot";
            case SWERVE_DELAY:
                return "TEST - move robot";
            default:
                return "UNKNOWN! ID = " + revTankState;
        }
    }


    // ***********************************************************************
    // getCurStateName
    // ***********************************************************************
    // Return the name of the current state
    public String getCurStateName() {
        return getStateName(revTankState);
    }


    // ***********************************************************************
    // Init
    // ***********************************************************************
    // Performs any actions that are necessary when the OpMode is enabled.
    // The system calls this member once when the OpMode is enabled.
    @Override
    public void init() {
        double initWheelAngle;
        double initWheelPower;

        swerveDebug(500, "SwerveAuto::init", "START");

        initWheelAngle = 0.3;
        initWheelPower = 0.02;
        // Run initialization of other parts of the class
        // Note that the class will connect to all of our motors and servos

        super.init();

        swerveDebug(500, "SwerveAuto::init", "Back from super.init");

        //DogeCV Initialization
        detector = new SamplingOrderDetector();
        // CameraIndex: 0 is back, 1 is front
        detector.init(hardwareMap.appContext, CameraViewDisplay.getInstance(),1,Boolean.FALSE);
        detector.useDefaults();
        detector.downscale= 0.4;
        detector.areaScoringMethod = DogeCV.AreaScoringMethod.MAX_AREA; // Can also be PERFECT_AREA
        // 2 different types of scoring
        //detector.perfectAreaScorer.perfectArea = 10000; // if using PERFECT_AREA scoring
        detector.maxAreaScorer.weight = 0.001;

        detector.ratioScorer.weight = 15;
        detector.ratioScorer.perfectRatio = 1.0;
       detector.enable();

        swerveDebug(500, "SwerveAuto::init", "DogeCV initialized");


        // orient to the field now and save our angle for use in teleOp
        ourSwerve.setFieldOrientation();

        // set initial pushoff delay
        moveTimePushoff = 400;
        // force the marker drop servo to hold tight
        gameMarkDrop.setPosition(0);

        // cause all the wheels to turn to the initialization position - 45 degrees
        swerveLeftFront.updateWheel(initWheelPower, -initWheelAngle);
        swerveRightFront.updateWheel(initWheelPower, initWheelAngle);
        swerveLeftRear.updateWheel(initWheelPower, initWheelAngle);
        swerveRightRear.updateWheel(initWheelPower, -initWheelAngle);

        // wait for the wheels to turn
        swerveSleep(500);

        // stop power to the wheels - servos stay locked
        swerveLeftFront.updateWheel(0, -initWheelAngle);
        swerveRightFront.updateWheel(0, initWheelAngle);
        swerveLeftRear.updateWheel(0, initWheelAngle);
        swerveRightRear.updateWheel(0, -initWheelAngle);

        swerveDebug(500, "SwerveAuto::init", "Swerve wheels in init positions");

        // Robot and autonomous settings are read in from files in the core class init()
        // Report the autonomous settings
        // ***** now done in silver & gold *****
        // ***** showAutonomousGoals();

        swerveDebug(500, "SwerveAuto::init", "DONE");
    }


    // ***********************************************************************
    // start
    // ***********************************************************************
    // Do first actions when the start command is given.
    // Called once when the OpMode is started.
    @Override
    public void start() {
        swerveDebug(500, "SwerveAuto::start", "START");

        // Call the super/base class start method.
        super.start();

        // Slowly shift the wheel power
        //useGradualWheelChange = Boolean.TRUE;

        // no auto drive yet
        autoDriveWait = Boolean.FALSE;

        // nothing sensed yet
        loopSenseStatus = "No sensing yet";

        // Start in the initial robot state
        revTankState = autoStates.SWERVE_INIT;
        setState(autoStates.SWERVE_START, 0);

        swerveDebug(500, "SwerveAuto::start", "DONE");
    }


    // ***********************************************************************
    // loop
    // ***********************************************************************
    // State machine for autonomous robot control
    // Called continuously while OpMode is running
    //
    // Note that since this is autonomous and we do not care about reading
    // joysticks.
    @Override
    public void loop() {
        // Normal logging of loop start
        swerveDebug(500, "SwerveAuto::loop", "START, state is " +
                getCurStateName() + "'");
        swerveDebug(50, "SwerveAuto::loop", "Sensing status: " +
                loopSenseStatus);

        // check for auto drive
        if ( autoDriveWait ) {
            if ( ourSwerve.autoDriveCheck( autoDriveStop )) {
                autoDriveWait = Boolean.FALSE;

                // auto finished in time, so no more waiting
                stateWaitTime = 0.0;
            }
        }

        // if we are waiting, move on
        if (!checkStateReady()) {
            swerveDebug(500, "SwerveAuto::loop", "Waiting for steady state (" +
                    checkReport + ")");

            loopEndReporting();
            return;
        }

        // Move based on the current state
        switch (revTankState) {
            // SHOULD NEVER HAPPEN - INIT while we are running....
            case SWERVE_INIT:
                // INIT is only used to have some state before we set START in start()
                swerveDebug(1, "SwerveAuto::loop *ERROR*", "== loop with INIT state");
                setState(autoStates.SWERVE_START, 0);
                delaycount=0;
                break;

            // First state - wait for autoDelay to complete before moving
            case SWERVE_START:
                // jump to debug if active
                if (debugActive) {
                    setState(debugStartState, 0);
                } else {


                    // start the drop
//                    setState(autoStates.SWERVE_SLIDE, 0);


                    setState(autoStates.SWERVE_SLIDE, 0);
                }
                break;

            // Drop down from the lander
            case SWERVE_DROP:

                lineSlideArm.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
                lineSlideArm.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                lineSlideArm.setTargetPosition(3800);

                lineSlideArm.setPower(1);

                swerveLeftFront.updateWheel(0.0, 0.7);
                swerveRightFront.updateWheel(0.0, 0.7);
                swerveLeftRear.updateWheel(0.0, 0.7);
                swerveRightRear.updateWheel(0.0, 0.7);

                // wait, then slide
                setState(autoStates.SWERVE_TO_PARTICLES, 8500);
                break;

            // Slide sideways
            // running into robots so we added a delay for when we need it
            case SWERVE_DELAY:
                // delay
                setState(autoStates.SWERVE_SLIDE, 0);
                break;
            case SWERVE_SLIDE:
                ourSwerve.autoDrive(0.7, 85, 0, 15);
                autoDriveWait = Boolean.TRUE;


                // turn for the planned time
                setState(autoStates .SWERVE_TO_PARTICLES, 500);
                break;

// Move to the particles

            case SWERVE_TO_PARTICLES:
                ourSwerve.autoDrive( 0.3, 0.0, 0.0, 30.0 );
                autoDriveWait = Boolean.TRUE;
                autoDriveStop = Boolean.FALSE;

                // turn for the planned time
                setState(autoStates.SWERVE_TO_WALL, 2000);
                break;

            // Move to the wall
            case SWERVE_TO_WALL:
                if (targetSilver) {
                    ourSwerve.autoDrive( 0.4, 275.0, 45.0, 212.0 );


                    autoDriveWait = Boolean.TRUE;
                    autoDriveStop = Boolean.TRUE;

                    // wait for wall move
                    setState(autoStates.SWERVE_WALL_TURN, 3000);
                } else {
                    ourSwerve.driveRobot(-0.6, 0.4, 0.35, 0);
                    // wait for wall move
                    setState(autoStates.SWERVE_WALL_PAUSE, 3000);
                }
                break;

            // Orient right at the wall
            case SWERVE_WALL_TURN:
                ourSwerve.stopRobot();

                swerveSleep(600);
                //orientRobot(40.0);

                // turn for the planned time
                setState(autoStates.SWERVE_WALL_PAUSE, 2000);
                break;


            // orient to wall
            case SWERVE_WALL_PAUSE:
                // stop the robot
                ourSwerve.stopRobot();

                // wait for delay
                setState(autoStates.SWERVE_TO_DEPOT, 300);
                break;

            // Move to the depot
            case SWERVE_TO_DEPOT:
                ourSwerve.autoDrive( 0.60, 225.0, 45.0, 100.0 );
                autoDriveWait = Boolean.TRUE;
                autoDriveStop = Boolean.TRUE;

                // drive to the depot
                if (targetSilver) {
                    setState(autoStates.SWERVE_PLACE_MARKER, 4000);
                } else {
                    setState(autoStates.SWERVE_PLACE_MARKER, 4000);
                }
                break;

            // place marker
            case SWERVE_PLACE_MARKER:
                // stop robot
                ourSwerve.stopRobot();

                // drop the marker
                gameMarkDrop.setPosition(1);

                // wait for marker drop
                setState(autoStates.SWERVE_TO_PIT, 1000);
                break;

            // Move to the pit
            case SWERVE_TO_PIT:
                ourSwerve.autoDrive( 0.70, 45.0, 47.0, 195.0 );
                autoDriveWait = Boolean.TRUE;
                autoDriveStop = Boolean.TRUE;

                // TODO: check for roll at pit


                // turn for the planned time
                setState(autoStates.SWERVE_LAST_MOVE, 10000);
                break;

            // Waiting for final move to complete before done
            // We use this because waiting for this state still gives reporting of wait status
            case SWERVE_LAST_MOVE:
                // stop moving
                ourSwerve.stopRobot();

                setState(autoStates.SWERVE_DONE, 10);
                break;



            // All moves are done
            case SWERVE_DONE:
                // stop any movement
                ourSwerve.stopRobot();

                // TODO - be sure any servos/other motors in use are stopped

                break;

            // **** TEST cases **** //
            // test turning robot to face 90 degrees
            case SWERVE_TEST_TURN_ROBOT:
                orientRobot(90.0);
                orientRobot(90.0);
                setState(autoStates.SWERVE_DONE, 0);
                break;

            // test moving the robot 60cm (one mat tile, roughly) at 20 degrees
            case SWERVE_TEST_MOVE_ROBOT:
                ourSwerve.autoDrive( 0.6, 90.0, 20.0, 120.0 );
                autoDriveWait = Boolean.TRUE;
                autoDriveStop = Boolean.TRUE;

                setState(autoStates.SWERVE_DONE, 4000);
                break;

            case SWERVE_TEST_MOVE1_ROBOT:
                ourSwerve.autoDrive( 0.4, 90.0, 0.0, 60.0 );
                autoDriveWait = Boolean.TRUE;
                autoDriveStop = Boolean.TRUE;

                setState(autoStates.SWERVE_TEST_MOVE2_ROBOT, 4000);
                break;

            case SWERVE_TEST_MOVE2_ROBOT:
                ourSwerve.autoDrive( 0.4, 180.0, 0.0, 60.0 );
                autoDriveWait = Boolean.TRUE;
                autoDriveStop = Boolean.TRUE;

                setState(autoStates.SWERVE_DONE, 4000);
                break;

//            case SWERVE_TEST_BREAK_ROBOT:
//                ourSwerve.autoBreak(-0.8);
//
//
//                setState(autoStates.SWERVE_DONE, 300);
//                break;


            default:
                swerveDebug(1, "SwerveAuto::loop *ERROR*", "== unknown case state " +
                        getCurStateName() + " (" + revTankState + ")");
                setState(autoStates.SWERVE_DONE, 0);
                break;
        }

        // Report changes if not done
        if ( debugActive || ( revTankState != autoStates.SWERVE_DONE )) {
            loopEndReporting();
        }

        swerveDebug(500, "SwerveAuto::loop", "LOOP PASS DONE");
    }


    // ***********************************************************************
    // stop
    // ***********************************************************************
    // Performs any actions that are necessary when the OpMode is disabled.
    // The system calls this member once when the OpMode is disabled.
    @Override
    public void stop() {
        swerveDebug(500, "SwerveAuto::stop", "START");
        detector.disable();
        // Disables vuforia on stop.





        // Call the super/base class stop method
        super.stop();

        swerveDebug(500, "SwerveAuto::stop", "DONE");
    }


    // ***********************************************************************
    // setState
    // ***********************************************************************
    // Set a new state to run, with a delay before it activates
    private void setState(autoStates myTarget, double myDelay) {

        // when debugging, be ready to stop
        if (debugActive && (debugStates-- < 1)) {
            swerveDebug(500, "SwerveAuto::setState", "DEBUG LIMIT - end now");
            myTarget = autoStates.SWERVE_DONE;
        }

        // Report change of state
        if (myTarget != revTankState) {
            swerveDebug(500, "SwerveAuto::setState", "STATE CHANGE--from '" +
                    getCurStateName() + "'  to '" +
                    getStateName(myTarget));
        }

        // Record the starting time for the new state
        stateStartTime = getRuntime();
        // Recored the intended delay
        stateWaitTime = myDelay;

        // set the state...
        revTankState = myTarget;

        // Send telemetry data to the driver station.
        swerveLog("State", "Autonomous State: " + getCurStateName() +
                ", state time = " + swerveNumberFormat.format(getRuntime() - stateStartTime));
    }


    // ***********************************************************************
    // checkStateReady
    // ***********************************************************************
    // Is the target delay past?
    // Any other conditions to wait for before changing?
    private Boolean checkStateReady() {
        // Check current time and requested delay
        if (!checkStateElapsed(stateWaitTime)) {
            return Boolean.FALSE;
        }

        // Add waits for motor positions or anything else here...

        return Boolean.TRUE;
    }


    // ***********************************************************************
    // checkStateElapsed
    // ***********************************************************************
    // Is the target delay past?
    private Boolean checkStateElapsed(double myDelay) {
        double curTime;
        double curDelay;

        curTime = getRuntime();
        curDelay = (curTime - stateStartTime) * 1000;

        // Check current time and requested delay
        if (curDelay < myDelay) {
            checkReport = "== current (" +
                    swerveNumberFormat.format(curDelay) + "' less than '" +
                    swerveNumberFormat.format(myDelay);

            // ONLY show this for very high debug levels, or it will overflow the logs
            swerveDebug(5000, "SwerveAuto::checkStateElapsed", "**Delaying** " + checkReport);

            if (debugLevel < 5000) {
                telemetry.addData("CheckElapsed", checkReport);
            }

            return Boolean.FALSE;
        } else if (debugLevel < 5000) {
            telemetry.addData("CheckElapsed", "--DONE--");
        }

        // Add waits for motor positions or anything else here...

        return Boolean.TRUE;
    }


    // ***********************************************************************
    // orientRobot
    // ***********************************************************************
    // turn the robot to a specific orientation
    private Boolean orientRobot(double newOrientationDegrees) {

        double newOrienation = newOrientationDegrees;
        double turnSpeed;

        // be sure we are not using automation
        ourSwerve.setSwerveMode(SwerveDrive.swerveModes.SWERVE_DRIVER);

        // check robot orientation
        ourSwerve.checkOrientation();

        // turn until within ~10 degrees
        while (Math.abs(newOrienation - ourSwerve.curHeading) > 5.0) {

            // never take longer than 1.5 seconds
            if (checkStateElapsed(10000)) {
                // stop the robot
                ourSwerve.stopRobot();

                return (Boolean.FALSE);
            }

            // turn faster if we need to turn more
            if (Math.abs(newOrienation - ourSwerve.curHeading) > 90.0) {
                turnSpeed = 0.20;
            } else {
                turnSpeed = 0.10;
            }
            if (newOrienation > ourSwerve.curHeading) {
                turnSpeed = -turnSpeed;
            }

            // turn the robot
            ourSwerve.driveRobot(0.0, 0.0, turnSpeed, 0.0);
        }

        // stop the robot
        ourSwerve.stopRobot();

        return (Boolean.TRUE);
    }

    void brakeOn(){
        motorLeftFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        motorLeftRear.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        motorRightFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        motorRightRear.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
    }

    void brakeOff(){
        motorLeftFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        motorLeftRear.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        motorRightFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        motorRightRear.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
    }

}
