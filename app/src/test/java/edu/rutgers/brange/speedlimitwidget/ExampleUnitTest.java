package edu.rutgers.brange.speedlimitwidget;

import android.widget.Toast;

import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.RouteManager;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.search.Address;
import com.here.android.mpa.search.ErrorCode;
import com.here.android.mpa.search.GeocodeRequest;
import com.here.android.mpa.search.Location;
import com.here.android.mpa.search.ResultListener;
import com.here.android.mpa.search.ReverseGeocodeRequest;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    private RouteManager.Listener routeManagerListener = new RouteManager.Listener() {
        public void onCalculateRouteFinished(RouteManager.Error errorCode,
                                             List<RouteResult> result) {

            if (errorCode == RouteManager.Error.NONE && result.get(0).getRoute() != null) {
                // create a map route object and place it on the map
                RouteResult routeResult = result.get(0);
                MapRoute mapRoute = new MapRoute(routeResult.getRoute());
                // Get the bounding box containing the route and zoom in (no animation)
                GeoBoundingBox gbb = result.get(0).getRoute().getBoundingBox();

            } else {

            }
        }

        public void onProgress(int percentage) {

        }
    };

    private static String readStream(InputStream is) {
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

    @Test
    public void http_isValid() {


        String urlString = "http://www.overpass-api.de/api/xapi?*[maxspeed=*][bbox=5.6283473,50.5348043,5.6285261,50.534884]";
        String response = "";
        try {
            URL url = new URL("http://www.android.com/");
            url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                response = readStream(in);
            } finally {
                urlConnection.disconnect();
            }
            assertNotEquals("", response);
        } catch (Exception e) {
            assertEquals(true, false);
        }
    }

    @Test
    public void routeTest() {
        // 2. Initialize RouteManager
        RouteManager routeManager = new RouteManager();

        // 3. Select routing options
        RoutePlan routePlan = new RoutePlan();

        RouteOptions routeOptions = new RouteOptions();
        routeOptions.setTransportMode(RouteOptions.TransportMode.CAR);
        routeOptions.setRouteType(RouteOptions.Type.FASTEST);
        routePlan.setRouteOptions(routeOptions);

        // 4. Select Waypoints for your routes
        // START: Nokia, Burnaby
        routePlan.addWaypoint(new GeoCoordinate(49.1966286, -123.0053635));

        // END: Airport, YVR
        routePlan.addWaypoint(new GeoCoordinate(49.1947289, -123.1762924));

        // 5. Retrieve Routing information via RouteManagerEventListener
        RouteManager.Error error = routeManager.calculateRoute(routePlan, routeManagerListener);
        if (error != RouteManager.Error.NONE) {
        }
    }
}