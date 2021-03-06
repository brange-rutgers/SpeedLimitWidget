package edu.rutgers.brange.speedlimitwidget;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.MatchedGeoPosition;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.common.RoadElement;
import com.here.android.mpa.electronic_horizon.ElectronicHorizon;
import com.here.android.mpa.electronic_horizon.Link;
import com.here.android.mpa.electronic_horizon.PathTree;
import com.here.android.mpa.electronic_horizon.Position;
import com.here.android.mpa.guidance.NavigationManager;
import com.here.android.mpa.prefetcher.MapDataPrefetcher;
import com.here.android.mpa.routing.Maneuver;
import com.here.android.mpa.routing.Route;
import com.here.android.mpa.routing.RouteElement;
import com.here.android.mpa.routing.RouteElements;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;
import com.here.android.mpa.search.ErrorCode;
import com.here.android.mpa.search.GeocodeRequest2;
import com.here.android.mpa.search.GeocodeResult;
import com.here.android.mpa.search.ResultListener;

import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;
import static edu.rutgers.brange.speedlimitwidget.FavoritesCursorAdapter.SPEED_TRAP_THRESHOLD;

public class FloatingViewService extends Service {

    //private static final int SAMPLE_RATE = 50;//60mph in 3 seconds
    private static final int SAMPLE_RATE = 100;//60mph in 6 seconds
    private WindowManager mWindowManager;
    private View mFloatingView;
    private ResizableLayout mResizableLayout;

    static final int LONG_PRESS_TIME = 250;
    static Tuple<GeoCoordinate, Timestamp> lastPostion;
    static LocationManager locationManager;
    static LocationListener locationListener;
    static MapEngine mapEngine;
    static PositioningManager positioningManager;
    static MapDataPrefetcher mapDataPrefetcher;
    static NavigationManager navigationManager;
    static MapDataPrefetcher.Adapter prefetcherListener;
    static final ReentrantLock mpLock = new ReentrantLock();

    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.f;
    private SpeedLimitLayout speedLimitViewCollapsed;
    private SpeedLimitLayout speedLimitViewExpanded;
    private SpeedometerView speedometerView;
    private TextView speedLimitTextViewCollapsed;
    private TextView speedLimitTextViewExpanded;
    static PositioningManager.OnPositionChangedListener positionListener;
    View collapsedView;
    View expandedView;

    double startTime, deltaTime;
    private int viewState;
    static ElectronicHorizon electronicHorizon;
    static MediaPlayer mp;

    ViewConfiguration viewConfig;
    int mViewScaledTouchSlop;

    boolean favoriteAdded = false;

    private boolean fetchingDataInProgress = false;
    private Tuple<GeoCoordinate, java.sql.Timestamp> mostRecentFavorite;

    public FloatingViewService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        init();

        if (MainActivity.initSettings()) {
            resize(MainActivity.factor);
        } else {
            resize(2.f);
        }

