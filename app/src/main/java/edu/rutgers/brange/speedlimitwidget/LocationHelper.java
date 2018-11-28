package edu.rutgers.brange.speedlimitwidget;

import android.location.Location;

public class LocationHelper {

    static double distance(Location x, Location y) {
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

}
