package edu.rutgers.brange.speedlimitwidget;

import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.electronic_horizon.DataNotReadyException;
import com.here.android.mpa.electronic_horizon.ElectronicHorizon;
import com.here.android.mpa.electronic_horizon.Link;
import com.here.android.mpa.electronic_horizon.LinkInformation;
import com.here.android.mpa.electronic_horizon.MapAccessor;
import com.here.android.mpa.electronic_horizon.PathTree;
import com.here.android.mpa.electronic_horizon.Position;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;

import java.util.List;

class LocationHelper {

    private LocationHelper() {
    }

    static double distance(Location x, Location y) {
        return distance(x.getLatitude(), x.getLongitude(), y.getLatitude(), y.getLongitude());
    }

    static double distance(GeoCoordinate x, GeoCoordinate y) {
        return distance(x.getLatitude(), x.getLongitude(), y.getLatitude(), y.getLongitude());
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = R * c; // Distance in km
        d = d * 1000;
        return d;
    }

    static GeoCoordinate getGeoCoordinateFromPositionDistanceBearing(GeoCoordinate init, double distanceInMeters, double bearingInDegrees) {
        double R = 6378.1;  //Radius of the Earth
        double brng = Math.toRadians(bearingInDegrees);  //Bearing is 90 degrees converted to radians.
        double d = distanceInMeters / 1000;  //Distance in km

        double lat2 = 52.20444; // - the lat result I'm hoping for
        double lon2 = 0.36056; // - the long result I'm hoping for.

        double lat1 = Math.toRadians(init.getLatitude()); //Current lat point converted to radians
        double lon1 = Math.toRadians(init.getLongitude()); //Current long point converted to radians

        lat2 = Math.asin(Math.sin(lat1) * Math.cos(d / R) +
                Math.cos(lat1) * Math.sin(d / R) * Math.cos(brng));

        lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(d / R) * Math.cos(lat1),
                Math.cos(d / R) - Math.sin(lat1) * Math.sin(lat2));

        lat2 = Math.toDegrees(lat2);
        lon2 = Math.toDegrees(lon2);

        System.out.println(lat2 + ", " + lon2);