        //Add the view to the window.
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.RIGHT;        //Initially view will be added to top-left corner
        params.x = MainActivity.posX;
        params.y = MainActivity.posY;

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);

        //The root element of the collapsed view layout
        collapsedView = mFloatingView.findViewById(R.id.collapse_view);
        //The root element of the expanded view layout
        expandedView = mFloatingView.findViewById(R.id.expanded_container);

        // Add label converter
        speedometerView.setLabelConverter(new SpeedometerView.LabelConverter() {
            @Override
            public String getLabelFor(double progress, double maxProgress) {
                return String.valueOf((int) Math.round(progress));
            }
        });

        // configure value range and ticks
        speedometerView.setMaxSpeed(120);
        speedometerView.setMajorTickStep(15);
        speedometerView.setMinorTicks(3);

        // Configure value range colors
        speedometerView.clearColoredRanges();
        speedometerView.addColoredRange(0, 45, Color.GREEN);
        speedometerView.addColoredRange(45, 60, Color.YELLOW);
        speedometerView.addColoredRange(60, 400, Color.RED);

        //Drag and move floating view using user's touch action.
        mResizableLayout = mFloatingView.findViewById(R.id.root_container);
        mResizableLayout.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            private int getDx(MotionEvent event){
                //return (int) (event.getRawX() - initialTouchX);
                return (int) (initialTouchX - event.getRawX());
            }

            private float mPrimStartTouchEventX = -1;
            private float mPrimStartTouchEventY = -1;
            private float mSecStartTouchEventX = -1;
            private float mSecStartTouchEventY = -1;
            private float mPrimSecStartTouchDistance = 0;

            public float distance(MotionEvent event, int first, int second) {
                if (event.getPointerCount() >= 2) {
                    final float x = event.getX(first) - event.getX(second);
                    final float y = event.getY(first) - event.getY(second);

                    return (float) Math.sqrt(x * x + y * y);
                } else {
                    return 0;
                }
            }

            private float getPinchFactor(View v, MotionEvent event){

                final float difInitX = mSecStartTouchEventX - mPrimStartTouchEventX;
                final float difInitY = mSecStartTouchEventY - mPrimStartTouchEventY;
                final double distanceInit = Math.sqrt(Math.pow(difInitX,2)+Math.pow(difInitY,2));
                final float difEndX = event.getX(1) - event.getX(0);
                final float difEndY = event.getY(1) - event.getY(0);
                final double distanceEnd = Math.sqrt(Math.pow(difEndX,2) + Math.pow(difEndY,2));

                int height = v.getHeight();
                int width = v.getWidth();
                double diagonal = Math.sqrt(Math.pow(height,2)+Math.pow(width,2));
                float resizeFactor = (float)((diagonal + (distanceEnd-distanceInit)) / diagonal);
                return resizeFactor;
            }

            private boolean isPinchGesture(MotionEvent event) {
                if (event.getPointerCount() == 2) {
                    final float distanceCurrent = distance(event, 0, 1);
                    final float diffPrimX = mPrimStartTouchEventX - event.getX(0);
                    final float diffPrimY = mPrimStartTouchEventY - event.getY(0);
                    final float diffSecX = mSecStartTouchEventX - event.getX(1);
                    final float diffSecY = mSecStartTouchEventY - event.getY(1);

                    if (// if the distance between the two fingers has increased past
                        // our threshold
                            Math.abs(distanceCurrent - mPrimSecStartTouchDistance) > mViewScaledTouchSlop
                                    // and the fingers are moving in opposing directions
                                    && (diffPrimY * diffSecY) <= 0
                                    && (diffPrimX * diffSecX) <= 0) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startDown(event);
                        return false;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        return false;
                    case MotionEvent.ACTION_UP:
                        startUp(event);
                        return false;
                    case MotionEvent.ACTION_POINTER_UP:
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        startMove(v, event);
                        return false;
                }
                return false;
            }

            static final int MOVE_THRESHOLD = 10;
            boolean resized;
            void startMove(View v, MotionEvent event) {
                if (isPinchGesture(event)) {
                    float resizeFactor = getPinchFactor(v,event);
                    resize(resizeFactor);
                    resized = true;
                    MainActivity.updateSettings(resizeFactor, MainActivity.posX, MainActivity.posY);
                } else {
                    //Calculate the X and Y coordinates of the view.
                    params.x = initialX + getDx(event);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);

                    MainActivity.updateSettings(1, params.x, params.y);

                    //Update the layout with new X & Y coordinate
                    mWindowManager.updateViewLayout(mFloatingView, params);

                }
            }

            void startUp(MotionEvent event) {

                if (event.getPointerCount() < 2) {
                    mSecStartTouchEventX = -1;
                    mSecStartTouchEventY = -1;
                }
                if (event.getPointerCount() < 1) {
                    mPrimStartTouchEventX = -1;
                    mPrimStartTouchEventY = -1;
                }

                // TouchEnd Time
                deltaTime = (System.currentTimeMillis() - startTime);

                int Xdiff = (int) (event.getRawX() - initialTouchX);
                int Ydiff = (int) (event.getRawY() - initialTouchY);

                //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                //So that is click event.
                if (!resized &&
                        Xdiff < MOVE_THRESHOLD && Ydiff < MOVE_THRESHOLD) {

                    if (deltaTime > LONG_PRESS_TIME) {
                        startMainActivity();
                    } else {
                        swapView();
                    }
                }

                resized = false;
            }

            void startDown(MotionEvent event) {
                // Touch start time
                startTime = System.currentTimeMillis();

                //remember the initial position.
                initialX = params.x;
                initialY = params.y;

                //get the touch location
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();

                if (event.getPointerCount() == 1) {
                    mPrimStartTouchEventX = event.getX(0);
                    mPrimStartTouchEventY = event.getY(0);
                    Log.d("TAG", String.format("POINTER ONE X = %.5f, Y = %.5f", mPrimStartTouchEventX, mPrimStartTouchEventY));
                }
                if (event.getPointerCount() == 2) {
                    // Starting distance between fingers
                    mSecStartTouchEventX = event.getX(1);
                    mSecStartTouchEventY = event.getY(1);
                    mPrimSecStartTouchDistance = distance(event, 0, 1);
                    Log.d("TAG", String.format("POINTER TWO X = %.5f, Y = %.5f", mSecStartTouchEventX, mSecStartTouchEventY));
                }
            }
        });

        startLocationUpdates();
    }

    private static Tuple<Double, Long> updateAverage(Tuple<Double, Long> currentAverage, double nextSample) {
        double speedTotal = currentAverage.x * currentAverage.y + nextSample;
        long newNumSamples = currentAverage.y + 1;
        double newAverage = speedTotal / (newNumSamples);
        return new Tuple<>(newAverage, newNumSamples);
    }

    static void stopServices(Context context) {
        stopLocationUpdates(context);
        stopManagersAndListeners();
    }

    static void stopLocationUpdates(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }


        locationManager.removeUpdates(locationListener);
    }

    static void stopManagersAndListeners() {
        try {
            stopPositioningManager();
            stopMapDataPrefetcher();
            stopNavigationManager();
        } catch (Exception e) {

        }
    }

    static void stopPositioningManager() {
        positioningManager.removeListener(positionListener);
        positionListener = null;
        positioningManager.stop();
        positioningManager = null;
    }

    static void stopNavigationManager() {
        navigationManager.getInstance().stop();
        navigationManager = null;
    }

    static void stopMapDataPrefetcher() {
        mapDataPrefetcher.removeListener(prefetcherListener);
        prefetcherListener = null;
        mapDataPrefetcher = null;
    }

    @Override
    public void onDestroy() {
        deInit();
        super.onDestroy();
    }

    private boolean isCloser(Coordinate init, Coordinate c1, Coordinate c2) {
        return isCloser(init.getX(), init.getY(), c1.getX(), c1.getY(), c2.getX(), c2.getY());
    }

    private boolean isCloser(float xInit, float yInit, float x1, float y1, float x2, float y2) {
        float distance1 = (float) Math.sqrt(Math.pow(xInit - x1, 2) + Math.pow(yInit - y1, 2));
        float distance2 = (float) Math.sqrt(Math.pow(xInit - x2, 2) + Math.pow(yInit - y2, 2));
        return distance1 < distance2;
    }

    private void resize(float resizeFactor) {

        final float height = 60.f;
        final float width = 40.f;

        // Resize collapsed Speed Limit
        ViewGroup.LayoutParams speedLimitViewCollapsedLayoutParams = speedLimitViewCollapsed.getLayoutParams();
        if (MainActivity.factor * resizeFactor < ResizableLayout.MIN_FACTOR) {
            speedLimitViewCollapsedLayoutParams.height *= ResizableLayout.MIN_FACTOR;
            speedLimitViewCollapsedLayoutParams.width *= ResizableLayout.MIN_FACTOR;
        } else if (MainActivity.factor * resizeFactor > ResizableLayout.MAX_FACTOR) {
            speedLimitViewCollapsedLayoutParams.height *= ResizableLayout.MAX_FACTOR;
            speedLimitViewCollapsedLayoutParams.width *= ResizableLayout.MAX_FACTOR;
        } else {
            speedLimitViewCollapsedLayoutParams.height *= resizeFactor;
            speedLimitViewCollapsedLayoutParams.width *= resizeFactor;
        }

        speedLimitViewCollapsed.setLayoutParams(speedLimitViewCollapsedLayoutParams);

        // Resize expanded Speed Limit
        ViewGroup.LayoutParams speedLimitViewExpandedLayoutParams = speedLimitViewExpanded.getLayoutParams();
        speedLimitViewExpandedLayoutParams.height *= resizeFactor;
        speedLimitViewExpandedLayoutParams.width *= resizeFactor;
        speedLimitViewExpanded.setLayoutParams(speedLimitViewExpandedLayoutParams);

        // Resize Speedometer
        ViewGroup.LayoutParams speedometerViewLayoutParams = speedometerView.getLayoutParams();
        speedometerViewLayoutParams.height *= resizeFactor;
        speedometerViewLayoutParams.width *= resizeFactor;
        speedometerView.setLayoutParams(speedometerViewLayoutParams);

        // Resize and Move TextView
        float textSizeComplexUnitPx = speedLimitTextViewCollapsed.getTextSize();
        speedLimitTextViewCollapsed.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeComplexUnitPx * resizeFactor);
        speedLimitTextViewExpanded.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeComplexUnitPx * resizeFactor);

    }

    void startMainActivity() {
        //Open the application  click.
        Intent intent = new Intent(FloatingViewService.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MainActivity.START_ACTIVITY_WITH_VIEW, true);
        startActivity(intent);

        //close the service and remove view from the view hierarchy
        stopSelf();
    }

    public void updateFavoriteLocation(Tuple<GeoCoordinate, java.sql.Timestamp> currentLocation) {
        final int CHECK_IN_TIME_IN_SECONDS = 15;
        if (mostRecentFavorite == null) {
            mostRecentFavorite = currentLocation;
        } else if (LocationHelper.distance(mostRecentFavorite.x, currentLocation.x) < 50) {
            long currentLocationTime = currentLocation.y.getTime();
            long mostRecentLocationTime = mostRecentFavorite.y.getTime();
            long diff = currentLocationTime - mostRecentLocationTime;
            if (Math.abs(currentLocation.y.getTime() - mostRecentFavorite.y.getTime()) > 1000 * CHECK_IN_TIME_IN_SECONDS && !favoriteAdded) {
                MainActivity.upateFavorites(getBaseContext(), getString(R.string.custom_favorite_name_default), mostRecentFavorite.x, "");
                favoriteAdded = true;
            }
        } else {
            mostRecentFavorite = currentLocation;
            favoriteAdded = false;
        }
    }

    class Coordinate {
        float x;
        float y;

        Coordinate(float x, float y) {
            this.x = x;
            this.y = y;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }
    }

    private void swapView() {
        viewState = (viewState + 1) % 2;
        switch (viewState) {
            case 1:
                collapsedView.setVisibility(View.GONE);
                expandedView.setVisibility(View.VISIBLE);
                break;
            default:
                collapsedView.setVisibility(View.VISIBLE);
                expandedView.setVisibility(View.GONE);
                break;
        }
    }

    public void getLastLocation(OnSuccessListener<Location> onSuccessListener) {
        // Get last known recent location using new Google Play Services SDK (v11+)
        FusedLocationProviderClient locationClient = getFusedLocationProviderClient(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.getLastLocation()
                    .addOnSuccessListener(onSuccessListener)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d("MapDemoActivity", "Error trying to get last GPS location");
                            e.printStackTrace();
                        }
                    });
        }
    }

    static MatchedGeoPosition lastPosition;

    private void initSDK() {
        final ApplicationContext appContext = new ApplicationContext(getApplicationContext());

        mapEngine = MapEngine.getInstance();
        mapEngine.init(appContext, new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(Error error) {
                if (error == Error.NONE) {

                    startMangersAndListeners();

                } else {
                    //handle error here
                    Toast.makeText(getApplicationContext(), "Unable to initialize SDK", Toast.LENGTH_SHORT);
                }
            }
        });

    }

    private void startMangersAndListeners() {
        startPositioningManager();
        startNavigationManager();
        startMapDataPrefetcher();
        startElectronicHorizon();
    }

    private void startPositioningManager() {
        positioningManager = PositioningManager.getInstance();
        boolean positioningManagerStarted = positioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK);

        if (positioningManagerStarted) {
            positioningManager.addListener(new WeakReference<>(positionListener));
        } else {
            //handle error here
            Toast.makeText(this, "PosisitioningManager not started", Toast.LENGTH_LONG).show();
        }
    }

    private void updateCurrentSpeedLimitView(int currentSpeedLimit) {
        //System.out.println("Speed Limit: " + currentSpeedLimit);
        String currentSpeedLimitText;

        if (currentSpeedLimit > 0) {
            currentSpeedLimitText = String.valueOf(currentSpeedLimit);
            resetColoredRanges(currentSpeedLimit);
        } else {
            currentSpeedLimitText = getResources().getString(R.string.navigation_speed_limit_default);
        }
        //currentSpeedLimitView.setText(currentSpeedLimitText);
        //currentSpeedLimitView.setTextColor(getResources().getColor(textColorId));
        //currentSpeedLimitView.setBackgroundResource(backgroundImageId);

        speedLimitTextViewCollapsed.setText(currentSpeedLimitText);
        speedLimitTextViewExpanded.setText(currentSpeedLimitText);
    }

    private void init() {

        //Inflate the floating view layout we created
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        mFloatingView = layoutInflater.inflate(R.layout.layout_floating_widget, null);
        speedometerView = mFloatingView.findViewById(R.id.speedometer);
        speedLimitViewCollapsed = mFloatingView.findViewById(R.id.collapsed_view_speed_limit);
        speedLimitViewExpanded = mFloatingView.findViewById(R.id.expanded_view_speed_limit);
        speedLimitTextViewExpanded = speedLimitViewExpanded.findViewById(R.id.speed_limit_text);
        speedLimitTextViewCollapsed = speedLimitViewCollapsed.findViewById(R.id.speed_limit_text);

        viewConfig = ViewConfiguration.get(this);
        mViewScaledTouchSlop = viewConfig.getScaledTouchSlop();

        mp = MediaPlayer.create(this, R.raw.crash_x);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                mp.reset();
            }
        });
        viewState = 0;
        mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    private void deInit() {
        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
        if (mp != null) {
            mp.stop();
            mp.release();
        }
    }

    private void startMapDataPrefetcher() {
        mapDataPrefetcher = MapDataPrefetcher.getInstance();
        mapDataPrefetcher.addListener(prefetcherListener);
    }

    // Trigger new location updates at interval
    private void startLocationUpdates() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            initSDK();

            positionListener = new PositioningManager.OnPositionChangedListener() {
                @Override
                public void onPositionUpdated(PositioningManager.LocationMethod locationMethod,
                                              GeoPosition geoPosition, boolean b) {

                    if (positioningManager.getRoadElement() == null && !fetchingDataInProgress) {
                        GeoBoundingBox areaAround = new GeoBoundingBox(geoPosition.getCoordinate(), 500, 500);
                        mapDataPrefetcher.fetchMapData(areaAround);
                        fetchingDataInProgress = true;
                    }

                    if (geoPosition.isValid() && geoPosition instanceof MatchedGeoPosition) {

                        MatchedGeoPosition mgp = (MatchedGeoPosition) geoPosition;
                        getSpeedTrap(mgp);

                        int currentSpeedLimitTransformed = 0;
                        double currentSpeedmps = mgp.getSpeed();
                        int currentSpeed = (int) LocationHelper.metersPerSecToMilesPerHour(currentSpeedmps);

                        if (mgp.getRoadElement() != null) {

                            updateLocation(mgp);

                            double currentSpeedLimit = mgp.getRoadElement().getSpeedLimit();
                            currentSpeedLimitTransformed = (int) LocationHelper.meterPerSecToKmPerHour(currentSpeedLimit);

                            String msg = "New Matched Geo Position Update: " +
                                    mgp.getRoadElement().getRoadName() +
                                    "(" +
                                    LocationHelper.metersPerSecToMilesPerHour(currentSpeedLimit)
                                    +
                                    "mph)";

                            //Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();

                            currentSpeedLimitTransformed = LocationHelper.mapMilesPerHour(
                                    LocationHelper.metersPerSecToMilesPerHour(currentSpeedLimit));
                        } else {
                            String msg = "getRoadElement is null";
                            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                        }

                        //updateCurrentSpeedView(currentSpeed, currentSpeedLimitTransformed);
                        updateCurrentSpeedLimitView(currentSpeedLimitTransformed);

                    } else {
                        //handle error
                        String msg = "Error Geo Position Update";
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();

                        updateCurrentSpeedLimitView(0);
                    }
                }

                @Override
                public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod,
                                                 PositioningManager.LocationStatus locationStatus) {

                }
            };
        }
    }

    private void updateLocation(MatchedGeoPosition geoPosition) {
        Tuple<GeoCoordinate, java.sql.Timestamp> currentLocation = new Tuple(geoPosition.getCoordinate(), new java.sql.Timestamp(System.currentTimeMillis()));
        updateFavoriteLocation(currentLocation);
        GeoCoordinate g = new GeoCoordinate(0, 0);
        g.setLatitude(currentLocation.x.getLatitude());
        g.setLongitude(currentLocation.x.getLongitude());
        lastPostion = new Tuple<>(g, currentLocation.y);
        double speed = geoPosition.getSpeed();
        double speedLimit =
                geoPosition.getRoadElement().getSpeedLimit();
        double delta = LocationHelper.metersPerSecToMilesPerHour(speedLimit - speed);

        if (Math.abs(delta) > 88 &&
                Math.abs(delta) < 200) {
            Toast.makeText(getApplicationContext(), "Where You're Going They Don't Need Roads", Toast.LENGTH_LONG).show();
        }

        if (Math.abs(delta) < 200) {
            if (speed > 0 &&
                    speedLimit > 0) {
                Tuple<Double, Long> newAverage = updateAverage(new Tuple<>(MainActivity.averageDelta, MainActivity.numSamples), delta);
                MainActivity.updateSpeedAverage(newAverage.x, newAverage.y);
            }

            updateCurrentSpeedView((int) LocationHelper.metersPerSecToMilesPerHour(speed), 0);

        } else {
            updateCurrentSpeedView((int) LocationHelper.metersPerSecToMilesPerHour(0), 0);
        }
    }
    private void startNavigationManager() {

        if (navigationManager == null) {

            navigationManager = NavigationManager.getInstance();
            NavigationManager.Error navError = navigationManager.startTracking();

            if (navError == NavigationManager.Error.NONE) {
                Toast.makeText(this, "NavigationManager started", Toast.LENGTH_LONG).show();
            } else {
                //handle error navError.toString());
                Toast.makeText(this, "NavigationManager not started", Toast.LENGTH_LONG).show();
            }

        } else {

        }

    }

    private void updateCurrentSpeedView(int currentSpeed, int currentSpeedLimit) {

        int color;
        if (currentSpeed > currentSpeedLimit && currentSpeedLimit > 0) {
            color = getResources().getColor(R.color.notAllowedSpeedBackground);
        } else {
            color = getResources().getColor(R.color.allowedSpeedBackground);
        }
        //currentSpeedContainerView.setBackgroundColor(color);
        //currentSpeedView.setText(String.valueOf(currentSpeed));
        speedometerView.setSpeed(currentSpeed);
    }

    private void resetColoredRanges(int speedLimit) {
        speedometerView.clearColoredRanges();
        int speedLimitFraction = (int) (5.0 / 6 * speedLimit);
        speedometerView.addColoredRange(0, speedLimitFraction, Color.GREEN);
        speedometerView.addColoredRange(speedLimitFraction, speedLimit, Color.YELLOW);
        speedometerView.addColoredRange(speedLimit, 400, Color.RED);
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));

            mFloatingView.invalidate();
            return true;
        }
    }

    static class Tuple<X, Y> {
        public final X x;
        public final Y y;

        public Tuple(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }

    private void startElectronicHorizon() {
        if (electronicHorizon == null) {
            try {
                electronicHorizon = new ElectronicHorizon();
                electronicHorizon.setLookAheadDistancesInCentimeters(500 * 100);
                electronicHorizon.setTrailingDistanceInCentimeters(0);
                electronicHorizon.setListener(new ElectronicHorizon.Listener() {
                    @Override
                    public void onNewPosition(Position position) {
                        PathTree pathTree = position.getPathTree();
                        if (pathTree == null) {
                            // we seem to be off road
                        } else {
                            LocationHelper.logSpeedLimits(electronicHorizon, pathTree);
                        }
                    }

                    @Override
                    public void onTreeReset() {
                    }

                    @Override
                    public void onLinkAdded(PathTree path, Link link) {
                    }

                    @Override
                    public void onLinkRemoved(PathTree path, Link link) {
                    }

                    @Override
                    public void onPathAdded(PathTree path) {
                    }

                    @Override
                    public void onPathRemoved(PathTree path) {
                    }

                    @Override
                    public void onChildDetached(PathTree parent, PathTree child) {
                    }
                });
            } catch (java.security.AccessControlException e) {
                return;
            }
        }
    }
    static boolean calculating = false;
    ReentrantLock lock = new ReentrantLock();

    private void getSpeedTrap(final MatchedGeoPosition currentPosition) {
        final double PROJECTION_THRESHOLD = 10;
        final double PROJECTION_DISTANCE = 100;

        final RoadElement currentPositionRoadElement = currentPosition.getRoadElement();
        final String currentPositionRoadElementRoadName;

        if (currentPositionRoadElement == null) {

        } else {
            currentPositionRoadElementRoadName = currentPositionRoadElement.getRoadName();

            ArrayList<GeoCoordinate> enRoute = new ArrayList<>();
            int index = -1;
            double greatestDistance = -1;

            if (lastPosition == null) {

            } else {
                List<GeoCoordinate> geometry = currentPositionRoadElement.getGeometry();

                int geometrySize = geometry.size();
                if (geometrySize != 2) {
                    //System.out.println("Weird geometry size: " + geometrySize);
                } else {
                    double d = currentPositionRoadElement.getGeometryLength();
                    //System.out.println("distance: " + d);
                }

                for (int i = 0; i < geometry.size(); i++) {
                    if (LocationHelper.isCloser(geometry.get(i), currentPosition.getCoordinate(), lastPosition.getCoordinate())) {
                        enRoute.add(geometry.get(i));
                        double dist = LocationHelper.distance(geometry.get(i), currentPosition.getCoordinate());
                        if (dist > greatestDistance) {
                            index = enRoute.size() - 1;
                            greatestDistance = dist;
                        }
                    }
                }
            }

            if (!currentPositionRoadElementRoadName.equals("") &&
                    (lastPosition == null ||
                            LocationHelper.distance(lastPosition.getCoordinate(), currentPosition.getCoordinate()) > PROJECTION_THRESHOLD)) {
                lastPosition = currentPosition;
            }

            if (index > -1) {
                double enRouteLatitude = enRoute.get(index).getLatitude();
                double enRouteLongitude = enRoute.get(index).getLongitude();
                double currentLatitude = currentPosition.getCoordinate().getLatitude();
                double currentLongitude = currentPosition.getCoordinate().getLongitude();
                double angle = Math.atan((enRouteLatitude - currentLatitude) / (enRouteLongitude - currentLongitude));

                final GeoCoordinate projectedCoordinate = LocationHelper.getGeoCoordinateFromPositionDistanceBearing(
                        currentPosition.getCoordinate(),
                        PROJECTION_DISTANCE * 1.1,
                        Math.toDegrees(angle));

                final double currentSpeedLimit = LocationHelper.metersPerSecToMilesPerHour(currentPosition.getRoadElement().getSpeedLimit());

                // dist should approximately be equal to PROJECTION_DISTANCE
                final double dist = LocationHelper.distance(currentPosition.getCoordinate(), projectedCoordinate);

                GeocodeRequest2 geocodeRequest2 = new GeocodeRequest2(currentPositionRoadElementRoadName);
                try {
                    geocodeRequest2.setSearchArea(projectedCoordinate, (int) PROJECTION_DISTANCE);
                    if (currentSpeedLimit > SPEED_TRAP_THRESHOLD && lock.tryLock()) {
                        try {
                            if (!calculating) {
                                calculating = true;

                                geocodeRequest2.execute(new ResultListener<List<GeocodeResult>>() {
                                    @Override
                                    public void onCompleted(List<GeocodeResult> geocodeResults, ErrorCode errorCode) {
                                        final Router.Listener<List<RouteResult>, RoutingError> routerListener = new Router.Listener<List<RouteResult>, RoutingError>() {
                                            @Override
                                            public void onProgress(int i) {
                                                System.out.println("Progress: " + i);
                                            }

                                            @Override
                                            public void onCalculateRouteFinished(List<RouteResult> routeResults, RoutingError routingError) {
                                                calculating = false;

                                                //get first route
                                                Route route = routeResults.get(0).getRoute();

                                                List<Maneuver> maneuvers = route.getManeuvers();

                                                List<RouteElement> routeElements = route.getRouteElementsFromLength(100).getElements();

                                                if (routeElements.size() > 0) {
                                                    List<RoadElement> roadElements = maneuvers.get(0).getRoadElements();
                                                    //List<RouteElement> routeElements = maneuvers.get(0).getRouteElements();
                                                    if (routeElements.size() > 1) {

                                                        float closestSpeedLimit = routeElements.get(0).getRoadElement().getSpeedLimit();

                                                        for (int i = 1; i < routeElements.size(); i++) {

                                                            double dist = routeElements.get(i).getGeometry().get(0).distanceTo(currentPosition.getCoordinate());
                                                            System.out.println("Route Distance: " + String.format("%.2f", dist));

                                                            RouteElement routeElement = routeElements.get(i);
                                                            RoadElement roadElement = routeElement.getRoadElement();
                                                            String roadName = roadElement.getRoadName();

                                                            double nextSpeedLimit = roadElement.getSpeedLimit();
                                                            double delta = closestSpeedLimit - nextSpeedLimit;
                                                            delta = LocationHelper.metersPerSecToMilesPerHour(delta);
                                                            String deltaMsg = String.format("Speed Limit1: %.2f (%.2f)",
                                                                    LocationHelper.metersPerSecToMilesPerHour(nextSpeedLimit),
                                                                    LocationHelper.metersPerSecToMilesPerHour(closestSpeedLimit));

                                                            boolean deltaThresholdMet = delta > SPEED_TRAP_THRESHOLD;

                                                            String currMsg = String.format("Speed Limit2: %.2f (%.2f)",
                                                                    LocationHelper.metersPerSecToMilesPerHour(nextSpeedLimit),
                                                                    currentSpeedLimit);
                                                            boolean currSpeedThresholdMet = currentSpeedLimit -
                                                                    LocationHelper.metersPerSecToMilesPerHour(nextSpeedLimit) > SPEED_TRAP_THRESHOLD;

                                                            if (!roadName.equals(currentPositionRoadElementRoadName)) {
                                                                System.out.println(roadName + " != " + currentPositionRoadElementRoadName);
                                                            }

                                                            if (nextSpeedLimit > 0 &&
                                                                    (deltaThresholdMet ||
                                                                            currSpeedThresholdMet)) {

                                                                if (deltaThresholdMet) {
                                                                    System.out.println("Road Name: " + roadName + ", " + String.format("%s", deltaMsg));
                                                                    Toast.makeText(getApplicationContext(), deltaMsg, Toast.LENGTH_LONG).show();
                                                                } else {
                                                                    System.out.println("Road Name: " + roadName + ", " + String.format("%s", currMsg));
                                                                    List<GeoCoordinate> geometry = currentPositionRoadElement.getGeometry();
                                                                    Toast.makeText(getApplicationContext(), currMsg, Toast.LENGTH_LONG).show();
                                                                }

                                                                if (mpLock.tryLock()) {
                                                                    try {
                                                                        if (!mp.isPlaying()) {
                                                                            mp.start();
                                                                        }
                                                                    } catch (java.lang.IllegalStateException e) {
                                                                        Toast.makeText(getApplicationContext(),"couldn't play wav file",Toast.LENGTH_SHORT).show();;
                                                                    } finally {
                                                                        mpLock.unlock();
                                                                    }
                                                                }
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                        };
                                        if (geocodeResults.size() > 0) {
                                            System.out.println("Projected Distance: " + String.format("%.2f", dist));
                                            GeoCoordinate farthest = LocationHelper.getFarthest(currentPosition.getCoordinate(), geocodeResults);
                                            LocationHelper.calculateRoute(currentPosition.getCoordinate(), farthest, routerListener);
                                        } else {
                                            calculating = false;
                                        }
                                        return;
                                    }
                                });
                            }

                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (IllegalArgumentException e) {

                }
            }

        }
    }
}
