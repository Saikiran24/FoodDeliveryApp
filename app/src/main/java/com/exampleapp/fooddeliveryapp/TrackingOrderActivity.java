package com.exampleapp.fooddeliveryapp;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import com.exampleapp.fooddeliveryapp.Common.Common;
import com.exampleapp.fooddeliveryapp.Common.MyCustomMarkerAdapter;
import com.exampleapp.fooddeliveryapp.Model.ShippingOrderModel;
import com.exampleapp.fooddeliveryapp.Remote.IGoogleAPI;
import com.exampleapp.fooddeliveryapp.Remote.RetrofitGoogleAPIClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class TrackingOrderActivity extends FragmentActivity implements OnMapReadyCallback, ValueEventListener {

    private GoogleMap mMap;
    private Marker shipperMarker;

    private Polyline yellowPolyline,grayPolyline,blackPolyline;
    private PolylineOptions polylineOptions, blackPolylineOptions;
    private List<LatLng> polylineList;
    private IGoogleAPI iGoogleAPI;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private DatabaseReference shipperRef;

    //Move marker
    private Handler handler;
    private int index, next;
    private LatLng start, end;
    private float v;
    private double lat, lng;

    private boolean isInit = false;

    @OnClick(R.id.btn_call)
    void onCallClick() {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                //Request permission
                Dexter.withContext(this)
                        .withPermission(Manifest.permission.CALL_PHONE)
                        .withListener(new PermissionListener() {
                            @Override
                            public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {

                            }

                            @Override
                            public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                                Toast.makeText(TrackingOrderActivity.this, "You must enable this permission to CALL shipper!", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                            }
                        }).check();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse(new StringBuilder("tel:")
                    .append(Common.currentShippingOrder.getShipperPhone()).toString()));
            startActivity(intent);
    }

    @Override
    protected void onStop() {
        compositeDisposable.clear();
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_order);

        ButterKnife.bind(this); //Don't forget it to make OnClick Call work

        iGoogleAPI = RetrofitGoogleAPIClient.getInstance().create(IGoogleAPI.class);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        subscribeShipperMove();
    }

    private void subscribeShipperMove() {
        shipperRef = FirebaseDatabase.getInstance()
                .getReference(Common.SHIPPING_ORDER_REF)
                .child(Common.currentShippingOrder.getKey());

        shipperRef.addValueEventListener(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setInfoWindowAdapter(new MyCustomMarkerAdapter(getLayoutInflater()));

        mMap.getUiSettings().setZoomControlsEnabled(true);

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,R.raw.uber_light_with_label));

            if (!success)
                Log.e("FOODDELIVERYAPP","Style parsing failed");
        } catch (Resources.NotFoundException ex)
        {
            Log.e("FOODDELIVERYAPP","Resource not found");

        }

        drawRoutes();

      /*  // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));*/
    }

    private void drawRoutes() {
        LatLng locationOrder = new LatLng(Common.currentShippingOrder.getOrderModel().getLat(),
                Common.currentShippingOrder.getOrderModel().getLng());
        LatLng locationShipper = new LatLng(Common.currentShippingOrder.getCurrentLat(),
                Common.currentShippingOrder.getCurrentLng());

        //Add box
        mMap.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.box))
                .title(Common.currentShippingOrder.getOrderModel().getUserName())
                .snippet(Common.currentShippingOrder.getOrderModel().getShippingAddress())
                .position(locationOrder));


        //Add Shipper
        if (shipperMarker == null)
        {
            int height, width;
            height = width = 80;
            BitmapDrawable bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(TrackingOrderActivity.this, R.drawable.shippernew); //Change icon
            Bitmap resized = Bitmap.createScaledBitmap(bitmapDrawable.getBitmap(), width, height, false);

            shipperMarker = mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromBitmap(resized))
                    .title(new StringBuilder("Shipper: ").append(Common.currentShippingOrder.getShipperName()).toString())
                    .snippet(new StringBuilder("Phone: ").append(Common.currentShippingOrder.getShipperPhone())
                    .append("\n")
                    .append("Estimate Time Delivery: ")
                    .append(Common.currentShippingOrder.getEstimateTime()).toString())
                    .position(locationShipper));

            //Always show information
            shipperMarker.showInfoWindow();

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locationShipper, 18)); //Change to 18
        }
        else
        {
            shipperMarker.setPosition(locationShipper);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locationShipper, 18)); //Change to 18
        }

        //Draw routes
        String to = new StringBuilder()
                .append(Common.currentShippingOrder.getOrderModel().getLat())
                .append(",")
                .append(Common.currentShippingOrder.getOrderModel().getLng())
                .toString();
        String from = new StringBuilder()
                .append(Common.currentShippingOrder.getCurrentLat())
                .append(",")
                .append(Common.currentShippingOrder.getCurrentLng())
                .toString();


        compositeDisposable.add(iGoogleAPI.getDirections("driving",
                "less_driving",
                from, to,
                getString(R.string.google_maps_key))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> {

                    try {
                        JSONObject jsonObject = new JSONObject(s);
                        JSONArray jsonArray = jsonObject.getJSONArray("routes");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject route = jsonArray.getJSONObject(i);
                            JSONObject poly = route.getJSONObject("overview_polyline");
                            String polyline = poly.getString("points");
                            polylineList = Common.decodePoly(polyline);
                        }

                        polylineOptions = new PolylineOptions();
                        polylineOptions.color(Color.RED);
                        polylineOptions.width(12);
                        polylineOptions.startCap(new SquareCap());
                        polylineOptions.jointType(JointType.ROUND);
                        polylineOptions.addAll(polylineList);
                        yellowPolyline = mMap.addPolyline(polylineOptions);
                    } catch (Exception e) {
                        Toast.makeText(this, "" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                }, throwable -> Toast.makeText(TrackingOrderActivity.this, "" + throwable.getMessage(), Toast.LENGTH_SHORT).show()));

    }

    @Override
    protected void onDestroy() {
        shipperRef.removeEventListener(this);
        isInit = false;
        super.onDestroy();
    }

    @Override
    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
        //Save old position
        String from = new StringBuilder()
                .append(Common.currentShippingOrder.getCurrentLat())
                .append(",")
                .append(Common.currentShippingOrder.getCurrentLng())
                .toString();

        //Update position
        Common.currentShippingOrder = dataSnapshot.getValue(ShippingOrderModel.class);
        Common.currentShippingOrder.setKey(dataSnapshot.getKey());

        //Save new position
        String to = new StringBuilder()
                .append(Common.currentShippingOrder.getCurrentLat())
                .append(",")
                .append(Common.currentShippingOrder.getCurrentLng())
                .toString();


        if (dataSnapshot.exists())
        {
            if (isInit)
                moveMarkerAnimation(shipperMarker,from,to);
            else
                isInit = true;
        }

    }

    private void moveMarkerAnimation(Marker shipperMarker, String from, String to) {

        compositeDisposable.add(iGoogleAPI.getDirections("driving",
                "less_driving",
                from, to,
                getString(R.string.google_maps_key))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(returnResult -> {

                    try {
                        JSONObject jsonObject = new JSONObject(returnResult);
                        JSONArray jsonArray = jsonObject.getJSONArray("routes");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject route = jsonArray.getJSONObject(i);
                            JSONObject poly = route.getJSONObject("overview_polyline");
                            String polyline = poly.getString("points");
                            polylineList = Common.decodePoly(polyline);
                        }

                        polylineOptions = new PolylineOptions();
                        polylineOptions.color(Color.YELLOW);
                        polylineOptions.width(12);
                        polylineOptions.startCap(new SquareCap());
                        polylineOptions.jointType(JointType.ROUND);
                        polylineOptions.addAll(polylineList);
                        grayPolyline = mMap.addPolyline(polylineOptions);

                        blackPolylineOptions = new PolylineOptions();
                        blackPolylineOptions.color(Color.BLACK);
                        blackPolylineOptions.width(5);
                        blackPolylineOptions.startCap(new SquareCap());
                        blackPolylineOptions.jointType(JointType.ROUND);
                        blackPolylineOptions.addAll(polylineList);
                        blackPolyline = mMap.addPolyline(blackPolylineOptions);

                        //Animator
                        ValueAnimator polylineAnimator = ValueAnimator.ofInt(0,100);
                        polylineAnimator.setDuration(2000);
                        polylineAnimator.setInterpolator(new LinearInterpolator());
                        polylineAnimator.addUpdateListener(valueAnimator -> {
                            List<LatLng> points = grayPolyline.getPoints();
                            int percentValue = (int)valueAnimator.getAnimatedValue();
                            int size = points.size();
                            int newPoints = (int) (size*(percentValue/100.0f));
                            List<LatLng> p = points.subList(0,newPoints);
                            blackPolyline.setPoints(p);
                        });
                        polylineAnimator.start();

                        //Bike moving
                        handler = new Handler();
                        index = -1;
                        next = 1;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (index < polylineList.size()-1)
                                {
                                    index++;
                                    next = index+1;
                                    start = polylineList.get(index);
                                    end = polylineList.get(next);
                                }
                                ValueAnimator valueAnimator = ValueAnimator.ofInt(0,1);
                                valueAnimator.setDuration(1500);
                                valueAnimator.setInterpolator(new LinearInterpolator());
                                valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                        v = valueAnimator.getAnimatedFraction();
                                        lng = v*end.longitude+(1+v)*start.longitude;
                                        lat = v*end.latitude+(1+v)*start.latitude;
                                        LatLng newPos = new LatLng(lat,lng);
                                        shipperMarker.setPosition(newPos);
                                        shipperMarker.setAnchor(0.5f,0.5f);
                                        shipperMarker.setRotation(Common.getBearing(start,newPos));

                                        mMap.moveCamera(CameraUpdateFactory.newLatLng(newPos)); // Move camera
                                    }
                                });
                                valueAnimator.start();
                                if (index <polylineList.size() - 2) //Reach destination
                                    handler.postDelayed(this,1500);
                            }
                        },1500);
                    } catch (Exception e) {
                        Toast.makeText(this, "" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                }, throwable -> Toast.makeText(TrackingOrderActivity.this, "" + throwable.getMessage(), Toast.LENGTH_SHORT).show()));

    }

    @Override
    public void onCancelled(@NonNull DatabaseError databaseError) {
        Toast.makeText(this, databaseError.getMessage(), Toast.LENGTH_SHORT).show();
    }
}