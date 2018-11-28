package edu.rutgers.brange.speedlimitwidget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    /**
     * code to post/handler request for permission
     */
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = -1010101;
    private static final boolean START_WITHOUT_VIEW = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        if (START_WITHOUT_VIEW) {
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

    /**
     * Set and initialize the view elements.
     */
    private void initializeView() {
        findViewById(R.id.notify_me).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(new Intent(MainActivity.this, FloatingViewService.class));
                finish();
            }
        });
    }

    private void startWithoutView(){
        startService(new Intent(MainActivity.this, FloatingViewService.class));
        finish();
    }
}
