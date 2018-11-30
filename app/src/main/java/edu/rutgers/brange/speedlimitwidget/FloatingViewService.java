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
import java.util.Calendar;
import java.util.LinkedList;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class FloatingViewService extends Service {

    //private static final int SAMPLE_RATE = 50;//60mph in 3 seconds
    private static final int SAMPLE_RATE = 100;//60mph in 6 seconds
    private static LocationRequest mLocationRequest;
    LocationManager locationManager;
    LocationListener locationListener;

    private WindowManager mWindowManager;
    private View mFloatingView;
    private ResizableLayout mResizableLayout;

    static Path drivePath;
    MapEngine mapEngine;
    PositioningManager positioningManager;
    MapDataPrefetcher mapDataPrefetcher;
    NavigationManager navigationManager;
    MapDataPrefetcher.Adapter prefetcherListener;

    Calendar calendar = Calendar.getInstance();
    private long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */
    PositioningManager.OnPositionChangedListener positionListener;

    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.f;
    private SpeedLimitLayout speedLimitViewCollapsed;
    private SpeedLimitLayout speedLimitViewExpanded;
    private SpeedometerView speedometerView;
    private TextView speedLimitTextViewCollapsed;
    private TextView speedLimitTextViewExpanded;
    private ImageView closeButtonCollapsed;

    public FloatingViewService() {
    }
    private boolean fetchingDataInProgress = false;

    private static double getSpeed(Location location0, java.sql.Timestamp timestamp0, Location location1, java.sql.Timestamp timestamp1) {
        double seconds = (timestamp1.getTime() - timestamp0.getTime()) / 1000;
        double distance = Math.sqrt(Math.pow(location0.getLatitude() - location1.getLatitude(), 2) + Math.pow(location0.getLongitude() - location1.getLongitude(), 2));
        return distance / seconds;
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
        ImageView closeButtonExpanded = speedLimitViewExpanded.findViewById(R.id.close_btn);
        closeButtonExpanded.setVisibility(View.GONE);
        speedLimitTextViewExpanded = speedLimitViewExpanded.findViewById(R.id.speed_limit_text);
        speedLimitTextViewCollapsed = speedLimitViewCollapsed.findViewById(R.id.speed_limit_text);
        closeButtonCollapsed = speedLimitViewCollapsed.findViewById(R.id.close_btn);

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
        final View collapsedView = mFloatingView.findViewById(R.id.collapse_view);
        //The root element of the expanded view layout
        final View expandedView = mFloatingView.findViewById(R.id.expanded_container);


        //Set the close button
        ImageView closeButtonCollapsed = speedLimitViewCollapsed.findViewById(R.id.close_btn);
        closeButtonCollapsed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //close the service and remove the from from the window
                stopSelf();
            }
        });

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

        //Set the close button
        ImageView closeButton = (ImageView) mFloatingView.findViewById(R.id.close_btn_expanded);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                collapsedView.setVisibility(View.VISIBLE);
                expandedView.setVisibility(View.GONE);
            }
        });

        //Open the application on thi button click
        ImageView openButton = (ImageView) mFloatingView.findViewById(R.id.open_button);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Open the application  click.
                Intent intent = new Intent(FloatingViewService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(MainActivity.START_ACTIVITY_WITH_VIEW, true);
                startActivity(intent);

                //close the service and remove view from the view hierarchy
                stopSelf();
            }
        });

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

                        //remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        return false;
                    case MotionEvent.ACTION_UP:
                        int Xdiff = (int) (event.getRawX() - initialTouchX);
                        int Ydiff = (int) (event.getRawY() - initialTouchY);

                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        //So that is click event.
                        if (Xdiff < 10 && Ydiff < 10) {
                            if (isViewCollapsed()) {
                                //When user clicks on the image view of the collapsed layout,
                                //visibility of the collapsed layout will be changed to "View.GONE"
                                //and expanded view will become visible.
                                collapsedView.setVisibility(View.GONE);
                                expandedView.setVisibility(View.VISIBLE);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        float rawX = event.getRawX();
                        float dragDiff = rawX - initialTouchX;
                        int width = v.getWidth();
                        float resizeFactor = (width + dragDiff) / width;
                        resize(resizeFactor);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + getDx(event);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });

        drivePath = new Path(10);
        startLocationUpdates();
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
        stopLocationUpdates();
        stopManagersAndListeners();
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

            ConstraintLayout.LayoutParams closeButtonCollapsedLayoutParams = (ConstraintLayout.LayoutParams) closeButtonCollapsed.getLayoutParams();
            closeButtonCollapsedLayoutParams.setMargins(
                    (int) (closeButtonCollapsedLayoutParams.leftMargin * resizeFactor),
                    closeButtonCollapsedLayoutParams.topMargin,
                    closeButtonCollapsedLayoutParams.rightMargin,
                    closeButtonCollapsedLayoutParams.bottomMargin);
            closeButtonCollapsed.setLayoutParams(closeButtonCollapsedLayoutParams);

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

    /**
     * Detect if the floating view is collapsed or expanded.
     *
     * @return true if the floating view is collapsed.
     */
    private boolean isViewCollapsed() {
        return mFloatingView == null || mFloatingView.findViewById(R.id.collapse_view).getVisibility() == View.VISIBLE;
    }

    private void onLocationChanged(@NonNull Location location) {

        String msg = "New Location Update: " + location.getLatitude() + "," + location.getLongitude();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();


        Tuple<Location, java.sql.Timestamp> currentLocation = new Tuple(location, new java.sql.Timestamp(System.currentTimeMillis()));
        updateFavoriteLocation(currentLocation);
        drivePath.push(currentLocation);
        updateCurrentSpeedView((int) LocationHelper.meterPerSecToMilesPerHour(location.getSpeed()), 0);
    }

    private void stopLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

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

    boolean favoriteAdded = false;
    private Tuple<Location, java.sql.Timestamp> mostRecentFavorite;

    // Trigger new location updates at interval
    private void startLocationUpdates() {

        // Create the location request to start receiving updates
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

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

                        int currentSpeedLimitTransformed = 0;
                        double currentSpeedmps = mgp.getSpeed();
                        int currentSpeed = (int) LocationHelper.meterPerSecToKmPerHour(currentSpeedmps);
                        currentSpeed = (int) LocationHelper.meterPerSecToMilesPerHour(currentSpeedmps);

                        if (mgp.getRoadElement() != null) {
                            double currentSpeedLimit = mgp.getRoadElement().getSpeedLimit();
                            currentSpeedLimitTransformed = (int) LocationHelper.meterPerSecToKmPerHour(currentSpeedLimit);
                            currentSpeedLimitTransformed = LocationHelper.mapMilesPerHour(
                                    LocationHelper.meterPerSecToMilesPerHour(currentSpeedLimit));
                        }

                        //updateCurrentSpeedView(currentSpeed, currentSpeedLimitTransformed);
                        updateCurrentSpeedLimitView(currentSpeedLimitTransformed);

                    } else {
                        //handle error
                    }
                }

                @Override
                public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod,
                                                 PositioningManager.LocationStatus locationStatus) {

                }
            };

            FusedLocationProviderClient fusedLocationProviderClient = getFusedLocationProviderClient(this);
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            String bestProvider = locationManager.getBestProvider(criteria, true);
            Location location = locationManager.getLastKnownLocation(bestProvider);
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    FloatingViewService.this.onLocationChanged(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }

                @Override
                public void onProviderEnabled(String provider) {

                }

                @Override
                public void onProviderDisabled(String provider) {

                }
            };
            locationManager.requestLocationUpdates(bestProvider, FASTEST_INTERVAL, 0, locationListener, Looper.myLooper());
        }
    }

    public void updateFavoriteLocation(Tuple<Location, java.sql.Timestamp> currentLocation) {
        final int TIME_IN_SECONDS = 15;
        if (mostRecentFavorite == null) {
            mostRecentFavorite = currentLocation;
        } else if (LocationHelper.distance(mostRecentFavorite.x, currentLocation.x) < 50) {
            long currentLocationTime = currentLocation.y.getTime();
            long mostRecentLocationTime = mostRecentFavorite.y.getTime();
            long diff = currentLocationTime - mostRecentLocationTime;
            if (Math.abs(currentLocation.y.getTime() - mostRecentFavorite.y.getTime()) > 1000 * TIME_IN_SECONDS && !favoriteAdded) {
                MainActivity.upateFavorites("Custom Name", mostRecentFavorite.x, "");
                favoriteAdded = true;
            }
        } else {
            mostRecentFavorite = currentLocation;
            favoriteAdded = false;
        }
    }

    private void startNavigationManager() {
        navigationManager = NavigationManager.getInstance();
        NavigationManager.Error navError = navigationManager.startTracking();

        if (navError == NavigationManager.Error.NONE) {
            Toast.makeText(this, "NavigationManager started", Toast.LENGTH_LONG).show();
        } else {
            //handle error navError.toString());
            Toast.makeText(this, "NavigationManager not started", Toast.LENGTH_LONG).show();
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

    public void getLastLocation() {
        OnSuccessListener<Location> onSuccessListener = new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // GPS location can be null if GPS is switched off
                if (location != null) {
                    onLocationChanged(location);
                }
            }
        };
        getLastLocation(onSuccessListener);
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

    private void stopManagersAndListeners() {
        try {
            stopMapDataPrefetcher();
            stopNavigationManager();
            stopPositioningManager();
        } catch (Exception e) {

        }
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

    public void stopPositioningManager() {
        positioningManager.removeListener(positionListener);
        positioningManager.stop();
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

    private void stopNavigationManager() {
        NavigationManager.getInstance().stop();
    }

    private void startMapDataPrefetcher() {
        mapDataPrefetcher = MapDataPrefetcher.getInstance();
        mapDataPrefetcher.addListener(prefetcherListener);
    }

    private void stopMapDataPrefetcher() {
        mapDataPrefetcher.removeListener(prefetcherListener);
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

    class Tuple<X, Y> {
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
