package edu.rutgers.brange.speedlimitwidget;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.here.android.mpa.common.GeoCoordinate;

import static android.provider.BaseColumns._ID;

public class MainActivity extends AppCompatActivity {
    /**
     * code to post/handler request for permission
     */
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2;

    static final String START_ACTIVITY_WITH_VIEW = "START_ACTIVITY_WITH_VIEW";
    private static final boolean START_WITHOUT_VIEW = true;

    public static SQLiteDatabase db;
    public static SpeedLimitWidgetDbHelper dbHelper;
    public static FavoritesCursorAdapter adapterFavorites;

    static ListView favorites;
    static long numSamples;
    static double averageDelta;

    static void initDbs(Context context) {
        dbHelper = new SpeedLimitWidgetDbHelper(context);
        db = dbHelper.getWritableDatabase();
        initSpeedAverage();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_DRAW_OVER_OTHER_APP_PERMISSION) {
            //Check if the permission is granted or not.
            if (!shouldCheckOverlayPermission()) {
                if (shouldCheckOtherPermissions()) {
                    requestOtherPermissions();
                } else {
                    startService();
                }
            } else { //Permission is not available
                Toast.makeText(this,
                        "Draw over other app permission not available. Closing the application",
                        Toast.LENGTH_SHORT).show();

                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS: {
                for (int index = 0; index < permissions.length; index++) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {

                        /**
                         * If the user turned down the permission request in the past and chose the
                         * Don't ask again option in the permission request system dialog.
                         */
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                                permissions[index])) {
                            Toast.makeText(this,
                                    "Required permission " + permissions[index] + " not granted. "
                                            + "Please go to settings and turn on for sample app",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    "Required permission " + permissions[index] + " not granted",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }

                /**
                 * All permission requests are being handled.Create map engine view.Please note
                 * the HERE SDK requires all permissions defined above to operate properly.
                 */
                startService();

                break;
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    static int initSpeedAverage() {
        String query = "SELECT * " +
                " FROM " + SpeedAverageContract.ContractEntry.TABLE_NAME;

        Cursor cursor = db.rawQuery(query, null);
        int count = cursor.getCount();
        if (count > 0) {
            cursor.moveToLast();
            averageDelta = cursor.getDouble(cursor.getColumnIndexOrThrow(SpeedAverageContract.ContractEntry.COLUMN_NAME_AVERAGE));
            numSamples = cursor.getInt(cursor.getColumnIndexOrThrow(SpeedAverageContract.ContractEntry.COLUMN_NAME_AVERAGE));
        } else {
            ContentValues cv = new ContentValues();
            cv.put(SpeedAverageContract.ContractEntry.COLUMN_NAME_AVERAGE, 0);
            cv.put(SpeedAverageContract.ContractEntry.COLUMN_NAME_SAMPLES, 0);
            db.insert(SpeedAverageContract.ContractEntry.TABLE_NAME, null, cv);
        }
        return count;
    }

    static void updateSpeedAverage() {
        ContentValues cv = new ContentValues();
        cv.put(SpeedAverageContract.ContractEntry.COLUMN_NAME_AVERAGE, averageDelta);
        cv.put(SpeedAverageContract.ContractEntry.COLUMN_NAME_SAMPLES, numSamples);

        String query = "SELECT * " +
                " FROM " + SpeedAverageContract.ContractEntry.TABLE_NAME;

        Cursor cursor = db.rawQuery(query, null);
        int count = cursor.getCount();
        if (count > 0) {
            db.update(SpeedAverageContract.ContractEntry.TABLE_NAME, cv, query, null);
        } else {
            db.insert(SpeedAverageContract.ContractEntry.TABLE_NAME, null, cv);
        }
    }

    public static boolean queryFavorite(GeoCoordinate location, int distanceInMeters) {
        String query = "SELECT * " +
                " FROM " + FavoritePlacesContract.ContractEntry.TABLE_NAME +
                " WHERE " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE + " - " + location.getLatitude() + ")" +
                " * " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE + " - " + location.getLatitude() + ")" +
                " + " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE + " - " + location.getLongitude() + ")" +
                " * " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE + " - " + location.getLongitude() + ")" +
                " < " + distanceInMeters * distanceInMeters;

        Cursor cursor = db.rawQuery(query, null);
        int count = cursor.getCount();
        return count > 0;
    }

    public static boolean queryFavorite(Location location, int distanceInMeters) {
        String query = "SELECT * " +
                " FROM " + FavoritePlacesContract.ContractEntry.TABLE_NAME +
                " WHERE " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE + " - " + location.getLatitude() + ")" +
                " * " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE + " - " + location.getLatitude() + ")" +
                " + " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE + " - " + location.getLongitude() + ")" +
                " * " +
                "(" + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE + " - " + location.getLongitude() + ")" +
                " < " + distanceInMeters * distanceInMeters;

        Cursor cursor = db.rawQuery(query, null);
        int count = cursor.getCount();
        return count > 0;
    }

    public static void upateFavorites(Context context, String customName, Location location, String address) {
        ContentValues cv;
        cv = new ContentValues();
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_NAME, customName);
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE, location.getLatitude()); //These Fields should be your String values of actual column names
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE, location.getLongitude()); //These Fields should be your String values of actual column names
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_ADDRESS, address);
        long id;
        String whereClause = FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE + "=? AND " + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE + "=?";