        return new GeoCoordinate(lat2, lon2);
    }

    static double metersToMiles(double meters) {
        return meters * 0.62137119 / 1000;
    }

    static double milesToMeters(double miles) {
        return miles / 1609.344;
    }

    static double milesPerHourToMetersPerSecond(double speed) {
        return milesToMeters(speed) * 3600;
    }

    static double meterPerSecToKmPerHour(double speed) {
        return (speed * 3.6);
    }

    static double meterPerSecToMilesPerHour(double speed) {
        return (speed * 2.236942);
    }

    static int mapMilesPerHour(double speed) {
        for (int i = 0; i < 120; i += 5) {
            if (Math.abs(speed - i) < 5) {
                if (Math.abs(speed - (i + 5)) < Math.abs(speed - i)) {
                    return i + 5;
                } else {
                    return i;
                }
            }
        }
        return (int) Math.ceil(speed);
    }

    static void calculateRoute(Location start,
                               Location end,
                               Router.Listener<List<RouteResult>, RoutingError> routerListener) {
        /* Initialize a CoreRouter */
        CoreRouter coreRouter = new CoreRouter();

        /* Initialize a RoutePlan */
        RoutePlan routePlan = new RoutePlan();

        /*
         * Initialize a RouteOption.HERE SDK allow users to define their own parameters for the
         * route calculation,including transport modes,route types and route restrictions etc.Please
         * refer to API doc for full list of APIs
         */
        RouteOptions routeOptions = new RouteOptions();
        /* Other transport modes are also available e.g Pedestrian */
        routeOptions.setTransportMode(RouteOptions.TransportMode.CAR);
        /* Disable highway in this route. */
        routeOptions.setHighwaysAllowed(false);
        /* Calculate the shortest route available. */
        routeOptions.setRouteType(RouteOptions.Type.SHORTEST);
        /* Calculate 1 route. */
        routeOptions.setRouteCount(1);
        /* Finally set the route option */
        routePlan.setRouteOptions(routeOptions);

        /* Define waypoints for the route */
        /* START: 4350 Still Creek Dr */
        RouteWaypoint startPoint = new RouteWaypoint(new GeoCoordinate(start.getLatitude(), start.getLongitude()));
        /* END: Langley BC */
        RouteWaypoint destination = new RouteWaypoint(new GeoCoordinate(end.getLatitude(), end.getLongitude()));

        /* Add both waypoints to the route plan */
        routePlan.addWaypoint(startPoint);
        routePlan.addWaypoint(destination);

        /* Trigger the route calculation,results will be called back via the listener */
        coreRouter.calculateRoute(routePlan,
                routerListener);
    }

    static void calculateRoute(GeoCoordinate start,
                               GeoCoordinate end,
                               Router.Listener<List<RouteResult>, RoutingError> routerListener) {
        /* Initialize a CoreRouter */
        CoreRouter coreRouter = new CoreRouter();

        /* Initialize a RoutePlan */
        RoutePlan routePlan = new RoutePlan();

        /*
         * Initialize a RouteOption.HERE SDK allow users to define their own parameters for the
         * route calculation,including transport modes,route types and route restrictions etc.Please
         * refer to API doc for full list of APIs
         */
        RouteOptions routeOptions = new RouteOptions();
        /* Other transport modes are also available e.g Pedestrian */
        routeOptions.setTransportMode(RouteOptions.TransportMode.CAR);
        /* Disable highway in this route. */
        routeOptions.setHighwaysAllowed(false);
        /* Calculate the shortest route available. */
        routeOptions.setRouteType(RouteOptions.Type.SHORTEST);
        /* Calculate 1 route. */
        routeOptions.setRouteCount(1);
        /* Finally set the route option */
        routePlan.setRouteOptions(routeOptions);

        /* Define waypoints for the route */
        RouteWaypoint startPoint = new RouteWaypoint(new GeoCoordinate(start.getLatitude(), start.getLongitude()));
        RouteWaypoint destination = new RouteWaypoint(new GeoCoordinate(end.getLatitude(), end.getLongitude()));

        /* Add both waypoints to the route plan */
        routePlan.addWaypoint(startPoint);
        routePlan.addWaypoint(destination);

        /* Trigger the route calculation,results will be called back via the listener */
        coreRouter.calculateRoute(routePlan,
                routerListener);
    }

    static boolean isCloser(GeoCoordinate init, GeoCoordinate isCloserTo, GeoCoordinate otherCoordinate) {
        return isCloser(
                init.getLatitude(), init.getLongitude(),
                isCloserTo.getLatitude(), isCloserTo.getLongitude(),
                otherCoordinate.getLatitude(), otherCoordinate.getLongitude());
    }

    static boolean isCloser(FloatingViewService.Coordinate init, FloatingViewService.Coordinate isCloserTo, FloatingViewService.Coordinate otherCoordinate) {
        return isCloser(init.getX(), init.getY(), isCloserTo.getX(), isCloserTo.getY(), otherCoordinate.getX(), otherCoordinate.getY());
    }

    static boolean isCloser(double xInit, double yInit, double x1, double y1, double x2, double y2) {
        double distance1 = Math.sqrt(Math.pow(xInit - x1, 2) + Math.pow(yInit - y1, 2));
        double distance2 = Math.sqrt(Math.pow(xInit - x2, 2) + Math.pow(yInit - y2, 2));
        return distance1 < distance2;
    }

    static void logSpeedLimits(ElectronicHorizon electronicHorizon, PathTree pathTree) {

        MapAccessor mapAccessor = electronicHorizon.getMapAccessor();
        for (Link link : pathTree.getLinks()) {
            LinkInformation linkInformation;
            try {
                linkInformation = mapAccessor.getLinkInformation(link);
            } catch (DataNotReadyException dataNotReadyException) {
                return;
            }
            double speedLimitMetersPerSecond = linkInformation.getSpeedLimitMetersPerSecond();
            String speedLimitText = (int) Math.round(speedLimitMetersPerSecond * 3.6) + " Km";
        }
    }

}
