package edu.rutgers.brange.speedlimitwidget;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
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
import com.here.android.mpa.guidance.NavigationManager;
import com.here.android.mpa.prefetcher.MapDataPrefetcher;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.LinkedList;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

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

    public FloatingViewService() {
    }

    private boolean fetchingDataInProgress = false;
    private Tuple<GeoCoordinate, java.sql.Timestamp> mostRecentFavorite;

    static void stopServices(Context context) {
        stopLocationUpdates(context);
        stopManagersAndListeners();
    }

    private boolean isCloser(Coordinate init, Coordinate c1, Coordinate c2) {
        return isCloser(init.getX(), init.getY(), c1.getX(), c1.getY(), c2.getX(), c2.getY());
    }

    private boolean isCloser(float xInit, float yInit, float x1, float y1, float x2, float y2) {
        float distance1 = (float) Math.sqrt(Math.pow(xInit - x1, 2) + Math.pow(yInit - y1, 2));
        float distance2 = (float) Math.sqrt(Math.pow(xInit - x2, 2) + Math.pow(yInit - y2, 2));
        return distance1 < distance2;
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

    private void resize(float resizeFactor) {

        final float ratio = 60.f / 40;

        if (true) {
            // Resize collapsed Speed Limit
            ViewGroup.LayoutParams speedLimitViewCollapsedLayoutParams = speedLimitViewCollapsed.getLayoutParams();
            speedLimitViewCollapsedLayoutParams.height *= resizeFactor;
            speedLimitViewCollapsedLayoutParams.width *= resizeFactor;
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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static Tuple<Double, Long> updateAverage(Tuple<Double, Long> currentAverage, double nextSample) {
        double speedTotal = currentAverage.x * currentAverage.y + nextSample;
        long newNumSamples = currentAverage.y + 1;
        double newAverage = speedTotal / (newNumSamples);
        return new Tuple<>(newAverage, newNumSamples);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Inflate the floating view layout we created
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        mFloatingView = layoutInflater.inflate(R.layout.layout_floating_widget, null);
        speedometerView = mFloatingView.findViewById(R.id.speedometer);
        speedLimitViewCollapsed = mFloatingView.findViewById(R.id.collapsed_view_speed_limit);
        speedLimitViewExpanded = mFloatingView.findViewById(R.id.expanded_view_speed_limit);
        speedLimitTextViewExpanded = speedLimitViewExpanded.findViewById(R.id.speed_limit_text);
        speedLimitTextViewCollapsed = speedLimitViewCollapsed.findViewById(R.id.speed_limit_text);

        init(this);
        resize(2.f);

        //Add the view to the window.
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.RIGHT;        //Initially view will be added to top-left corner
        params.x = 225;
        params.y = 175;

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

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        // Touch start time
                        startTime = System.currentTimeMillis();

                        //remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return false;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        return false;
                    case MotionEvent.ACTION_UP:

                        // TouchEnd Time
                        deltaTime = (System.currentTimeMillis() - startTime);

                        int Xdiff = (int) (event.getRawX() - initialTouchX);
                        int Ydiff = (int) (event.getRawY() - initialTouchY);

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        //So that is click event.
                        if (Xdiff < 10 && Ydiff < 10) {
                            if (deltaTime > LONG_PRESS_TIME) {
                                startMainActivity();
                            } else {
                                swapView();
                            }
                        }
                        return false;
                    case MotionEvent.ACTION_POINTER_UP:
                        Coordinate c1 = new Coordinate(initialX, initialY);
                        Coordinate c2 = new Coordinate(event.getRawX(), event.getRawY());
                        Coordinate init;
                        int pointerCount = event.getPointerCount();
                        if (pointerCount > 1) {
                            float posX = event.getX(1);
                            float posY = event.getY(1);
                            init = new Coordinate(posX, posY);
                        } else {
                            return false;
                        }
                        float rawX = event.getRawX();
                        float dragDiff = Math.abs(rawX - initialTouchX);
                        if (isCloser(init, c1, c2)) {

                        } else {
                            dragDiff *= -1;
                        }
                        int width = v.getWidth();
                        float resizeFactor = (width + dragDiff) / width;
                        resize(resizeFactor);
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + getDx(event);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return false;
                }
                return false;
            }

        });

        startLocationUpdates();
    }

    boolean favoriteAdded = false;

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
                Toast.makeText(this, "Checkin Triggered", Toast.LENGTH_LONG).show();
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
    }

    private void init(Context context) {
        viewState = 0;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
        //getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateCurrentSpeedLimitView(int currentSpeedLimit) {

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

    private void updateLocation(GeoPosition geoPosition) {
        Tuple<GeoCoordinate, java.sql.Timestamp> currentLocation = new Tuple(geoPosition.getCoordinate(), new java.sql.Timestamp(System.currentTimeMillis()));
        updateFavoriteLocation(currentLocation);
        GeoCoordinate g = new GeoCoordinate(0, 0);
        g.setLatitude(currentLocation.x.getLatitude());
        g.setLongitude(currentLocation.x.getLongitude());
        lastPostion = new Tuple<>(g, currentLocation.y);
        double speed = geoPosition.getSpeed();
        if (speed > 0) {
            Tuple<Double, Long> newAverage = updateAverage(new Tuple<>(MainActivity.averageDelta, MainActivity.numSamples), speed);
            MainActivity.averageDelta = newAverage.x;
            MainActivity.numSamples = newAverage.y;
            MainActivity.updateSpeedAverage();
        }
        updateCurrentSpeedView((int) LocationHelper.meterPerSecToMilesPerHour(speed), 0);
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

                    /*
                    String msg = "New Position Update: " +
                            geoPosition.getCoordinate().getLatitude() +
                            "," +
                            geoPosition.getCoordinate().getLongitude();
                    Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                    */
                    updateLocation(geoPosition);

                    if (positioningManager.getRoadElement() == null && !fetchingDataInProgress) {
                        GeoBoundingBox areaAround = new GeoBoundingBox(geoPosition.getCoordinate(), 500, 500);
                        mapDataPrefetcher.fetchMapData(areaAround);
                        fetchingDataInProgress = true;
                    }

                    if (geoPosition.isValid() && geoPosition instanceof MatchedGeoPosition) {

                        MatchedGeoPosition mgp = (MatchedGeoPosition) geoPosition;

                        int currentSpeedLimitTransformed = 0;
                        double currentSpeedmps = mgp.getSpeed();
                        int currentSpeed = (int) LocationHelper.meterPerSecToMilesPerHour(currentSpeedmps);

                        if (mgp.getRoadElement() != null) {
                            double currentSpeedLimit = mgp.getRoadElement().getSpeedLimit();
                            currentSpeedLimitTransformed = (int) LocationHelper.meterPerSecToKmPerHour(currentSpeedLimit);

                            String msg = "New Matched Geo Position Update: " +
                                    mgp.getRoadElement().getRoadName() +
                                    "(" +
                                    LocationHelper.meterPerSecToMilesPerHour(currentSpeedLimit)
                                    +
                                    "mph)";

                            //Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();

                            currentSpeedLimitTransformed = LocationHelper.mapMilesPerHour(
                                    LocationHelper.meterPerSecToMilesPerHour(currentSpeedLimit));
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

    class Path extends LinkedList<Tuple<Location, java.sql.Timestamp>> {
        private static final int MAX_LOCATIONS = 5;
        private int maxLocations;

        public Path() {
            super();
            maxLocations = MAX_LOCATIONS;
        }

        public Path(String apiKey) {
            this();
        }

        public Path(int maxLocations) {
            this();
            this.maxLocations = maxLocations;
        }

        public Path(String apiKey, int maxLocations) {
            this(apiKey);
            this.maxLocations = maxLocations;
        }

        public int getMaxLocations() {
            return maxLocations;
        }

        @Override
        public void push(Tuple<Location, java.sql.Timestamp> location) {
            if (!this.isEmpty() &&
                    Math.abs(this.get(this.size() - 1).y.getTime() - location.y.getTime()) < SAMPLE_RATE / this.getMaxLocations()) {
                return;
            }
            if (this.size() == maxLocations) {
                pop();
            } else {
                super.push(location);
            }
        }

        public String getPath() {
            String path = "";
            if (this.size() > 0) {
                path = this.get(0).x.getLatitude() + "," + this.get(0).x.getLongitude();
            }
            for (int i = 1; i < this.size(); i++) {
                path += "|" + this.get(i).x.getLatitude() + "," + this.get(i).x.getLongitude();
            }
            return path;
        }

        public String getRequestUrl2() {
            String url = "http://www.overpass-api.de/api/xapi?*[maxspeed=*][bbox=";
            for (int i = this.size() - 2; i < this.size(); i++) {
                url += this.get(i).x.getLatitude() + "," + this.get(i).x.getLongitude();
                if (i < this.size() - 1) {
                    url += ",";
                }
            }
            url += "]";
            return url;
        }

        private String readStream(InputStream is) {
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int i = is.read();
                while (i != -1) {
                    bo.write(i);
                    i = is.read();
                }
                return bo.toString();
            } catch (IOException e) {
                return "";
            }
        }

        public void query() {
            String urlString = getRequestUrl2();
            String response = "";
            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    response = readStream(in);
                } finally {
                    urlConnection.disconnect();
                }
            } finally {
                if (response.equals("")) {
                    return;
                } else {
                    return;
                }
            }
        }

    }

}
