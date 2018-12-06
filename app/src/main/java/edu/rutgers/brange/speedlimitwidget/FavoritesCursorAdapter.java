package edu.rutgers.brange.speedlimitwidget;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.Maneuver;
import com.here.android.mpa.routing.Route;
import com.here.android.mpa.routing.RouteElement;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteTta;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;

import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import static android.provider.BaseColumns._ID;

public class FavoritesCursorAdapter extends CursorAdapter {
    String id;
    ReentrantLock lock = new ReentrantLock();
    boolean routeCalculated;
    boolean addressTextSet;

    static final int SPEED_TRAP_THRESHOLD = 20;

    public FavoritesCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
    }

    // The newView method is used to inflate a new view and return it,
    // you don't bind any data to the view at this point.
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.favorite_place_item, parent, false);
    }

    // The bindView method is used to bind all data to a given view
    // such as setting the text on a TextView.
    @Override
    public void bindView(View view, final Context context, Cursor cursor) {
        id = cursor.getString(cursor.getColumnIndexOrThrow(_ID));

        final Context mContext = context;
        final EditText addressView = view.findViewById(R.id.address);
        final TextView minutesLeftView = view.findViewById(R.id.minutes_to_go);

        //int id = Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow(_ID)));
        final String customNameString = cursor.getString(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_NAME));
        String addressString = cursor.getString(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_ADDRESS));
        double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE));
        double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE));

        Location location = new Location("");
        location.reset();
        location.setLatitude(latitude);
        location.setLongitude(longitude);

        final Router.Listener<List<RouteResult>, RoutingError> listener = new Router.Listener<List<RouteResult>, RoutingError>() {
            @Override
            public void onProgress(int i) {
                /* The calculation progress can be retrieved in this callback. */
                if (i == 100) {

                } else {
                    if (lock.tryLock()) {
                        try {
                            minutesLeftView.setText(String.format("%3d%% Complete", i));
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            }

            @Override
            public void onCalculateRouteFinished(List<RouteResult> routeResults,
                                                 RoutingError routingError) {
                routeCalculated = true;

                /* Calculation is done. Let's handle the result */
                if (routingError == RoutingError.NONE) {
                    if (routeResults.get(0).getRoute() != null) {
                        /* Create a MapRoute so that it can be placed on the map */
                        Route route = routeResults.get(0).getRoute();

                        if (false) {
                            List<Maneuver> maneuvers = route.getManeuvers();
                            if (maneuvers.size() > 0) {
                                List<RouteElement> routeElements = maneuvers.get(0).getRouteElements();
                                if (routeElements.size() > 1) {
                                    float lastSpeedLimit = routeElements.get(0).getRoadElement().getSpeedLimit();
                                    for (int i = 1; i < routeElements.size(); i++) {
                                        double speedLimit = routeElements.get(i).getRoadElement().getSpeedLimit();
                                        double delta = speedLimit - lastSpeedLimit;
                                        delta = LocationHelper.meterPerSecToMilesPerHour(delta);
                                        if (delta > SPEED_TRAP_THRESHOLD) {

                                        }
                                    }
                                }
                            }
                        }

                        MapRoute m_mapRoute = new MapRoute(route);

                        RouteTta routeTta = route.getTtaExcludingTraffic(0);

                        int timeInSeconds = routeTta.getDuration();//seconds
                        int distanceInMeters = route.getLength();//meters

                        String change;
                        if (distanceInMeters == 0) {
                            change = "0 minutes at 5mph over";
                        } else {
                            double deltaS = LocationHelper.milesPerHourToMetersPerSecond(5);
                            double newTime = (timeInSeconds - distanceInMeters / (distanceInMeters - deltaS * timeInSeconds));
                            newTime = timeInSeconds * (1 - distanceInMeters / (distanceInMeters + timeInSeconds * deltaS));

                            TimeZone tz = TimeZone.getTimeZone("UTC");
                            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
                            df.setTimeZone(tz);
                            String time = df.format(new Date((int) (newTime * 1000)));

                            change = "-" + time + " at 5mph over";
                        }

                        TimeZone tz = TimeZone.getTimeZone("UTC");
                        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
                        df.setTimeZone(tz);
                        String time = df.format(new Date(timeInSeconds * 1000));

                        change = time + ", (" + change + ")";
                        minutesLeftView.setText(change);
                    } else {
                        // TODO Handle Error
                    }
                } else {
                    // TODO Handle Error
                    minutesLeftView.setText(mContext.getString(R.string.error_no_route));
                }
            }
        };


        if (FloatingViewService.lastPostion != null &&
                !routeCalculated) {
            GeoCoordinate g = new GeoCoordinate(0, 0);
            g.setLatitude(latitude);
            g.setLongitude(longitude);
            LocationHelper.calculateRoute(FloatingViewService.lastPostion.x, g, listener);
        }

        if (!customNameString.equals("") && !addressTextSet) {
            addressView.setText(customNameString);
        } else {
        }
        addressTextSet = true;
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    latitude,
                    longitude,
                    // In this sample, get just a single address.
                    1);
            if (addresses.size() > 0) {
                Address address = addresses.get(0);
                addressView.setHint(address.getAddressLine(0));
            }
        } catch (IOException ioException) {
        } catch (IllegalArgumentException illegalArgumentException) {
        }

        if (false) {
            addressView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        updateCustomName(context, (EditText) v);
                    }
                }
            });
        } else {
            addressView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateCustomName(context, addressView);
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });
        }

    }

    void updateCustomName(Context context, EditText v) {
        lock.lock();
        try {
            ContentValues cv = new ContentValues();
            String customName = v.getText().toString();
            cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_NAME, customName);
            long i = MainActivity.db.update(FavoritePlacesContract.ContractEntry.TABLE_NAME, cv, _ID + "=" + id, null);
            MainActivity.initDbs(context);
            ((BaseAdapter) MainActivity.favorites.getAdapter()).notifyDataSetChanged();
        } finally {
            lock.unlock();
        }
    }
}
