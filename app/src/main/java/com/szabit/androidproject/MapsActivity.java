package com.szabit.androidproject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AppComponentFactory;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Random;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GeoQueryEventListener, IOnLoadListener {

    private GoogleMap mMap;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location lastLocation;

    private FusedLocationProviderClient fusedLocationProviderClient;

    private GeoFire geoFire;
    private GeoQuery geoQuery;
    private DatabaseReference myLocationRef;
    private DatabaseReference myOffice;
    private DatabaseReference wifiRef;
    private Marker currentUser;

    private List<LatLng> officeArea;
    private IOnLoadListener listener;

//  Checks Variables
    private boolean locationCheck = false;
    private boolean wifiCheck = false;
    private boolean identityCheck = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

//        wifiManager = (WifiManager) getApplicationContext(Context.WIFI_SERVICE);

        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_WIFI_STATE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {

                        if (report.areAllPermissionsGranted()) {
//                            pushWifiInfo();
//                            getWifiInformation();
                            validateWifiCheck();
                            buildLocationRequest();
                            buildLocationCallback();
                            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MapsActivity.this);
                            initArea();
                            settingGeoFire();
                        }

                        if (report.isAnyPermissionPermanentlyDenied()) {

                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {

                    }
                }).onSameThread().check();
    }

//  Check I: GeoFencing

    private void buildLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(final LocationResult locationResult) {
                if (mMap != null) {
                    lastLocation = locationResult.getLastLocation();
                    addUserMarker();
                }
            }
        };
    }

    private void buildLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setSmallestDisplacement(10f);
    }

    private void settingGeoFire() {

        myLocationRef = FirebaseDatabase.getInstance().getReference("MyLocation");
        geoFire = new GeoFire(myLocationRef);
    }

    private void initArea() {
//      Pushing Office Area Co-ordinates to Firebase

//        officeArea = new ArrayList<>();
//        officeArea.add(new LatLng(37.422, -122.044));
//        officeArea.add(new LatLng(37.422, -122.144));
//        FirebaseDatabase.getInstance()
//                .getReference("OfficeArea")
//                .child("Offices")
//                .setValue(officeArea)
//                .addOnCompleteListener(new OnCompleteListener<Void>() {
//                    @Override
//                    public void onComplete(@NonNull Task<Void> task) {
//                        Toast.makeText(MapsActivity.this, "Updated", Toast.LENGTH_SHORT).show();
//                    }
//                }).addOnFailureListener(new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception e) {
//                Toast.makeText(MapsActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
//            }
//        });
        myOffice = FirebaseDatabase.getInstance()
                .getReference("OfficeArea")
                .child("Offices");

        listener = this;

        myOffice.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<MyLatLng> latLngList = new ArrayList<>();
                for (DataSnapshot locationSnapShot: dataSnapshot.getChildren()) {
                    MyLatLng latLng = locationSnapShot.getValue(MyLatLng.class);
                    latLngList.add(latLng);
                }
                listener.onLoadLocationSuccess(latLngList);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void addUserMarker() {
        geoFire.setLocation("You", new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()),
                new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                        if (currentUser != null) currentUser.remove();
                        currentUser = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()))
                            .title("You"));

                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUser.getPosition(), 12.0f));
                    }
                });
    }

    private void addCircleArea() {

        if (geoQuery != null) {
            geoQuery.removeGeoQueryEventListener(this);
            geoQuery.removeAllListeners();
        }
        for (LatLng latLng: officeArea) {
            mMap.addCircle(new CircleOptions().center(latLng)
                    .radius(500)
                    .strokeColor(Color.GREEN)
                    .fillColor(0x220000FF)
                    .strokeWidth(5.0f)
            );

            geoQuery = geoFire.queryAtLocation(new GeoLocation(latLng.latitude, latLng.longitude), 0.5f); //500m
            geoQuery.addGeoQueryEventListener(MapsActivity.this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        if(fusedLocationProviderClient != null) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        }

        addCircleArea();
    }

    @Override
    public void onLoadLocationSuccess(List<MyLatLng> latLngs) {

        officeArea = new ArrayList<>();
        for (MyLatLng myLatLng: latLngs) {
            LatLng convert = new LatLng(myLatLng.getLatitude(), myLatLng.getLongitude());
            officeArea.add(convert);
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        if (mMap != null) {
            mMap.clear();
            addUserMarker();
            addCircleArea();
        }

    }

    @Override
    public void onLoadLocationFailed(String message) {
        Toast.makeText(this, ""+message, Toast.LENGTH_SHORT).show();
    }

//  Check II: Wifi

    public Wifi getWifiInformation() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        Wifi wifi = new Wifi();
        wifi.setSSID(wifiInfo.getSSID());
        wifi.setBSSID(wifiInfo.getBSSID());
        return wifi;
    }

    public void pushWifiInfo() {
        FirebaseDatabase.getInstance()
                .getReference("WiFiInfo")
                .child("WiFi")
                .setValue(getWifiInformation())
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(MapsActivity.this, "Added", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MapsActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void validateWifiCheck() {
        FirebaseDatabase
                    .getInstance()
                    .getReference("WiFiInfo").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Wifi wifi = new Wifi();
                for (DataSnapshot wifiSnapShot: dataSnapshot.getChildren()) {
                    wifi = (wifiSnapShot.getValue(Wifi.class));
                }

//                Log.d("Checks", "Current WiFi: " + wifi.getSSID() + " " + wifi.getBSSID() +
//                        " Database: " + getWifiInformation().getSSID() + " " + getWifiInformation().getBSSID());

                if (wifi.getSSID() != null && wifi.getBSSID() != null) {
                    if (wifi.getBSSID().equals(getWifiInformation().getBSSID()) &&
                            wifi.getSSID().equals(getWifiInformation().getSSID())) {
                        wifiCheck = true;
                        sendNotification("Attendance", "Connected to office Wifi mark attendance now!");
                    }
                    else {
                        wifiCheck = false;
                        Log.d("Checks", "Connect to Office Wifi!");
                    }
                }
                else {
                    Log.d("Checks", "Empty");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

//  ***************************** //
    @Override
    protected void onStop() {
        super.onStop();
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onKeyEntered(String key, GeoLocation location) {
        locationCheck = true;
        validateWifiCheck();
        Log.d("Checks", ""+wifiCheck);
        if (locationCheck && wifiCheck) {
            sendNotification("Attendance", "You can mark your Attendance now!");
        }
        else if (!wifiCheck) {
            sendNotification("Attendance", "Connect to office Wifi to mark attendance!");
        }
    }

    @Override
    public void onKeyExited(String key) {
        locationCheck = false;
        sendNotification("Attendance", "You left office you have signed out");
    }

    @Override
    public void onKeyMoved(String key, GeoLocation location) {

    }

    @Override
    public void onGeoQueryReady() {

    }

    @Override
    public void onGeoQueryError(DatabaseError error) {

    }

    private void sendNotification(String title, String content) {
        String NOTIFICATION_CHANNEL_ID = "edmt_multiple_location";
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "My Notification",
                    NotificationManager.IMPORTANCE_DEFAULT);

//            Config
            notificationChannel.setDescription("Channel description");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[] {0,1000,500,1000});
            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        builder.setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(false)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));

        Notification notification = builder.build();
        notificationManager.notify(new Random().nextInt(), notification);
    }

    public void onAccountClick(View view) {

        Intent intent = new Intent(this, AccountActivity.class);
        startActivity(intent);

    }

    public void onCameraBtnClick(View view) {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }
}