        if (!queryFavorite(location, 50)) {
            id = db.insert(FavoritePlacesContract.ContractEntry.TABLE_NAME, null, cv);
            initDbs(context);
        }
    }

    public static void upateFavorites(Context context, String customName, GeoCoordinate location, String address) {
        ContentValues cv;
        cv = new ContentValues();
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_NAME, customName);
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE, location.getLatitude()); //These Fields should be your String values of actual column names
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE, location.getLongitude()); //These Fields should be your String values of actual column names
        cv.put(FavoritePlacesContract.ContractEntry.COLUMN_NAME_ADDRESS, address);
        long id;
        String whereClause = FavoritePlacesContract.ContractEntry.COLUMN_NAME_LATITUDE + "=? AND " + FavoritePlacesContract.ContractEntry.COLUMN_NAME_LONGITUDE + "=?";

        if (!queryFavorite(location, 50)) {
            id = db.insert(FavoritePlacesContract.ContractEntry.TABLE_NAME, null, cv);
            initDbs(context);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDbs(this);

        // check Android version to request permissions
        if (shouldCheckOverlayPermission()) {
            requestOverlayPermissions();
        } else if (shouldCheckOtherPermissions()) {
            requestOtherPermissions();
        } else {
            startService();
        }
    }

    private void startService() {
        Intent intent = getIntent();
        boolean startActivityWithView = intent.getBooleanExtra(START_ACTIVITY_WITH_VIEW, false);
        if (START_WITHOUT_VIEW && !startActivityWithView) {
            startWithoutView();
        } else {
            initializeView();
        }
    }

    private boolean shouldCheckOverlayPermission() {
        boolean greaterThanM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        boolean canOverlay = Settings.canDrawOverlays(this);
        return greaterThanM && !canOverlay;
    }

    private boolean shouldCheckOtherPermissions() {
        boolean greaterThanM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        boolean permissionDenied = false;
        PackageManager pm = getPackageManager();
        String[] permissions = getRequiredPermissions(this);
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            int permissionStatus = pm.checkPermission(permission, getPackageName());
            if (permissionStatus == PackageManager.PERMISSION_DENIED &&
                    !permission.equals(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                // do something
                permissionDenied = true;
            } else {
                // do something
            }
        }
        return greaterThanM && permissionDenied;
    }

    private void requestOverlayPermissions() {
        //If the draw over permission is not available open the settings screen
        //to grant the permission.
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, CODE_DRAW_OVER_OTHER_APP_PERMISSION);
    }

    private void requestOtherPermissions() {
        String[] permissions = getRequiredPermissions(this);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
    }

    private String[] getRequiredPermissions(Context context) {

        try {
            return context
                    .getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
        } catch (PackageManager.NameNotFoundException e) {
            //handle or raise error
        }
        return new String[0];
    }

    /**
     * Set and initialize the view elements.
     */
    private void initializeView() {
        resetListView();

        findViewById(R.id.show_widget_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(new Intent(MainActivity.this, FloatingViewService.class));
                finish();
            }
        });

        findViewById(R.id.close_app_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FloatingViewService.stopManagersAndListeners();
                stopService(new Intent(MainActivity.this, FloatingViewService.class));
                finish();
            }
        });
    }

    private void startWithoutView(){
        startService(new Intent(MainActivity.this, FloatingViewService.class));
        finish();
    }

    void requeryFavorites() {
        String queryAll = "SELECT * FROM " + FavoritePlacesContract.ContractEntry.TABLE_NAME;
        Cursor cursor = db.rawQuery(queryAll, null);
        adapterFavorites = new FavoritesCursorAdapter(this, cursor);
        adapterFavorites.notifyDataSetChanged();
    }

    void resetListView() {
        favorites = findViewById(R.id.favorites);

        favorites.setLongClickable(true);
        favorites.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = ((CursorAdapter) favorites.getAdapter()).getCursor();
                cursor.moveToPosition(position);
                db.delete(FavoritePlacesContract.ContractEntry.TABLE_NAME, _ID + "=" + cursor.getString(cursor.getColumnIndexOrThrow(_ID)), null);
                resetListView();
                return true;
            }
        });

        requeryFavorites();
        favorites.setAdapter(adapterFavorites);
    }

}
