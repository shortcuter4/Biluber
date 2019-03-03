package com.tccss.biluber;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.Image;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private boolean driverFound = false;
    private boolean requestCheck = false;

    private String driverID;
    private String destination;

    private SupportMapFragment mapFragment;

    private Button mLogout, mRequest, mProfile;

    private double radius = 1.0;

    private LatLng markLocation, destinationLatLng;

    private Marker mDriverMark;
    private Marker pickUpMarker;

    private LinearLayout mDriverInfo;

    private ImageView mDriverProfileImage;

    private TextView mDriverName, mDriverPhoneNum, mDriverCarModel, mPlateNumber;

    private GeoQuery geoQuery;

    private DatabaseReference driverLocationReference;
    private ValueEventListener driverLocationReferenceListener;

    private DatabaseReference driverEndLocationRef;
    private ValueEventListener driverEndLocationRefListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        } else {
            mapFragment.getMapAsync(this);
        }

        // Creates the layout for the CustomerMapActivity class

        mDriverInfo = (LinearLayout) findViewById(R.id.driverInfo);

        mDriverProfileImage = (ImageView) findViewById(R.id.driverProfileImage);

        destinationLatLng = new LatLng(0,0);

        mDriverName = (TextView) findViewById(R.id.driverName);
        mDriverPhoneNum = (TextView) findViewById(R.id.driverPhone);
        mDriverCarModel = (TextView) findViewById(R.id.driverCarModel);
        mPlateNumber = (TextView) findViewById(R.id.driverPlateNumber);

        mLogout = (Button) findViewById(R.id.signOut);
        mRequest = (Button) findViewById(R.id.request);
        mProfile = (Button) findViewById(R.id.profile);

        //Button listener for Sign Out Button on the Customer's Map
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent (CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        //Button listener for Make a Request on the Customer's Map
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(requestCheck) { // If there is an active request, when it is clicked, it erases current driver
                    endRide();
                }
                else { // Assigns a driver to the current customer
                    requestCheck = true;
                    String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference reference = FirebaseDatabase.getInstance().getReference("customerRequests");
                    GeoFire geoFire = new GeoFire(reference);
                    geoFire.setLocation(userID, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    markLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    pickUpMarker = mMap.addMarker(new MarkerOptions().position(markLocation).title("Pickup Point Here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                    mRequest.setText("Please Wait for Your Ride...");

                    getNearestDriver();
                }
            }
        });

        // Button Listener for the Profile Button
        mProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerProfileActivity.class);
                startActivity(intent);
                return;
            }
        });


        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        // Google Places API method, takes the information about the destination which is given by the customer
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
                destinationLatLng = place.getLatLng();
            }
            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
            }
        });
    }

    // Calls the endRide() method
    private void getRideEnd() {
        driverEndLocationRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverID).child("customerRequests").child("customerRideID");
        driverEndLocationRefListener = driverEndLocationRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){

                }else{
                    endRide();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    // To finish the current ride, erases the information about current driver of the customer from Firebase Database and the layout
    private void endRide() {
        requestCheck = false;
        geoQuery.removeAllListeners();
        driverLocationReference.removeEventListener(driverLocationReferenceListener);
        driverEndLocationRef.removeEventListener(driverEndLocationRefListener);

        if(driverID != null) {
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverID).child("customerRequests");
            driverRef.removeValue();
            driverID = null;
        }
        driverFound = false;
        radius = 1;
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("customerRequests");
        GeoFire geoFire = new GeoFire(reference);
        geoFire.removeLocation(userID);

        if(pickUpMarker != null) {
            pickUpMarker.remove();
        }
        if(mDriverMark != null) {
            mDriverMark.remove();
        }

        mRequest.setText("Make a Request");
        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverPhoneNum.setText("");
        mDriverProfileImage.setImageResource(R.mipmap.ic_profileimage);
        mDriverCarModel.setText("");
        mPlateNumber.setText("");
    }

    // Takes the driver's information from Firebase Database and shows them in the CustomerMapAcitivity Layout
    private void getDriverInfo() {
        mDriverInfo.setVisibility(View.VISIBLE);
        final DatabaseReference mDriverDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverID);
        mDriverDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name") != null) {
                        mDriverName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null) {
                        mDriverPhoneNum.setText(map.get("phone").toString());
                    }
                    if(map.get("carModel") != null) {
                        mDriverCarModel.setText(map.get("carModel").toString());
                    }
                    if(map.get("plateNumber") != null) {
                        mPlateNumber.setText(map.get("plateNumber").toString());
                    }
                    if(map.get("profileImageUrl") != null) {
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mDriverProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }


    /*
    Creates a GeoFire location for an example driver and search the nearest driver for the customer
    If the driver is not found, the method calls itself again with a wider radius for the search
    At the end, if the driver is found, method informs the customer through the map by adding a marker on it
    */
    private void getNearestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);

        geoQuery = geoFire.queryAtLocation(new GeoLocation(markLocation.latitude, markLocation.longitude), radius);

        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) { // when a driver enters in the radius
                if(!driverFound && requestCheck) {

                    driverFound = true;
                    driverID = key;

                    DatabaseReference driverReference = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverID).child("customerRequests");
                    String customerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap hashMap = new HashMap();
                    hashMap.put("customerRideID", customerID);
                    hashMap.put("destination", destination);
                    hashMap.put("destinationLat", destinationLatLng.latitude);
                    hashMap.put("destinationLng", destinationLatLng.longitude);
                    driverReference.updateChildren(hashMap);

                    getDriverLocation();
                    getDriverInfo();
                    getRideEnd();
                    mRequest.setText("Getting the location of driver...");

                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if(!driverFound) {
                    radius++;
                    getNearestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    /*
    Tells the customer where the driver is
    and takes continuously driver's location from FireBase
    */
    private void getDriverLocation(){
        driverLocationReference = FirebaseDatabase.getInstance().getReference().child("driversBusy").child(driverID).child("l");
        driverLocationReferenceListener = driverLocationReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && requestCheck) {
                    List <Object> map = (List<Object>) dataSnapshot.getValue();
                    double latitude = 0;
                    double longitude = 0;
                    if(map.get(0) != null) {
                        latitude = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null) {
                        longitude = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLocation = new LatLng(latitude, longitude);
                    if(mDriverMark != null) {
                        mDriverMark.remove();
                    }
                    Location customerLoc = new Location("");
                    customerLoc.setLatitude(markLocation.latitude);
                    customerLoc.setLongitude(markLocation.longitude);

                    Location driverLoc = new Location("");
                    driverLoc.setLatitude(driverLocation.latitude);
                    driverLoc.setLongitude(driverLocation.longitude);

                    float distance = customerLoc.distanceTo(driverLoc);

                    if(distance < 100) {
                        mRequest.setText("Your Driver is here");
                    }
                    else {
                        if(distance > 1000)
                            mRequest.setText("Your Driver is " + String.valueOf(distance/1000).substring(0,3) + " Kilometer Away");
                        else
                            mRequest.setText("Your Driver is " + String.format("%.0f",distance) + " Meter Away");
                    }
                    mDriverMark = mMap.addMarker(new MarkerOptions().position(driverLocation).title("Your Driver").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)));

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    @Override
    // Creates a map
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    // Google Maps API method, connects to location services
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    // Checks a customer's location and zooms the map for the customer about the driver's current location
    public void onLocationChanged(Location location) {
        if(getApplicationContext()!=null) {
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(14));
        }
    }

    @Override
    // If the location of customer changes, this method takes the new location with a 1 second delay
    public void onConnected(@Nullable Bundle bundle) {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    final int LOCATION_REQUEST_CODE = 1;
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    mapFragment.getMapAsync(this);


                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
