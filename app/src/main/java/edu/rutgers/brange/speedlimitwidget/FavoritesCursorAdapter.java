package edu.rutgers.brange.speedlimitwidget;

import android.content.Context;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.Route;
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

import static android.provider.BaseColumns._ID;

public class FavoritesCursorAdapter extends CursorAdapter {
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
    public void bindView(View view, Context context, Cursor cursor) {
        EditText customNameView = view.findViewById(R.id.custom_name);
        EditText addressView = view.findViewById(R.id.address);
        final TextView minutesLeftView = view.findViewById(R.id.minutes_to_go);

        //int id = Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow(_ID)));
        String customNameString = cursor.getString(cursor.getColumnIndexOrThrow(FavoritePlacesContract.ContractEntry.COLUMN_NAME_NAME));
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
            }

            @Override
            public void onCalculateRouteFinished(List<RouteResult> routeResults,
                                                 RoutingError routingError) {
                // TODO Get route time
                /* Calculation is done. Let's handle the result */
                if (routingError == RoutingError.NONE) {
                    if (routeResults.get(0).getRoute() != null) {
                        /* Create a MapRoute so that it can be placed on the map */
                        Route route = routeResults.get(0).getRoute();
                        MapRoute m_mapRoute = new MapRoute(route);
                        /*
                         * We may also want to make sure the map view is orientated properly
                         * so the entire route can be easily seen.
                         */
                        GeoBoundingBox gbb = route
                                .getBoundingBox();
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
                }
            }
        };

        if (FloatingViewService.drivePath != null && FloatingViewService.drivePath.size() > 0) {
            int size = FloatingViewService.drivePath.size();
            LocationHelper.calculateRoute(FloatingViewService.drivePath.get(size - 1).x, location, listener);
        }


        customNameView.setText(customNameString);

        if (addressString.equals("")) {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        latitude,
                        longitude,
                        // In this sample, get just a single address.
                        1);
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    addressView.setText(address.getAddressLine(0));
                }
            } catch (IOException ioException) {
            } catch (IllegalArgumentException illegalArgumentException) {
            }

        } else {
            addressView.setText(addressString);
        }

    }
}
